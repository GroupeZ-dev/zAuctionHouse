package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionClaimService;
import fr.maxlego08.zauctionhouse.api.services.result.ClaimResult;
import fr.maxlego08.zauctionhouse.api.storage.dto.TransactionDTO;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.TransactionRepository;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ClaimService extends AuctionService implements AuctionClaimService {

    /**
     * Propriete systeme (ou variable d'environnement {@code ZAUCTIONHOUSE_CLAIM_SERVER_ID})
     * permettant de FORCER l'identite du serveur qui signe les reservations de claim.
     * <p>
     * Par defaut l'identite est deduite de l'environnement (nom de serveur de config.yml, nom
     * d'hote, chemin du dossier du plugin). Ce n'est a renseigner que si plusieurs backends d'un
     * meme cluster partagent ces trois valeurs (images de conteneur strictement identiques) : ils
     * partageraient sinon la meme empreinte et pourraient recuperer leurs reservations
     * mutuellement. Aucune cle YAML n'est ajoutee, la valeur se pose au demarrage de la JVM
     * ({@code -Dzauctionhouse.claim.server-id=survie-1}).
     */
    private static final String SERVER_ID_PROPERTY = "zauctionhouse.claim.server-id";

    /**
     * Variante variable d'environnement de {@link #SERVER_ID_PROPERTY}.
     */
    private static final String SERVER_ID_ENVIRONMENT = "ZAUCTIONHOUSE_CLAIM_SERVER_ID";

    /**
     * Nombre de tentatives des ecritures de cloture. Une cloture perdue APRES un depot laisse
     * l'argent verse et les lignes reservees : quelques reprises espacees absorbent les echecs
     * transitoires (pool JDBC sature, coupure reseau breve) qui produiraient sinon ce cas.
     */
    private static final int CLAIM_WRITE_ATTEMPTS = 3;

    /**
     * Attente de base entre deux tentatives de cloture, multipliee par le numero de tentative.
     */
    private static final long CLAIM_WRITE_RETRY_MS = 200L;

    private final AuctionPlugin plugin;

    /**
     * Garde de confort par JVM : empeche deux claims simultanes pour le meme joueur sur CE
     * serveur. Sans aucun effet en cluster : c'est la reservation en base qui arbitre.
     */
    private final Set<UUID> claimingPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Jetons de claim actuellement poses en base PAR CE PROCESSUS. Un jeton present ici ne doit
     * JAMAIS etre libere par la recuperation : soit le claim est en cours, soit il a paye sans
     * reussir a cloturer, et dans les deux cas liberer revient a payer deux fois.
     */
    private final Set<String> liveClaimTokens = ConcurrentHashMap.newKeySet();

    /**
     * Prefixe identifiant CE SERVEUR ({@code "xxxxxxxx-"}), stable d'un demarrage a l'autre.
     */
    private volatile String serverTokenPrefix;

    /**
     * Prefixe identifiant CETTE EXECUTION ({@code "xxxxxxxx-yyyyyy-"}), regenere a chaque
     * demarrage : il distingue les reservations laissees par un run precedent (recuperables) de
     * celles du run courant (jamais recuperables).
     */
    private volatile String runTokenPrefix;

    public ClaimService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ClaimResult> claimMoney(Player player) {

        // C-022 : la garde vient AVANT l'ajout dans claimingPlayers, sinon un refus laisserait
        // l'UUID dans le jeu et bloquerait tout claim ulterieur. Un claim engage pendant l'arret
        // reserve les transactions PENDING puis voit sa cloture rejetee par un pool draine :
        // l'argent est verse et les lignes restent reservees.
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(ClaimResult.failure("Server is shutting down"));
        }

        var playerUniqueId = player.getUniqueId();
        if (!this.claimingPlayers.add(playerUniqueId)) {
            return CompletableFuture.completedFuture(ClaimResult.failure("A claim is already in progress"));
        }

        return CompletableFuture.supplyAsync(() -> doClaim(player, playerUniqueId), this.plugin.getExecutorService())
                .whenComplete((result, throwable) -> this.claimingPlayers.remove(playerUniqueId));
    }

    /**
     * Sequence : RESERVER, relire ce qu'on a remporte, payer, puis cloturer uniquement ce qui a
     * ete paye et liberer le reste. La recuperation des reservations abandonnees n'intervient que
     * si la reservation n'a rien remporte, et uniquement sur les reservations de CE serveur.
     *
     * @param player         le joueur qui recupere son argent
     * @param playerUniqueId l'identifiant du joueur, capture avant tout saut de thread
     * @return le resultat du claim
     */
    private ClaimResult doClaim(Player player, UUID playerUniqueId) {

        var repository = this.plugin.getStorageManager().with(TransactionRepository.class);

        String claimToken = newClaimToken();
        // Le jeton est publie AVANT la moindre ecriture : un autre claim de CE processus ne doit
        // jamais pouvoir le prendre pour une reservation abandonnee.
        this.liveClaimTokens.add(claimToken);

        final List<Integer> paidIds = new ArrayList<>();
        final List<Integer> unpaidIds = new ArrayList<>();
        boolean tokenCleared = false;
        boolean anyDeposit = false;

        try {

            // RESERVER AVANT DE PAYER : le rowcount est le seul arbitre.
            if (reserveWithRecovery(repository, playerUniqueId, claimToken) == 0) {
                tokenCleared = true;
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            }

            var transactions = repository.selectByClaimToken(claimToken);
            if (transactions.isEmpty()) {
                // Reservation gagnee mais relecture vide (echec de lecture avale par Sarah) : il
                // FAUT rendre les lignes, sinon elles resteraient bloquees jusqu'au redemarrage.
                repository.releaseReservation(claimToken);
                tokenCleared = true;
                message(this.plugin, player, Message.CLAIM_NO_PENDING);
                return ClaimResult.nothingToClaim("No pending transactions");
            }

            var economyManager = this.plugin.getEconomyManager();
            var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();
            Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));

            // On ne cloture QUE les lignes effectivement creditees. Tout ce qui n'a pas ete verse
            // repart en PENDING et sera represente au prochain claim, au lieu d'etre detruit.
            BigDecimal totalClaimed = BigDecimal.ZERO;
            AuctionEconomy lastEconomy = null;

            for (var entry : byEconomy.entrySet()) {
                String economyName = entry.getKey();
                List<TransactionDTO> economyTransactions = entry.getValue();

                var optionalEconomy = economyManager.getEconomy(economyName);
                if (optionalEconomy.isEmpty()) {
                    // Economie disparue d'economies.yml : surtout ne rien cloturer, l'argent reste du.
                    this.plugin.getLogger().warning("Economy not found: " + economyName + ", " + economyTransactions.size() + " transaction(s) released back to PENDING");
                    economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                    continue;
                }
                var economy = optionalEconomy.get();

                BigDecimal economyTotal = economyTransactions.stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

                if (economyTotal.compareTo(BigDecimal.ZERO) <= 0) {
                    // Aucune valeur positive a verser : ce sont des lignes d'historique, on cloture.
                    economyTransactions.forEach(transaction -> paidIds.add(transaction.id()));
                    continue;
                }

                if (!player.isOnline()) {
                    this.plugin.getLogger().warning("Player " + playerUniqueId + " went offline during claim, " + economyTransactions.size() + " transaction(s) released back to PENDING");
                    economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                    continue;
                }

                // depositChecked, JAMAIS deposit : le chemin void est volontairement silencieux et
                // rendrait RETRIEVED une ligne dont l'argent n'a jamais ete credite (economie
                // incapable de crediter hors ligne, provider en echec). Sur false comme sur
                // exception, la transaction repart en PENDING : le joueur reste creancier.
                boolean deposited;
                try {
                    deposited = economy.depositChecked(playerUniqueId, economyTotal, depositReason);
                } catch (Exception exception) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to deposit " + economyTotal + " to " + player.getName() + " for economy " + economyName + ", transaction(s) released back to PENDING", exception);
                    economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                    continue;
                }

                if (!deposited) {
                    this.plugin.getLogger().severe("Deposit of " + economyTotal + " to " + player.getName() + " was REFUSED by economy " + economyName + ", " + economyTransactions.size() + " transaction(s) released back to PENDING");
                    economyTransactions.forEach(transaction -> unpaidIds.add(transaction.id()));
                    continue;
                }

                anyDeposit = true;
                economyTransactions.forEach(transaction -> paidIds.add(transaction.id()));
                totalClaimed = totalClaimed.add(economyTotal);
                lastEconomy = economy;
                message(this.plugin, player, Message.CLAIM_ECONOMY_SUCCESS, "%amount%", economyManager.format(economy, economyTotal), "%economy%", economy.getDisplayName());
            }

            closeClaim(repository, claimToken, paidIds, unpaidIds);
            tokenCleared = true;

            if (totalClaimed.compareTo(BigDecimal.ZERO) > 0) {
                message(this.plugin, player, Message.CLAIM_SUCCESS, "%amount%", totalClaimed.toString());
                return ClaimResult.success("Money claimed successfully", totalClaimed.doubleValue(), lastEconomy);
            }

            return ClaimResult.nothingToClaim("No positive amount to claim");

        } catch (RuntimeException exception) {
            if (anyDeposit) {
                // Trou residuel assume : le depot a eu lieu mais la cloture a echoue. Les lignes
                // restent reservees et AUCUNE recuperation automatique ne les rendra (leur jeton
                // porte le prefixe du run courant), pour ne pas payer une seconde fois. On trace
                // tout ce qu'il faut pour l'auditer et corriger a la main.
                this.plugin.getLogger().log(Level.SEVERE, "CLAIM NOT CLOSED - player " + playerUniqueId + " token " + claimToken + " paid ids " + paidIds + " : money was deposited but the rows could not be closed, manual check required", exception);
            } else {
                // Aucun depot n'a eu lieu : rendre les lignes est sans risque et evite de les
                // immobiliser jusqu'au prochain demarrage.
                try {
                    repository.releaseReservation(claimToken);
                    tokenCleared = true;
                } catch (RuntimeException releaseFailure) {
                    exception.addSuppressed(releaseFailure);
                }
                this.plugin.getLogger().log(Level.SEVERE, "Claim failed for player " + playerUniqueId + " token " + claimToken + " before any deposit", exception);
            }
            throw exception;
        } finally {
            if (tokenCleared) {
                this.liveClaimTokens.remove(claimToken);
            } else {
                this.plugin.getLogger().severe("Claim token " + claimToken + " kept in memory for player " + playerUniqueId + " : the reserved rows will NOT be released automatically by this server run");
            }
        }
    }

    /**
     * Reserve les lignes PENDING du joueur et, seulement si rien n'etait libre, recupere les
     * reservations abandonnees par une execution PRECEDENTE DE CE SERVEUR avant de retenter une
     * unique fois.
     * <p>
     * L'ordre compte : la recuperation ne s'execute plus a chaque claim (elle ne s'executait
     * qu'avant, et pouvait donc rendre libre une reservation vivante), mais uniquement quand le
     * joueur n'a effectivement rien de reclamable.
     *
     * @param repository     le depot des transactions
     * @param playerUniqueId le joueur concerne
     * @param claimToken     le jeton de ce claim
     * @return le nombre de lignes remportees
     */
    private int reserveWithRecovery(TransactionRepository repository, UUID playerUniqueId, String claimToken) {
        int reserved = repository.reservePending(playerUniqueId, claimToken);
        if (reserved > 0) return reserved;

        if (recoverAbandonedReservations(repository, playerUniqueId) == 0) return 0;

        return repository.reservePending(playerUniqueId, claimToken);
    }

    /**
     * Libere les reservations qui appartiennent a CE SERVEUR mais a une execution precedente
     * (crash entre la reservation et le paiement).
     * <p>
     * Aucune horloge n'intervient : c'est le prefixe du jeton qui decide. Un serveur ne peut donc
     * jamais liberer la reservation vivante d'un autre serveur, meme avec une derive NTP de
     * plusieurs heures, et il ne peut pas non plus liberer une reservation de sa propre execution
     * en cours, meme si celle-ci dure tres longtemps.
     *
     * @param repository     le depot des transactions
     * @param playerUniqueId le joueur concerne
     * @return le nombre de lignes rendues reclamables
     */
    private int recoverAbandonedReservations(TransactionRepository repository, UUID playerUniqueId) {

        var tokens = repository.selectReservedTokens(playerUniqueId, this.serverTokenPrefix);
        if (tokens.isEmpty()) return 0;

        int released = 0;
        for (String token : tokens) {

            if (token.startsWith(this.runTokenPrefix) || this.liveClaimTokens.contains(token)) {
                // Jeton de l'execution courante : claim en cours, ou claim paye dont la cloture a
                // echoue. Dans les deux cas, liberer reviendrait a payer deux fois.
                this.plugin.getLogger().warning("Claim reservation " + token + " of player " + playerUniqueId + " belongs to the current server run, left untouched");
                continue;
            }

            int count = repository.releaseReservation(token);
            if (count > 0) {
                released += count;
                this.plugin.getLogger().warning("Released " + count + " abandoned claim reservation(s) of this server for " + playerUniqueId + " (token " + token + ", server crashed between reservation and payment?)");
            }
        }
        return released;
    }

    /**
     * Cloture le claim : les lignes payees passent RETRIEVED, les autres redeviennent libres.
     *
     * @param repository les transactions
     * @param claimToken le jeton du claim
     * @param paidIds    les lignes reellement creditees
     * @param unpaidIds  les lignes reservees mais non creditees
     */
    private void closeClaim(TransactionRepository repository, String claimToken, List<Integer> paidIds, List<Integer> unpaidIds) {
        if (!paidIds.isEmpty()) retryWrite("closing paid claim rows", claimToken, () -> repository.finishClaim(paidIds, claimToken));
        if (!unpaidIds.isEmpty()) retryWrite("releasing unpaid claim rows", claimToken, () -> repository.releaseClaim(unpaidIds, claimToken));
    }

    /**
     * Rejoue une ecriture de cloture quelques fois avant d'abandonner.
     *
     * @param description ce que l'ecriture tente de faire, pour les journaux
     * @param claimToken  le jeton concerne, pour les journaux
     * @param write       l'ecriture a effectuer
     */
    private void retryWrite(String description, String claimToken, Runnable write) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= CLAIM_WRITE_ATTEMPTS; attempt++) {
            try {
                write.run();
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                this.plugin.getLogger().log(Level.WARNING, "Attempt " + attempt + "/" + CLAIM_WRITE_ATTEMPTS + " of " + description + " failed for claim token " + claimToken, exception);
                if (attempt == CLAIM_WRITE_ATTEMPTS) break;
                try {
                    Thread.sleep(CLAIM_WRITE_RETRY_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw lastFailure == null ? new IllegalStateException(description + " was never attempted") : lastFailure;
    }

    /**
     * Fabrique un jeton de claim de 36 caracteres EXACTEMENT (taille de la colonne
     * {@code claim_token}) : {@code <8 empreinte serveur>-<6 execution>-<20 aleatoires>}.
     *
     * @return un nouveau jeton, attribuable a ce serveur et a cette execution
     */
    private String newClaimToken() {
        ensureTokenPrefixes();
        return this.runTokenPrefix + randomHexadecimal(20);
    }

    /**
     * Calcule, une seule fois par execution, les deux prefixes de jeton.
     * <p>
     * Fait paresseusement et non dans le constructeur : la configuration n'est pas encore chargee
     * quand les services sont instancies.
     */
    private void ensureTokenPrefixes() {
        if (this.runTokenPrefix != null) return;
        synchronized (this) {
            if (this.runTokenPrefix != null) return;
            String serverPrefix = computeServerFingerprint() + "-";
            this.serverTokenPrefix = serverPrefix;
            this.runTokenPrefix = serverPrefix + randomHexadecimal(6) + "-";
            this.plugin.getLogger().info("Claim reservations are signed with " + this.runTokenPrefix + " (server identity + run identity)");
        }
    }

    /**
     * Empreinte STABLE d'un demarrage a l'autre et DISTINCTE d'un serveur a l'autre.
     * <p>
     * Stable, sinon les reservations laissees par un crash ne seraient plus jamais reconnues comme
     * siennes (argent immobilise). Distincte, sinon deux serveurs pourraient se recuperer
     * mutuellement leurs reservations (argent verse deux fois) : c'est le seul point de confiance
     * de ce mecanisme, et {@link #SERVER_ID_PROPERTY} permet de le forcer si l'environnement ne
     * fournit rien de discriminant.
     *
     * @return huit caracteres hexadecimaux identifiant ce serveur
     */
    private String computeServerFingerprint() {

        String override = System.getProperty(SERVER_ID_PROPERTY);
        if (override == null || override.isBlank()) override = System.getenv(SERVER_ID_ENVIRONMENT);

        String identity;
        if (override != null && !override.isBlank()) {
            identity = override.trim();
        } else {
            identity = this.plugin.getConfiguration().getServerName() + "|" + hostName() + "|" + dataFolderPath();
        }

        // UUID de type 3 : hachage stable et bien reparti, sans exception verifiee a traiter.
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString().substring(0, 8);
    }

    /**
     * @return le nom d'hote de la machine, ou une chaine vide si l'environnement n'en fournit pas
     */
    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            // Repli : "pid@hote" du RuntimeMXBean, dont on ne garde que l'hote (le pid change a
            // chaque demarrage et rendrait l'empreinte instable).
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int separator = runtimeName.indexOf('@');
            return separator >= 0 ? runtimeName.substring(separator + 1) : "";
        }
    }

    /**
     * @return le chemin canonique du dossier du plugin, discriminant deux instances d'une meme machine
     */
    private String dataFolderPath() {
        File dataFolder = this.plugin.getDataFolder();
        try {
            return dataFolder.getCanonicalPath();
        } catch (IOException exception) {
            return dataFolder.getAbsolutePath();
        }
    }

    /**
     * @param length le nombre de caracteres voulus, au plus 32
     * @return une suite hexadecimale aleatoire
     */
    private static String randomHexadecimal(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

    @Override
    public CompletableFuture<Map<String, BigDecimal>> getPendingMoneyByEconomy(UUID playerUniqueId) {
        return getPendingTransactions(playerUniqueId).thenApply(transactions -> {
            Map<String, BigDecimal> result = new HashMap<>();

            for (TransactionDTO transaction : transactions) {
                // Only count positive values (money to receive)
                if (transaction.value().compareTo(BigDecimal.ZERO) > 0) {
                    result.merge(transaction.economy_name(), transaction.value(), BigDecimal::add);
                }
            }

            return result;
        });
    }

    @Override
    public CompletableFuture<BigDecimal> getTotalPendingMoney(UUID playerUniqueId) {
        return getPendingMoneyByEconomy(playerUniqueId).thenApply(byEconomy -> byEconomy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    public CompletableFuture<List<TransactionDTO>> getPendingTransactions(UUID playerUniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);
            return repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING);
        }, this.plugin.getExecutorService());
    }

    @Override
    public void handlePlayerJoin(Player player) {
        var config = this.plugin.getConfiguration().getAutoClaimConfiguration();

        getPendingMoneyByEconomy(player.getUniqueId()).thenAccept(pendingByEconomy -> {
            // Check if player is still online at the start of async callback
            if (!player.isOnline()) {
                return;
            }

            if (pendingByEconomy.isEmpty()) {
                return;
            }

            BigDecimal total = pendingByEconomy.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            if (config.enabled()) {
                // Auto-claim is enabled
                long delay = config.delayTicks();
                if (delay <= 0) {
                    claimMoney(player);
                } else {
                    this.plugin.getScheduler().runLater(task -> {
                        if (player.isOnline()) {
                            claimMoney(player);
                        }
                    }, delay);
                }
            } else if (config.notifyPending()) {
                // Just notify the player about pending money
                long delay = config.notifyDelayTicks();
                Runnable notifyTask = () -> {
                    if (player.isOnline()) {
                        String formattedAmount = formatPendingMoney(pendingByEconomy);
                        message(this.plugin, player, Message.CLAIM_PENDING_NOTIFY, "%amount%", formattedAmount, "%count%", String.valueOf(pendingByEconomy.size()));
                    }
                };

                if (delay <= 0) {
                    notifyTask.run();
                } else {
                    this.plugin.getScheduler().runLater(task -> notifyTask.run(), delay);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> clearPendingTransactions(UUID playerUniqueId, boolean giveMoney) {
        return CompletableFuture.runAsync(() -> {

            var repository = this.plugin.getStorageManager().with(TransactionRepository.class);

            if (!giveMoney) {
                // Le javadoc de l'API l'indique : "the transactions are simply discarded without
                // any payment". Aucun paiement, donc aucune reservation necessaire, mais le
                // compare-and-set sur PENDING reste indispensable pour ne pas ecraser une
                // cloture concurrente.
                var pendingIds = repository.selectByPlayerAndStatus(playerUniqueId, TransactionStatus.PENDING).stream().map(TransactionDTO::id).toList();
                if (pendingIds.isEmpty()) return;
                int closed = repository.updateStatus(pendingIds, TransactionStatus.RETRIEVED);
                this.plugin.getLogger().warning("Discarded " + closed + "/" + pendingIds.size() + " pending transaction(s) for " + playerUniqueId + " without payment (clearPendingTransactions)");
                return;
            }

            String claimToken = newClaimToken();
            this.liveClaimTokens.add(claimToken);

            final List<Integer> paidIds = new ArrayList<>();
            final List<Integer> unpaidIds = new ArrayList<>();
            boolean tokenCleared = false;
            boolean anyDeposit = false;

            try {

                if (reserveWithRecovery(repository, playerUniqueId, claimToken) == 0) {
                    tokenCleared = true;
                    return;
                }

                var transactions = repository.selectByClaimToken(claimToken);
                if (transactions.isEmpty()) {
                    repository.releaseReservation(claimToken);
                    tokenCleared = true;
                    return;
                }

                var economyManager = this.plugin.getEconomyManager();
                var depositReason = this.plugin.getConfiguration().getAutoClaimConfiguration().depositReason();
                Map<String, List<TransactionDTO>> byEconomy = transactions.stream().collect(Collectors.groupingBy(TransactionDTO::economy_name));

                for (var entry : byEconomy.entrySet()) {
                    var optionalEconomy = economyManager.getEconomy(entry.getKey());
                    if (optionalEconomy.isEmpty()) {
                        this.plugin.getLogger().warning("Economy not found: " + entry.getKey() + ", transaction(s) released back to PENDING");
                        entry.getValue().forEach(transaction -> unpaidIds.add(transaction.id()));
                        continue;
                    }

                    BigDecimal economyTotal = entry.getValue().stream().map(TransactionDTO::value).filter(v -> v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (economyTotal.compareTo(BigDecimal.ZERO) > 0) {
                        boolean deposited;
                        try {
                            deposited = optionalEconomy.get().depositChecked(playerUniqueId, economyTotal, depositReason);
                        } catch (Exception exception) {
                            this.plugin.getLogger().log(Level.SEVERE, "Failed to deposit " + economyTotal + " to " + playerUniqueId + " for economy " + entry.getKey() + ", transaction(s) released back to PENDING", exception);
                            entry.getValue().forEach(transaction -> unpaidIds.add(transaction.id()));
                            continue;
                        }
                        if (!deposited) {
                            // Refus explicite du provider : ne surtout pas cloturer, l'argent est du.
                            this.plugin.getLogger().severe("Deposit of " + economyTotal + " to " + playerUniqueId + " was REFUSED by economy " + entry.getKey() + ", transaction(s) released back to PENDING");
                            entry.getValue().forEach(transaction -> unpaidIds.add(transaction.id()));
                            continue;
                        }
                        anyDeposit = true;
                    }
                    entry.getValue().forEach(transaction -> paidIds.add(transaction.id()));
                }

                closeClaim(repository, claimToken, paidIds, unpaidIds);
                tokenCleared = true;

            } catch (RuntimeException exception) {
                if (anyDeposit) {
                    this.plugin.getLogger().log(Level.SEVERE, "CLAIM NOT CLOSED - player " + playerUniqueId + " token " + claimToken + " paid ids " + paidIds + " : money was deposited but the rows could not be closed, manual check required", exception);
                } else {
                    try {
                        repository.releaseReservation(claimToken);
                        tokenCleared = true;
                    } catch (RuntimeException releaseFailure) {
                        exception.addSuppressed(releaseFailure);
                    }
                    this.plugin.getLogger().log(Level.SEVERE, "clearPendingTransactions failed for player " + playerUniqueId + " token " + claimToken + " before any deposit", exception);
                }
                throw exception;
            } finally {
                if (tokenCleared) {
                    this.liveClaimTokens.remove(claimToken);
                } else {
                    this.plugin.getLogger().severe("Claim token " + claimToken + " kept in memory for player " + playerUniqueId + " : the reserved rows will NOT be released automatically by this server run");
                }
            }

        }, this.plugin.getExecutorService());
    }

    private String formatPendingMoney(Map<String, BigDecimal> pendingByEconomy) {
        var economyManager = this.plugin.getEconomyManager();
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (var entry : pendingByEconomy.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;

            var optionalEconomy = economyManager.getEconomy(entry.getKey());
            if (optionalEconomy.isPresent()) {
                sb.append(economyManager.format(optionalEconomy.get(), entry.getValue()));
            } else {
                sb.append(entry.getValue()).append(" ").append(entry.getKey());
            }
        }

        return sb.toString();
    }
}
