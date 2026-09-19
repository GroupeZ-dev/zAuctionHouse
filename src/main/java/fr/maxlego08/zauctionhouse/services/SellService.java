package fr.maxlego08.zauctionhouse.services;

import com.tcoded.folialib.enums.EntityTaskResult;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.services.AuctionSellService;
import fr.maxlego08.zauctionhouse.api.services.result.SellFailReason;
import fr.maxlego08.zauctionhouse.api.services.result.SellResult;
import fr.maxlego08.zauctionhouse.api.storage.StorageManager;
import fr.maxlego08.zauctionhouse.api.tax.TaxResult;
import fr.maxlego08.zauctionhouse.api.tax.TaxType;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SellService extends ZUtils implements AuctionSellService {

    private final AuctionPlugin plugin;
    private final AuctionManager manager;

    public SellService(AuctionPlugin plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Resultat du jalon de vente {@code pending_publish 1 -> 2}.
     * <p>
     * Les trois cas appellent des traitements DIFFERENTS et ne doivent surtout pas etre reduits a
     * un booleen : c'est cette distinction qui empeche a la fois la duplication et la destruction
     * du lot.
     */
    private enum CommitOutcome {
        /** Le jalon est pose : le vendeur s'est dessaisi de son lot, la vente peut se publier. */
        COMMITTED,
        /** La base a repondu 0 ligne : la reservation n'existe plus, rien ne sera reclamable. */
        MISSING,
        /** La base n'a pas repondu : on ignore si le jalon est pose. Etat le plus dangereux. */
        UNKNOWN
    }

    @Override
    public CompletableFuture<SellResult> sellAuctionItems(Player player, BigDecimal price, long expiredAt, Map<Integer, ItemStack> slotItems, AuctionEconomy auctionEconomy) {

        // C-022 : une vente engagee pendant l'arret preleve la taxe, retire les items de
        // l'inventaire, puis voit son INSERT rejete par un pool deja draine : le joueur perd
        // son lot ET sa taxe. Le refus doit etre visible, SellConfirmButton ignorant le
        // SellResult.
        if (this.plugin.isShuttingDown()) {
            message(this.plugin, player, Message.SERVER_SHUTTING_DOWN);
            return CompletableFuture.completedFuture(SellResult.failure("Server is shutting down", SellFailReason.INTERNAL_ERROR));
        }

        // Filter out null or air items and clone them
        var validSlotItems = slotItems.entrySet().stream().filter(entry -> entry.getValue() != null && !entry.getValue().getType().isAir()).collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().clone()));

        if (validSlotItems.isEmpty()) {
            message(this.plugin, player, Message.SELL_ERROR_AIR);
            return CompletableFuture.completedFuture(SellResult.failure("No valid items to sell", SellFailReason.INVALID_ITEM));
        }

        List<ItemStack> itemsToSell = new ArrayList<>(validSlotItems.values());

        SellFailReason validationReason = this.validateItems(player, price, auctionEconomy, itemsToSell);
        if (validationReason != SellFailReason.NONE) {
            return CompletableFuture.completedFuture(SellResult.failure("Validation failed", validationReason));
        }

        // C-067 : on encode AVANT de toucher a l'inventaire ET avant tout INSERT. Un item non
        // serialisable est refuse au joueur, au lieu de lui etre retire puis rendu, et aucune ligne
        // ne part avec un contenu vide. Les objets encodes sont les CLONES qui seront stockes :
        // encoder ici, c'est encoder exactement ce qui sera ecrit, il n'y a pas de double encodage.
        List<String> encodedItems = encodeItems(itemsToSell);
        if (encodedItems == null) {
            message(this.plugin, player, Message.SELL_ERROR_INVALID_ITEM);
            return CompletableFuture.completedFuture(SellResult.failure("Item cannot be serialized", SellFailReason.INVALID_ITEM));
        }

        // Verify items are still in their slots before the async operation
        if (!verifyItemsInSlots(player, validSlotItems)) {
            message(this.plugin, player, Message.SELL_ERROR_CHANGE);
            return CompletableFuture.completedFuture(SellResult.failure("Items changed", SellFailReason.ITEMS_CHANGED));
        }

        CompletableFuture<SellResult> resultFuture = new CompletableFuture<>();

        // Add timeout to prevent indefinite blocking (30 seconds)
        resultFuture.completeOnTimeout(
                SellResult.failure("Operation timed out", SellFailReason.DATABASE_ERROR),
                30, TimeUnit.SECONDS
        );

        // Le prelevement de la taxe est desormais SYNCHRONE (voir applySellTaxAsync) : il peut donc
        // lever sur le thread appelant. On enveloppe pour que l'echec continue d'etre route vers un
        // SellResult TAX_ERROR au lieu de remonter dans le clic du joueur.
        CompletableFuture<TaxResult> taxFuture;
        try {
            taxFuture = applySellTaxAsync(player, price, itemsToSell, auctionEconomy);
        } catch (Throwable throwable) {
            taxFuture = CompletableFuture.failedFuture(throwable);
        }

        taxFuture.thenAccept(taxResult -> {

            if (taxResult == null) {
                // L'enum distingue les deux cas depuis toujours, le code renvoyait le mauvais.
                resultFuture.complete(SellResult.failure("Insufficient funds for tax", SellFailReason.INSUFFICIENT_FUNDS_FOR_TAX));
                return;
            }

            var storageManager = this.plugin.getStorageManager();
            var itemRepository = itemRepository(storageManager);

            // ----------------------------------------------------------------
            // ETAPE 1 : RESERVER. La ligne parente est creee en DELETED avec le jalon
            // pending_publish = 1 (RESERVEE, "le vendeur a encore son lot") et les contenus sont
            // inseres. L'annonce n'est visible d'AUCUN serveur. Rien n'a encore quitte
            // l'inventaire du vendeur : si cette etape echoue, il n'a rien perdu, et une ligne
            // laissee derriere par un crash sera SUPPRIMEE, jamais rendue.
            // ----------------------------------------------------------------
            storageManager.reserveAuctionItem(player, price, expiredAt, itemsToSell, encodedItems, auctionEconomy).whenComplete((auctionItem, reserveError) -> {

                if (reserveError != null || auctionItem == null) {
                    this.plugin.getLogger().severe("Unable to reserve the listing: " + reserveError);
                    refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (reservation failed)");
                    resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                    return;
                }

                // ------------------------------------------------------------
                // ETAPE 2 : sur le thread du joueur, verifier une derniere fois, RETIRER, puis
                // POSER LE JALON dans la foulee.
                // ------------------------------------------------------------
                this.plugin.getScheduler().runAtEntity(player, task -> {

                    if (!player.isOnline() || !verifyItemsInSlots(player, validSlotItems)) {
                        if (player.isOnline()) message(this.plugin, player, Message.SELL_ERROR_CHANGE);
                        // La reservation porte encore pending_publish = 1 : le vendeur a garde son
                        // lot, la ligne peut donc etre detruite sans aucune compensation.
                        cancelReservation(storageManager, auctionItem, "items changed before removal");
                        refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (items changed)");
                        resultFuture.complete(SellResult.failure(player.isOnline() ? "Items changed" : "Player disconnected",
                                player.isOnline() ? SellFailReason.ITEMS_CHANGED : SellFailReason.PLAYER_DISCONNECTED));
                        return;
                    }

                    removeItemsFromSlots(player, validSlotItems);

                    // --------------------------------------------------------
                    // JALON DE VENTE (pending_publish 1 -> 2), IMMEDIATEMENT apres le retrait,
                    // dans la MEME tache de region et avant tout autre travail.
                    //
                    // C'est la seule ecriture qui distingue les deux abandons possibles d'une
                    // vente. Avant elle, une reservation orpheline signifie "le vendeur a garde
                    // son lot" et doit etre SUPPRIMEE ; apres elle, elle signifie "le vendeur a
                    // perdu son lot" et doit rester RECLAMABLE. Les confondre, c'est soit
                    // dupliquer le lot, soit le detruire.
                    //
                    // Appel base SYNCHRONE et bloquant sur le thread de la region : c'est
                    // volontaire. Le deporter sur l'executor rouvrirait exactement la fenetre que
                    // ce jalon existe pour fermer (et le pool peut etre draine a l'arret, ce qui
                    // laisserait des lots retires sans jalon).
                    // --------------------------------------------------------
                    CommitOutcome outcome = commitReservation(itemRepository, auctionItem.getId());

                    if (outcome == CommitOutcome.MISSING) {
                        // Verdict CERTAIN : la ligne n'est plus une reservation non engagee, donc
                        // rien ne deviendra reclamable. On rend le lot ici meme - on est deja sur
                        // le bon thread, le joueur est en ligne, la fenetre est nulle.
                        this.plugin.getLogger().severe("[ZAH] Reservation " + auctionItem.getId()
                                + " vanished between its creation and the sale milestone: the stacks are handed back"
                                + " to " + player.getName() + " and the sale is aborted.");
                        returnItemsNow(player, itemsToSell);
                        saveProfileIfNeeded(player);
                        cancelReservation(storageManager, auctionItem, "sale milestone refused");
                        refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (sale milestone refused)");
                        resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                        return;
                    }

                    if (outcome == CommitOutcome.UNKNOWN) {
                        // La base n'a pas repondu : le jalon est peut-etre pose, peut-etre pas. On
                        // ne rend RIEN. Rendre le lot alors que le jalon a pu passer creerait un
                        // second exemplaire au prochain demarrage ; ne pas le rendre laisse au
                        // balayage de demarrage le soin de trancher sur l'etat REEL de la ligne
                        // (2 -> reclamable, 1 -> supprimee). On choisit la perte tracable plutot
                        // que la duplication silencieuse.
                        this.plugin.getLogger().severe("[ZAH] CRITICAL: the sale milestone of reservation "
                                + auctionItem.getId() + " (seller " + player.getUniqueId() + ", " + itemsToSell.size()
                                + " stack(s)) could not be confirmed by the database. The stacks have LEFT the"
                                + " inventory and are NOT handed back: the next server start decides from the row"
                                + " itself whether the seller can claim them. Manual reconciliation may be needed.");
                        resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                        return;
                    }

                    // C-080 : les contenus sont DEJA durablement en base (etape 1), forcer la
                    // sauvegarde du profil ici ne peut plus faire perdre le lot au joueur ; elle
                    // ne fait que fermer la fenetre de duplication ouverte jusqu'a l'autosave
                    // suivant. Ecriture disque SYNCHRONE sur le thread de la region, d'ou la cle
                    // de configuration action.save-profile-on-sell (defaut true).
                    saveProfileIfNeeded(player);

                    // --------------------------------------------------------
                    // ETAPE 3 : PUBLIER. C'est ici, et seulement ici, que l'annonce devient
                    // visible et achetable sur tout le reseau.
                    // --------------------------------------------------------
                    storageManager.publishAuctionItem(auctionItem).whenComplete((rows, publishError) -> {

                        if (publishError != null || rows == null || rows != 1) {
                            this.plugin.getLogger().severe("Unable to publish listing " + auctionItem.getId()
                                    + " (rows=" + rows + "): " + publishError);
                            recoverFailedPublish(player, auctionItem, itemsToSell, auctionEconomy, taxResult, storageManager, itemRepository);
                            resultFuture.complete(SellResult.failure("Database error", SellFailReason.DATABASE_ERROR));
                            return;
                        }

                        // ----------------------------------------------------
                        // ETAPE 4 : APRES COMMIT. Plus aucun remboursement n'est possible ici :
                        // une exception de postSell ne doit JAMAIS rendre les items ni la taxe,
                        // l'annonce est publiee et achetable sur tout le reseau (C-037).
                        // postSell repasse par le thread principal : il mute les stores et les
                        // index IntArrayList non thread-safe (C-082).
                        // ----------------------------------------------------
                        this.plugin.getScheduler().runNextTick(tick -> {
                            try {
                                this.postSell(player, auctionItem, auctionEconomy, taxResult);
                            } catch (Throwable throwable) {
                                this.plugin.getLogger().severe("[ZAH] Listing " + auctionItem.getId()
                                        + " IS PUBLISHED but postSell failed, manual reconciliation may be needed: " + throwable);
                            }
                            resultFuture.complete(SellResult.success("Item listed successfully", auctionItem));
                        });
                    });
                });
            });

        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to check tax for sell: " + throwable.getMessage());
            resultFuture.complete(SellResult.failure("Tax calculation error", SellFailReason.TAX_ERROR));
            return null;
        });

        return resultFuture;
    }

    /**
     * Rend le depot d'annonces de l'implementation de stockage courante.
     * <p>
     * {@code null} pour un {@link StorageManager} tiers qui n'enregistre pas ce depot : les
     * implementations par defaut de l'API publient directement l'annonce, il n'y a alors aucun
     * jalon a poser et le chemin de vente doit continuer a fonctionner tel quel.
     *
     * @param storageManager le gestionnaire de stockage actif
     * @return le depot, ou {@code null} s'il n'existe pas
     */
    private ItemRepository itemRepository(StorageManager storageManager) {
        try {
            return storageManager.with(ItemRepository.class);
        } catch (Throwable throwable) {
            this.plugin.getLogger().warning("[ZAH] No item repository on the active storage manager,"
                    + " the sale milestone is skipped: " + throwable);
            return null;
        }
    }

    /**
     * Pose le jalon de vente {@code pending_publish 1 -> 2}.
     * <p>
     * Une exception ne peut PAS etre traitee comme un echec : la connexion peut avoir ete coupee
     * apres que l'ecriture a ete commitee cote serveur. Elle est donc rendue comme
     * {@link CommitOutcome#UNKNOWN}, et l'appelant doit alors s'interdire de rendre le lot.
     *
     * @param itemRepository le depot, ou {@code null} si le stockage ne gere pas les reservations
     * @param itemId         identifiant de la reservation
     * @return le verdict du jalon
     */
    private CommitOutcome commitReservation(ItemRepository itemRepository, int itemId) {
        // Pas de reservation a jalonner : l'annonce est deja publiee par l'implementation.
        if (itemRepository == null) return CommitOutcome.COMMITTED;

        try {
            return itemRepository.markReservationCommitted(itemId) == 1 ? CommitOutcome.COMMITTED : CommitOutcome.MISSING;
        } catch (Throwable throwable) {
            this.plugin.getLogger().severe("[ZAH] Unable to mark reservation " + itemId + " as committed: " + throwable);
            return CommitOutcome.UNKNOWN;
        }
    }

    /**
     * Recule le jalon de vente {@code pending_publish 2 -> 1}, juste AVANT de rendre le lot.
     * <p>
     * Tant que le jalon vaut 2, la ligne est "le vendeur a perdu son lot" et sera rendue
     * reclamable au prochain demarrage : lui rendre le lot en main AVANT de reculer le jalon
     * creerait donc un second exemplaire si le recul, ou la suppression qui suit, echouait. Toute
     * reponse autre que 1 - y compris une exception - interdit la remise en main propre.
     *
     * @param itemRepository le depot, ou {@code null} si le stockage ne gere pas les reservations
     * @param itemId         identifiant de la reservation
     * @return {@code true} si et seulement si le lot peut etre rendu en main propre
     */
    private boolean uncommitReservation(ItemRepository itemRepository, int itemId) {
        if (itemRepository == null) return true;

        try {
            int rows = itemRepository.markReservationUncommitted(itemId);
            if (rows == 1) return true;
            this.plugin.getLogger().severe("[ZAH] Reservation " + itemId + " could not be rolled back to the"
                    + " 'seller still holds his items' state (rows=" + rows + "): the stacks are NOT handed back,"
                    + " the reservation is left claimable instead.");
            return false;
        } catch (Throwable throwable) {
            this.plugin.getLogger().severe("[ZAH] Reservation " + itemId + " could not be rolled back to the"
                    + " 'seller still holds his items' state: " + throwable + ". The stacks are NOT handed back,"
                    + " the reservation is left claimable instead.");
            return false;
        }
    }

    /**
     * Supprime une reservation NON ENGAGEE et CONSOMME le future rendu.
     * <p>
     * Le future etait jete : un DELETE en echec ne laissait aucune trace, alors que sa cause la
     * plus probable - la base est tombee - est justement celle de l'echec qui l'a declenche.
     *
     * @param storageManager le gestionnaire de stockage actif
     * @param auctionItem    la reservation a supprimer
     * @param reason         le motif, journalise tel quel
     */
    private void cancelReservation(StorageManager storageManager, AuctionItem auctionItem, String reason) {
        storageManager.cancelReservation(auctionItem).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().severe("[ZAH] CRITICAL: unable to cancel reservation " + auctionItem.getId()
                        + " [" + reason + "]: " + throwable + ". The row still carries the 'seller holds his items'"
                        + " milestone, so the next server start will DELETE it: it will never be handed to the"
                        + " seller and cannot duplicate his stacks.");
            }
        });
    }

    /**
     * Rattrape une vente dont la publication a echoue APRES que le vendeur s'est dessaisi de son
     * lot (C-030).
     * <p>
     * Deux issues, et une seule regle : le lot doit exister a UN exemplaire, jamais zero, jamais
     * deux.
     * <ul>
     *   <li>la tache de region s'execute, le vendeur est en ligne et le jalon a pu etre recule :
     *   le lot lui est rendu en main propre, puis la reservation est supprimee ;</li>
     *   <li>la tache ne s'execute jamais (Folia, entite retiree, ordonnanceur arrete) ou le
     *   vendeur est parti : la reservation est basculee en RECLAMABLE. Elle n'est surtout PAS
     *   annulee, sans quoi le lot serait detruit sans aucune trace - l'ancien code journalisait
     *   depuis l'INTERIEUR de la tache, donc ne journalisait rien du tout quand elle ne tournait
     *   pas, et supprimait quand meme la ligne.</li>
     * </ul>
     *
     * @param player         le vendeur
     * @param auctionItem    la reservation engagee mais non publiee
     * @param itemsToSell    les items a restituer
     * @param auctionEconomy l'economie qui a percu la taxe
     * @param taxResult      le resultat de taxe a rembourser
     * @param storageManager le gestionnaire de stockage actif
     * @param itemRepository le depot, ou {@code null} si le stockage ne gere pas les reservations
     */
    private void recoverFailedPublish(Player player, AuctionItem auctionItem, List<ItemStack> itemsToSell,
                                      AuctionEconomy auctionEconomy, TaxResult taxResult,
                                      StorageManager storageManager, ItemRepository itemRepository) {

        // Ecrit dans la tache de region, lu dans le whenComplete : AtomicBoolean et non boolean.
        AtomicBoolean handedBack = new AtomicBoolean(false);

        this.plugin.getScheduler().runAtEntity(player, task -> {

            if (!player.isOnline()) return;

            // Le jalon est recule AVANT la remise en main propre : voir uncommitReservation.
            if (!uncommitReservation(itemRepository, auctionItem.getId())) return;

            returnItemsNow(player, itemsToSell);

            // C-080 : une restitution non sauvegardee n'est pas durable. Sans cet appel, un
            // crash entre la restitution et l'autosave rendrait le lot invisible au joueur alors
            // que la reservation, elle, a bien ete detruite.
            saveProfileIfNeeded(player);

            refundSellTax(player, auctionEconomy, taxResult, "Refund sell tax (sale failed)");
            handedBack.set(true);

        }).whenComplete((result, throwable) -> {

            if (throwable == null && result == EntityTaskResult.SUCCESS && handedBack.get()) {
                // Le lot est de nouveau dans l'inventaire et la ligne est revenue a "le vendeur a
                // ses items" : elle peut disparaitre. Si ce DELETE echoue, elle reste a 1 et sera
                // SUPPRIMEE au prochain demarrage, jamais rendue au vendeur : pas de duplication.
                cancelReservation(storageManager, auctionItem, "publish failed, stacks handed back");
                return;
            }

            // La trace est ICI, hors de la tache : une tache qui ne s'execute jamais ne peut pas
            // journaliser sa propre absence.
            this.plugin.getLogger().severe("[ZAH] Listing " + auctionItem.getId() + " could not be published and its "
                    + itemsToSell.size() + " stack(s) could NOT be handed back to " + player.getName()
                    + " (" + player.getUniqueId() + ") [task=" + result
                    + (throwable != null ? ", error=" + throwable : "") + "]. The reservation is kept CLAIMABLE by its"
                    + " seller in the 'expired items' section (visible after the next server start). Sell tax of "
                    + (taxResult != null && taxResult.hasTax() ? taxResult.taxAmount() : BigDecimal.ZERO)
                    + " was NOT refunded, manual reconciliation may be needed.");

            makeReservationClaimable(auctionItem, itemRepository);
        });
    }

    /**
     * Bascule une reservation ENGAGEE en conteneur reclamable, sans attendre le balayage de
     * demarrage.
     * <p>
     * Un echec est benin et volontairement non propage : la ligne reste alors a
     * {@code pending_publish = 2} et le prochain demarrage la rendra reclamable de toute facon.
     *
     * @param auctionItem    la reservation engagee
     * @param itemRepository le depot, ou {@code null} si le stockage ne gere pas les reservations
     */
    private void makeReservationClaimable(AuctionItem auctionItem, ItemRepository itemRepository) {
        if (itemRepository == null) return;

        CompletableFuture.runAsync(() -> {
            int rows = itemRepository.makeReservationClaimable(auctionItem.getId());
            if (rows != 1) {
                this.plugin.getLogger().warning("[ZAH] Reservation " + auctionItem.getId()
                        + " was not made claimable immediately (rows=" + rows + "), the next server start will decide.");
            }
        }, this.plugin.getExecutorService()).exceptionally(throwable -> {
            this.plugin.getLogger().severe("[ZAH] Unable to make reservation " + auctionItem.getId()
                    + " claimable immediately: " + throwable + ". It keeps its committed milestone and the next"
                    + " server start will recover it.");
            return null;
        });
    }

    /**
     * Encode tout le lot AVANT la moindre ecriture.
     * <p>
     * On attrape {@code Throwable} : sur un serveur anterieur a 1.20.5 la voie NMS de
     * {@code ItemStackUtils} peut lever une NPE ou une StackOverflowError, pas seulement une
     * IOException.
     *
     * @param itemStacks les items a mettre en vente
     * @return les charges utiles, ou {@code null} si un seul item n'est pas serialisable
     */
    private List<String> encodeItems(List<ItemStack> itemStacks) {
        List<String> encoded = new ArrayList<>(itemStacks.size());
        for (ItemStack itemStack : itemStacks) {
            String value;
            try {
                value = Base64ItemStack.encode(itemStack);
            } catch (Throwable throwable) {
                this.plugin.getLogger().severe("[ZAH] Unable to encode " + itemStack.getType() + " for sale: " + throwable);
                return null;
            }
            if (value == null || value.isEmpty()) {
                this.plugin.getLogger().severe("[ZAH] Base64ItemStack.encode returned an empty payload for " + itemStack.getType()
                        + ", the sale is refused instead of storing an empty listing.");
                return null;
            }
            encoded.add(value);
        }
        return encoded;
    }

    /**
     * Rembourse une taxe de vente deja prelevee.
     * <p>
     * Un remboursement qui echoue est un incident monetaire : il doit laisser une trace
     * exploitable pour une reconciliation manuelle. Avec une economie incapable de crediter un
     * joueur hors ligne, depositChecked refuse AVANT d'appeler le provider et journalise, la
     * ou l'ancien deposit() void perdait l'argent en silence.
     *
     * @param player         le vendeur a rembourser
     * @param auctionEconomy l'economie qui a percu la taxe
     * @param taxResult      le resultat de taxe reellement preleve
     * @param reason         le motif transmis au fournisseur d'economie
     */
    private void refundSellTax(Player player, AuctionEconomy auctionEconomy, TaxResult taxResult, String reason) {
        if (taxResult == null || !taxResult.hasTax()) return;

        try {
            if (!auctionEconomy.depositChecked(player.getUniqueId(), taxResult.taxAmount(), reason)) {
                this.plugin.getLogger().severe("[ZAH] CRITICAL: unable to refund the sell tax of " + taxResult.taxAmount()
                        + " (" + auctionEconomy.getName() + ") to " + player.getUniqueId()
                        + " [" + reason + "]. Manual reconciliation required.");
            }
        } catch (Throwable throwable) {
            // Ce remboursement est appele depuis des chemins de compensation : une exception du
            // fournisseur d'economie ne doit jamais faire dérailler la restitution des items.
            this.plugin.getLogger().severe("[ZAH] CRITICAL: sell tax refund of " + taxResult.taxAmount()
                    + " (" + auctionEconomy.getName() + ") failed for " + player.getUniqueId()
                    + " [" + reason + "]: " + throwable);
        }
    }

    /**
     * Rend physiquement le lot au vendeur. DOIT etre appele sur le thread de l'entite, et
     * uniquement quand la base garantit deja qu'aucune ligne ne restera reclamable.
     * <p>
     * La Map de restes rendue par addItem n'est pas ignoree : tout ce qui ne rentre pas est
     * depose au sol plutot que perdu en silence.
     *
     * @param player      le vendeur
     * @param itemsToSell les items a restituer
     */
    private void returnItemsNow(Player player, List<ItemStack> itemsToSell) {
        for (ItemStack itemStack : itemsToSell) {
            if (itemStack == null) continue;
            player.getInventory().addItem(itemStack.clone())
                    .forEach((slot, leftover) -> player.getWorld().dropItem(player.getLocation(), leftover));
        }
    }

    /**
     * Force la sauvegarde du profil quand la configuration le demande (C-080). Ecriture disque
     * SYNCHRONE : a n'appeler que sur le thread de l'entite.
     *
     * @param player le joueur dont le profil doit devenir durable
     */
    private void saveProfileIfNeeded(Player player) {
        if (this.plugin.getConfiguration().isSaveProfileOnSell()) {
            player.saveData();
        }
    }

    @Override
    public void openSellCommandInventory(Player player, BigDecimal price, AuctionEconomy auctionEconomy) {
        var cache = this.manager.getCache(player);
        var configuration = this.plugin.getConfiguration();

        long expiration = configuration.getSellExpiration().getExpiration(player);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        cache.set(PlayerCacheKey.SELL_PRICE, price);
        cache.set(PlayerCacheKey.SELL_ECONOMY, auctionEconomy);
        cache.set(PlayerCacheKey.SELL_EXPIRED_AT, expiredAt);
        cache.set(PlayerCacheKey.SELL_AMOUNT, 1);
        cache.remove(PlayerCacheKey.SELL_ITEMS);

        this.plugin.getInventoriesLoader().openInventory(player, Inventories.SELL_INVENTORY);
    }

    /**
     * Verifies that all items are still in their expected inventory slots.
     *
     * @param player    the player whose inventory to check
     * @param slotItems map of slot to expected ItemStack
     * @return true if all items are still in their slots with correct amounts, false otherwise
     */
    private boolean verifyItemsInSlots(Player player, Map<Integer, ItemStack> slotItems) {
        PlayerInventory inventory = player.getInventory();

        for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
            int slot = entry.getKey();
            ItemStack expectedItem = entry.getValue();

            ItemStack currentItem;
            if (slot == MAIN_HAND_SLOT) {
                currentItem = inventory.getItemInMainHand();
            } else {
                currentItem = inventory.getItem(slot);
            }

            if (currentItem == null || !currentItem.isSimilar(expectedItem) || currentItem.getAmount() < expectedItem.getAmount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes items from their inventory slots.
     *
     * @param player    the player whose inventory to modify
     * @param slotItems map of slot to ItemStack to remove
     */
    private void removeItemsFromSlots(Player player, Map<Integer, ItemStack> slotItems) {
        PlayerInventory inventory = player.getInventory();

        for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
            int slot = entry.getKey();
            ItemStack itemToRemove = entry.getValue();
            int amountToRemove = itemToRemove.getAmount();

            if (slot == MAIN_HAND_SLOT) {
                ItemStack currentItem = inventory.getItemInMainHand();
                if (currentItem.getAmount() > amountToRemove) {
                    currentItem.setAmount(currentItem.getAmount() - amountToRemove);
                } else {
                    inventory.setItemInMainHand(null);
                }
            } else {
                ItemStack currentItem = inventory.getItem(slot);
                if (currentItem != null) {
                    if (currentItem.getAmount() > amountToRemove) {
                        currentItem.setAmount(currentItem.getAmount() - amountToRemove);
                    } else {
                        inventory.setItem(slot, null);
                    }
                }
            }
        }
    }

    /**
     * Validates if the player is allowed to sell items.
     * This method checks if the price is valid, if the player has reached the maximum
     * number of items for sale, if the world is banned, if the items are blacklisted
     * or whitelisted.
     *
     * @param player         the player who wants to sell items
     * @param price          the price of the items
     * @param auctionEconomy the economy of the items
     * @param itemStacks     the items the player wants to sell
     * @return the fail reason if validation failed, NONE if validation passed
     */
    private SellFailReason validateItems(Player player, BigDecimal price, AuctionEconomy auctionEconomy, List<ItemStack> itemStacks) {

        var economyManager = this.plugin.getEconomyManager();
        var configuration = this.plugin.getConfiguration();
        var ruleManager = this.plugin.getItemRuleManager();
        var maxPrice = auctionEconomy.getMaxPrice(ItemType.AUCTION);
        var minPrice = auctionEconomy.getMinPrice(ItemType.AUCTION);

        if (price.compareTo(maxPrice) > 0) {
            message(plugin, player, Message.PRICE_TOO_HIGH, "%max-price%", economyManager.format(auctionEconomy, maxPrice));
            return SellFailReason.PRICE_TOO_HIGH;
        }

        if (price.compareTo(minPrice) < 0) {
            message(plugin, player, Message.PRICE_TOO_LOW, "%min-price%", economyManager.format(auctionEconomy, minPrice));
            return SellFailReason.PRICE_TOO_LOW;
        }

        // Reject prices containing decimals when whole-number prices are enforced
        if (!configuration.isAllowDecimalPrices() && price.stripTrailingZeros().scale() > 0) {
            message(plugin, player, Message.PRICE_DECIMAL_NOT_ALLOWED);
            return SellFailReason.PRICE_DECIMAL_NOT_ALLOWED;
        }

        long listedItems = manager.getPlayerSellingItems(player).size();
        long maxSellPermission = configuration.getPermission().getLimit(ItemType.AUCTION, player);
        if (listedItems >= maxSellPermission) {
            message(plugin, player, Message.LISTED_ITEMS_LIMIT, "%max-items%", String.valueOf(maxSellPermission));
            return SellFailReason.LISTING_LIMIT_REACHED;
        }

        if (configuration.getWorld().isWorldBanned(ItemType.AUCTION, player.getWorld().getName())) {
            message(plugin, player, Message.WORLD_BANNED);
            return SellFailReason.WORLD_RESTRICTED;
        }

        for (ItemStack itemStack : itemStacks) {

            if (itemStack.getType().isAir()) {
                message(plugin, player, Message.SELL_ERROR_AIR);
                return SellFailReason.INVALID_ITEM;
            }

            if (ruleManager.isBlacklistEnabled() && ruleManager.isBlacklisted(itemStack)) {
                message(plugin, player, Message.ITEM_BLACKLISTED);
                return SellFailReason.BLACKLISTED;
            }

            if (ruleManager.isWhitelistEnabled() && !ruleManager.isWhitelisted(itemStack)) {
                message(plugin, player, Message.ITEM_WHITELISTED);
                return SellFailReason.NOT_WHITELISTED;
            }
        }
        return SellFailReason.NONE;
    }

    /**
     * Calculates and applies the sell tax for a listing.
     * Takes into account all items being sold and uses the highest tax among them
     * (in case of item-specific tax rules).
     *
     * @param player         the player selling
     * @param price          the sale price
     * @param itemStacks     the items being sold (for item-specific rules)
     * @param auctionEconomy the economy used
     * @return a CompletableFuture containing the tax result, or null if the player cannot afford the tax
     */
    private CompletableFuture<TaxResult> applySellTaxAsync(Player player, BigDecimal price, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        var taxConfig = auctionEconomy.getTaxConfiguration();
        var economyManager = this.plugin.getEconomyManager();

        // Check if sell tax applies
        TaxType taxType = taxConfig.getTaxType();
        if (!taxConfig.isEnabled() || (taxType != TaxType.SELL && taxType != TaxType.BOTH)) {
            return CompletableFuture.completedFuture(TaxResult.disabled(price));
        }

        // Calculate tax for each item and keep the highest one
        // This ensures item-specific tax rules are respected
        TaxResult highestTaxResult = null;
        for (ItemStack itemStack : itemStacks) {
            TaxResult itemTaxResult = auctionEconomy.calculateSellTax(player, price, itemStack);
            if (highestTaxResult == null || itemTaxResult.taxAmount().compareTo(highestTaxResult.taxAmount()) > 0) {
                highestTaxResult = itemTaxResult;
            }
        }

        // Fallback if no items (shouldn't happen, but safety check)
        if (highestTaxResult == null) {
            return CompletableFuture.completedFuture(TaxResult.disabled(price));
        }

        final TaxResult taxResult = highestTaxResult;

        if (taxResult.isBypassed()) {
            message(this.plugin, player, Message.TAX_EXEMPT);
            return CompletableFuture.completedFuture(taxResult);
        }

        if (!taxResult.hasTax()) {
            return CompletableFuture.completedFuture(taxResult);
        }

        // C-102 : le has() prealable a disparu. Il ouvrait une fenetre entre la verification et le
        // retrait, et son resultat etait de toute facon perdu par un withdraw() void.
        // IMPORTANT : on reste sur le thread APPELANT (thread du joueur : SellConfirmButton ou
        // CommandAuctionSell). Basculer ce retrait sur getExecutorService() deporterait les
        // providers LEVEL / EXPERIENCE / ITEM, qui manipulent des API Bukkit main-thread-only, sur
        // un pool : refus immediat sous Folia. Le confinement de threads de la couche economie
        // releve du chantier 8.
        boolean withdrawn = auctionEconomy.withdrawChecked(player.getUniqueId(), taxResult.taxAmount(),
                "Sell tax (zAuctionHouse)");

        if (!withdrawn) {
            this.plugin.getScheduler().runAtEntity(player, task ->
                    message(this.plugin, player, Message.TAX_INSUFFICIENT_FUNDS,
                            "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount())));
            return CompletableFuture.completedFuture(null);
        }

        // Send appropriate messages on entity thread
        this.plugin.getScheduler().runAtEntity(player, task -> {
            if (taxResult.isReduced()) {
                message(this.plugin, player, Message.TAX_REDUCED,
                        "%percentage%", String.format("%.1f", 100 - taxResult.reductionPercentage()));
            }
            message(this.plugin, player, Message.TAX_SELL_APPLIED,
                    "%tax%", economyManager.format(auctionEconomy, taxResult.taxAmount()),
                    "%percentage%", String.format("%.1f", taxResult.taxPercentage()));
        });

        return CompletableFuture.completedFuture(taxResult);
    }

    /**
     * Notify the cluster and the database that an auction item has been sold.
     * This method is called after an auction item has been successfully sold.
     *
     * @param player         the player who sold the auction item
     * @param auctionItem    the auction item that was sold
     * @param auctionEconomy the economy of the auction item
     * @param taxResult      the tax result from the sell operation
     */
    private void postSell(Player player, AuctionItem auctionItem, AuctionEconomy auctionEconomy, TaxResult taxResult) {

        // Ajout des catégories de l'item
        this.plugin.getCategoryManager().applyCategories(auctionItem);

        this.manager.addItem(StorageType.LISTED, auctionItem);

        this.manager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH); // Suppression du cache global
        this.manager.clearPlayerCache(player, PlayerCacheKey.ITEMS_SELLING); // Suppression du cache du joueur

        this.manager.updateListedItems(auctionItem, true, player);

        message(this.plugin, player, Message.ITEM_SOLD, "%price%", auctionItem.getFormattedPrice(), "%items%", auctionItem.getItemDisplay());

        // L'annonce est DEJA publiee : un contenu recalcitrant ne doit plus faire remonter
        // d'exception apres commit, le log admin est le seul a en souffrir.
        String encodedItemStack;
        try {
            encodedItemStack = auctionItem.getItemStacks().stream()
                    .filter(Objects::nonNull)
                    .map(Base64ItemStack::encode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(";"));
        } catch (Throwable throwable) {
            encodedItemStack = null;
            this.plugin.getLogger().severe("[ZAH] Unable to encode the log payload for listing " + auctionItem.getId() + ": " + throwable);
        }

        String additionalData = "added_auction_item_to_listed";
        if (taxResult.hasTax()) {
            additionalData += ";sell_tax=" + taxResult.taxAmount();
        }
        this.plugin.getStorageManager().log(LogType.SALE, auctionItem.getId(), player, null, encodedItemStack, auctionItem.getPrice(), auctionEconomy.getName(), additionalData, null);

        this.plugin.getAuctionClusterBridge().notifyItemListed(auctionItem).thenAccept(v -> {
            this.plugin.getLogger().info("Cluster notify item sold");
        }).exceptionally(throwable -> {
            this.plugin.getLogger().severe("Unable to notify item sold: " + throwable.getMessage());
            return null;
        });

        // Discord webhook notification
        if (this.plugin instanceof ZAuctionPlugin zAuctionPlugin) {
            var discordService = zAuctionPlugin.getDiscordWebhookService();
            if (discordService != null && discordService.isEnabled()) {
                discordService.notifyItemSold(player, auctionItem);
            }

            // Broadcast sell notification
            zAuctionPlugin.getBroadcastService().broadcastSell(player, auctionItem);
        }
    }
}
