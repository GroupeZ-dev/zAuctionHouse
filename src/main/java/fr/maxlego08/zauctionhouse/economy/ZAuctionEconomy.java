package fr.maxlego08.zauctionhouse.economy;

import com.tcoded.folialib.enums.EntityTaskResult;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.economy.PriceFormat;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.tax.TaxConfiguration;
import fr.maxlego08.zauctionhouse.tax.ZTaxConfiguration;
import fr.traqueur.currencies.Currencies;
import fr.traqueur.currencies.CurrencyProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public class ZAuctionEconomy implements AuctionEconomy {

    /**
     * Providers qui font du read-modify-write nu sur des API Bukkit main-thread-only
     * ({@code player.setLevel}, {@code player.giveExp}, inventaire). Les appeler depuis un
     * worker est illegal sous Paper et refuse sous Folia : ces types passent par le thread
     * proprietaire du joueur, ce qui les serialise du meme coup — inutile d'y ajouter un verrou.
     */
    private static final EnumSet<Currencies> OWNER_THREAD_CURRENCIES = EnumSet.of(Currencies.ITEM, Currencies.ZMENUITEMS, Currencies.LEVEL, Currencies.EXPERIENCE);

    /**
     * Economies adossees a l'entite joueur : leur {@code deposit} est un no-op silencieux quand
     * le joueur n'est pas connecte, et l'argent du vendeur disparait purement et simplement.
     * <p>
     * Le contenu est aujourd'hui identique a {@link #OWNER_THREAD_CURRENCIES}, mais les deux
     * ensembles repondent a deux questions differentes (« ou dois-je muter ? » et « puis-je
     * muter hors ligne ? ») : ils sont volontairement declares separement.
     */
    private static final EnumSet<Currencies> OFFLINE_INCAPABLE_CURRENCIES = EnumSet.of(Currencies.ITEM, Currencies.ZMENUITEMS, Currencies.LEVEL, Currencies.EXPERIENCE);

    /**
     * Nombre de verrous « stripes » servant a serialiser les mutations d'un meme compte.
     * Borne fixe et allouee une fois : contrairement a une {@code ConcurrentHashMap<UUID, Object>}
     * alimentee par {@code computeIfAbsent}, cela ne fuit pas une entree par joueur pour la duree
     * de vie de la JVM.
     */
    private static final int ACCOUNT_STRIPES = 64;
    private static final Object[] ACCOUNT_LOCKS = createAccountLocks();

    /**
     * Borne d'attente du saut vers le thread proprietaire du joueur. Indispensable :
     * {@code FoliaImplementation.runAtEntity} peut ne JAMAIS completer son future si l'entite
     * est retiree apres la planification.
     */
    private static final long MUTATION_TIMEOUT_SECONDS = 5L;

    private final AuctionPlugin plugin;
    private final CurrencyProvider currencyProvider;
    private final Currencies currencies;
    private final boolean ownerThreadRequired;
    private final String name;
    private final String displayName;
    private final String format;
    private final String symbol;
    private final String permission;
    private final String depositReason;
    private final String withdrawReason;
    private final PriceFormat priceFormat;
    private final EnumMap<ItemType, BigDecimal> minPrices;
    private final EnumMap<ItemType, BigDecimal> maxPrices;
    private final boolean autoClaim;
    private final boolean mustBeOnline;
    private final TaxConfiguration taxConfiguration;

    public ZAuctionEconomy(AuctionPlugin plugin, CurrencyProvider currencyProvider, Currencies currencies, String name, String displayName, String format, String symbol, String permission, String depositReason, String withdrawReason, PriceFormat priceFormat, EnumMap<ItemType, BigDecimal> minPrices, EnumMap<ItemType, BigDecimal> maxPrices, boolean autoClaim, boolean mustBeOnline, TaxConfiguration taxConfiguration) {
        this.plugin = plugin;
        this.currencyProvider = currencyProvider;
        this.currencies = currencies;
        this.ownerThreadRequired = currencies != null && OWNER_THREAD_CURRENCIES.contains(currencies);
        this.name = name;
        this.displayName = displayName;
        this.format = format;
        this.symbol = symbol;
        this.permission = permission;
        this.depositReason = depositReason;
        this.withdrawReason = withdrawReason;
        this.priceFormat = priceFormat;
        this.minPrices = minPrices;
        this.maxPrices = maxPrices;
        this.autoClaim = autoClaim;
        this.mustBeOnline = mustBeOnline;
        this.taxConfiguration = taxConfiguration != null ? taxConfiguration : ZTaxConfiguration.disabled();
    }

    private static Object[] createAccountLocks() {
        Object[] locks = new Object[ACCOUNT_STRIPES];
        for (int index = 0; index < ACCOUNT_STRIPES; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    /**
     * Indique si le type d'economie donne sait crediter un joueur HORS LIGNE.
     * <p>
     * Le test porte sur la valeur {@link Currencies} declaree dans {@code economies.yml} et non
     * sur la classe concrete du provider : cela evite une dependance de compilation dure sur les
     * classes internes de CurrenciesAPI, et {@code ZMENUITEMS} est couvert explicitement plutot
     * que par heritage.
     *
     * @param currencies le type d'economie a tester, {@code null} etant traite comme capable
     * @return {@code true} si un depot hors ligne aboutit reellement
     */
    public static boolean supportsOfflineDeposit(Currencies currencies) {
        return currencies == null || !OFFLINE_INCAPABLE_CURRENCIES.contains(currencies);
    }

    /**
     * Verrou du couple (provider, joueur). Deux economies declarees sur le MEME provider
     * partagent le verrou d'un meme compte, ce qui est voulu : c'est le solde sous-jacent qui
     * doit etre serialise, pas le nom de l'economie. Deux providers differents ne se genent pas.
     *
     * @param playerId le compte a verrouiller
     * @return le verrou stripe correspondant
     */
    private Object accountLock(UUID playerId) {
        int hash = (System.identityHashCode(this.currencyProvider) * 31) + playerId.hashCode();
        hash ^= (hash >>> 16);
        return ACCOUNT_LOCKS[Math.floorMod(hash, ACCOUNT_STRIPES)];
    }

    public AuctionPlugin getPlugin() {
        return this.plugin;
    }

    public CurrencyProvider getCurrencyProvider() {
        return this.currencyProvider;
    }

    /**
     * Retourne le type d'economie declare dans {@code economies.yml}.
     *
     * @return le type d'economie, potentiellement {@code null} pour une construction historique
     */
    public Currencies getCurrencies() {
        return this.currencies;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public String getFormat() {
        return this.format;
    }

    @Override
    public CompletableFuture<BigDecimal> get(UUID playerId) {
        return CompletableFuture.completedFuture(this.currencyProvider.getBalance(playerId));
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerId, BigDecimal price) {
        return get(playerId).thenApply(balance -> balance.compareTo(price) >= 0);
    }

    @Override
    public void deposit(UUID playerId, BigDecimal value, String reason) {
        mutateBalance(playerId, () -> this.currencyProvider.deposit(playerId, value, reason), "deposit");
    }

    @Override
    public void withdraw(UUID playerId, BigDecimal value, String reason) {
        mutateBalance(playerId, () -> this.currencyProvider.withdraw(playerId, value, reason), "withdraw");
    }

    @Override
    public boolean supportsOfflineDeposit() {
        return supportsOfflineDeposit(this.currencies);
    }

    @Override
    public boolean withdrawChecked(UUID playerId, BigDecimal value, String reason) {
        if (playerId == null) return false;
        if (value == null || value.signum() <= 0) return true;

        // Lecture, pre-controle, retrait et post-controle sont executes DANS la meme section
        // confinee : c'est la seule chose qui donne un sens au pre-controle (C-084).
        return executeConfined(playerId, () -> withdrawConfined(playerId, value, reason), "withdraw");
    }

    /**
     * Corps du retrait verifie. Toujours execute a l'interieur de
     * {@link #executeConfined(UUID, BooleanSupplier, String)}, donc soit sous le verrou du
     * compte, soit sur le thread proprietaire du joueur.
     *
     * @param playerId le joueur a debiter
     * @param value    le montant a debiter
     * @param reason   la raison du retrait
     * @return {@code true} si l'argent a ete retire, {@code false} si rien n'a bouge
     */
    private boolean withdrawConfined(UUID playerId, BigDecimal value, String reason) {

        BigDecimal before;
        try {
            before = this.currencyProvider.getBalance(playerId);
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "[" + this.name + "] Unable to read the balance of "
                    + playerId + " before withdrawing " + value, exception);
            return false;
        }

        // PRE-controle : c'est le SEUL controle sur lequel on s'autorise a refuser.
        // Il ne peut pas se tromper dans le sens dangereux : quand il refuse, rien n'a bouge.
        if (before == null || before.compareTo(value) < 0) return false;

        try {
            this.currencyProvider.withdraw(playerId, value, reason);
        } catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "[" + this.name + "] Withdraw of " + value
                    + " from " + playerId + " failed", exception);
            return false;
        }

        // POST-controle : PUREMENT INFORMATIF. On ne rend jamais false ici. Un depot
        // concurrent d'un autre plugin entre les deux lectures ferait sinon echouer un
        // retrait pourtant reussi, et l'appelant abandonnerait APRES avoir preleve
        // l'argent : on remplacerait une duplication par une destruction.
        try {
            BigDecimal after = this.currencyProvider.getBalance(playerId);
            if (after != null && before.subtract(after).compareTo(value) < 0) {
                this.plugin.getLogger().warning("[" + this.name + "] Withdraw of " + value + " from "
                        + playerId + " reported success but the balance only moved by "
                        + before.subtract(after) + ". Concurrent economy write, or the provider"
                        + " silently ignored the withdrawal.");
            }
        } catch (Exception exception) {
            this.plugin.getLogger().warning("[" + this.name + "] Unable to verify the balance of "
                    + playerId + " after withdrawing " + value + ": " + exception.getMessage());
        }

        return true;
    }

    @Override
    public boolean depositChecked(UUID playerId, BigDecimal value, String reason) {
        if (playerId == null) return false;
        if (value == null || value.signum() <= 0) return true;

        // Refus AVANT tout appel : ces providers ne savent pas crediter un joueur hors ligne
        // et se contentent d'un no-op silencieux. Mieux vaut un false exploitable par
        // l'appelant (dette PENDING) qu'un depot fantome.
        if (!supportsOfflineDeposit() && Bukkit.getPlayer(playerId) == null) {
            this.plugin.getLogger().warning("[" + this.name + "] Refused to deposit " + value
                    + " to the offline player " + playerId + ": this economy type cannot credit an offline player.");
            return false;
        }

        return executeConfined(playerId, () -> {
            try {
                this.currencyProvider.deposit(playerId, value, reason);
                return true;
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.SEVERE, "[" + this.name + "] Deposit of " + value
                        + " to " + playerId + " failed", exception);
                return false;
            }
        }, "deposit");
    }

    /**
     * Variante sans verdict de {@link #executeConfined(UUID, BooleanSupplier, String)}, utilisee
     * par les deux mutations historiques {@link #deposit(UUID, BigDecimal, String)} et
     * {@link #withdraw(UUID, BigDecimal, String)}, dont le type de retour {@code void} ne peut
     * pas changer sans casser le descripteur JVM des appelants deja compiles.
     *
     * @param playerId  le compte mute
     * @param mutation  la mutation a executer
     * @param operation le libelle de l'operation, pour la journalisation
     */
    private void mutateBalance(UUID playerId, Runnable mutation, String operation) {
        executeConfined(playerId, () -> {
            mutation.run();
            return true;
        }, operation);
    }

    /**
     * Execute une mutation de solde en respectant deux invariants (C-084) :
     * <ol>
     *   <li>les providers main-thread-only sont executes sur le thread proprietaire du joueur
     *       (inline si on y est deja, sinon saut + attente bornee) ;</li>
     *   <li>tous les autres sont serialises par compte via un verrou stripe.</li>
     * </ol>
     * Le contrat reste SYNCHRONE : l'appelant doit pouvoir considerer l'argent comme deplace au
     * retour, sans quoi on remplacerait une course par un mensonge. On ne bloque JAMAIS le thread
     * proprietaire (branche inline), donc pas d'auto-interblocage ; l'attente est bornee a
     * {@link #MUTATION_TIMEOUT_SECONDS} secondes et journalisee en SEVERE.
     * <p>
     * En cas d'echec du saut, le retour est {@code false} : le resultat reel est INCONNU et l'on
     * prefere faire abandonner l'appelant plutot que lui faire croire a un mouvement d'argent qui
     * n'a peut-etre pas eu lieu.
     * <p>
     * LIMITE ASSUMEE : ce verrou ne protege que les appels passant par {@code ZAuctionEconomy}.
     * Un plugin tiers touchant le meme compte via Vault n'est pas serialise — la fenetre est
     * reduite, pas fermee. Sa fermeture reelle exige le retour d'un verdict cote CurrenciesAPI.
     *
     * @param playerId  le compte mute
     * @param action    l'operation a executer, qui rend son propre verdict
     * @param operation le libelle de l'operation, pour la journalisation
     * @return le verdict de {@code action}, ou {@code false} si elle n'a pas pu etre executee
     */
    private boolean executeConfined(UUID playerId, BooleanSupplier action, String operation) {

        if (!this.ownerThreadRequired) {
            synchronized (accountLock(playerId)) {
                return action.getAsBoolean();
            }
        }

        var scheduler = this.plugin.getScheduler();
        Player player = Bukkit.getPlayer(playerId);

        // Joueur hors ligne : ce provider ne peut de toute facon rien faire d'utile (C-032, filtre
        // en amont par depositChecked). Deja sur le thread proprietaire : execution inline, sans
        // verrou, sans quoi on s'attendrait soi-meme.
        if (scheduler == null || player == null || scheduler.isOwnedByCurrentRegion(player)) {
            return action.getAsBoolean();
        }

        AtomicBoolean outcome = new AtomicBoolean(false);
        try {
            // Timeout indispensable : FoliaImplementation.runAtEntity peut ne JAMAIS completer
            // son future si l'entite est retiree apres la planification.
            EntityTaskResult result = scheduler.runAtEntity(player, wrappedTask -> outcome.set(action.getAsBoolean())).get(MUTATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (result != EntityTaskResult.SUCCESS) {
                this.plugin.getLogger().severe("Economy " + operation + " for " + playerId + " was not executed (" + result + ") on economy " + this.name);
                return false;
            }
            return outcome.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.plugin.getLogger().severe("Interrupted while performing a " + operation + " for " + playerId + " on economy " + this.name + ". The outcome of this operation is UNKNOWN.");
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to " + operation + " on the owning thread for " + playerId + " on economy " + this.name + ". The outcome of this operation is UNKNOWN, a manual reconciliation may be required.", exception);
            return false;
        }
    }

    @Override
    public String getSymbol() {
        return this.symbol;
    }

    @Override
    @Nullable
    public String getPermission() {
        return this.permission;
    }

    @Override
    public PriceFormat getPriceFormat() {
        return this.priceFormat;
    }

    @Override
    public String getDepositReason() {
        return this.depositReason;
    }

    @Override
    public String getWithdrawReason() {
        return this.withdrawReason;
    }

    @Override
    public boolean isAutoClaim() {
        return this.autoClaim;
    }

    @Override
    public boolean mustBeOnline() {
        return this.mustBeOnline;
    }

    @Override
    public BigDecimal getMaxPrice(ItemType itemType) {
        return this.maxPrices.get(itemType);
    }

    @Override
    public BigDecimal getMinPrice(ItemType itemType) {
        return this.minPrices.get(itemType);
    }

    @Override
    public TaxConfiguration getTaxConfiguration() {
        return this.taxConfiguration;
    }
}
