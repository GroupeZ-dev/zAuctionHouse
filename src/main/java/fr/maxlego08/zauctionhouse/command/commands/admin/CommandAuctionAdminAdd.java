package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class CommandAuctionAdminAdd extends VCommand {

    public CommandAuctionAdminAdd(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("add");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN_ITEMS);
        this.setDescription(Message.ADMIN_ITEM_ADDED);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addRequireArg("type", (sender, args) -> java.util.List.of("listed", "expired", "purchased"));
        this.addOptionalArg("price", (sender, args) -> java.util.List.of("1000", "0"));
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        if (!(this.sender instanceof Player admin)) {
            return CommandType.DEFAULT;
        }

        String targetName = argAsString(0);
        if (targetName == null) {
            this.auctionManager.message(admin, Message.ADMIN_TARGET_REQUIRED);
            return CommandType.SYNTAX_ERROR;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            this.auctionManager.message(admin, Message.ADMIN_TARGET_NOT_FOUND, "%target%", targetName);
            return CommandType.DEFAULT;
        }

        ItemStack inHand = admin.getInventory().getItemInMainHand();
        if (inHand.getType().isAir()) {
            message(plugin, admin, Message.SELL_ERROR_AIR);
            return CommandType.DEFAULT;
        }

        String type = argAsString(1, "listed");
        String priceAsString = argAsString(2, "0");

        var number = plugin.getConfiguration().getNumberMultiplicationConfiguration().parseNumber(priceAsString);
        if (number == null) {
            return CommandType.SYNTAX_ERROR;
        }

        BigDecimal price = number;
        AuctionEconomy economy = plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION);
        if (economy == null) {
            message(plugin, admin, Message.SELL_ERROR_DEFAULT_ECONOMY);
            return CommandType.DEFAULT;
        }
        ItemStack cloned = inHand.clone();

        removeItemInHand(admin, cloned.getAmount());

        switch (type.toLowerCase(Locale.ENGLISH)) {
            case "expired" -> this.addExpired(target, cloned, price, economy, admin);
            case "purchased" -> this.addPurchased(target, cloned, price, economy, admin);
            default -> this.addListed(target, cloned, price, economy, admin);
        }

        return CommandType.SUCCESS;
    }

    private void removeItemInHand(Player player, int how) {
        var inventory = player.getInventory();
        if (inventory.getItemInMainHand().getAmount() > how) {
            inventory.getItemInMainHand().setAmount(inventory.getItemInMainHand().getAmount() - how);
        } else {
            inventory.setItemInMainHand(new ItemStack(Material.AIR));
        }
        player.updateInventory();
    }

    private void addListed(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        long expiredAt = plugin.getConfiguration().getSellExpiration().getExpiration(target);
        expiredAt = expiredAt > 0 ? System.currentTimeMillis() + (expiredAt * 1000) : 0;

        var creationFuture = this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, List.of(cloned), economy);

        // C-063 : removeItemInHand a vide la main de l'admin AVANT toute ecriture en base. Si
        // l'insertion echoue, l'item est purement et simplement detruit. La compensation est
        // branchee sur le future de CREATION lui-meme (et non sur la chaine avale) pour ne pas
        // rendre l'item une seconde fois lorsque c'est le post-traitement qui echoue.
        creationFuture.exceptionally(throwable -> {
            restoreToAdmin(admin, cloned, throwable);
            return null;
        });

        creationFuture
                .thenAccept(item -> {
                    // Sans applyCategories, l'item apparait dans « toutes categories » mais
                    // n'est rattache a AUCUNE categorie : invisible des qu'un joueur filtre, et
                    // absent des compteurs de categorie. Les trois autres chemins de publication
                    // l'appellent deja (SellService.postSell, CommandAuctionAdminGenerate,
                    // ItemLoaderUtils) : celui-ci etait le seul oubli.
                    this.plugin.getCategoryManager().applyCategories(item);

                    this.auctionManager.addItem(StorageType.LISTED, item);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                    this.auctionManager.updateListedItems(item, true, target);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "listed");

                    // Annonce au cluster, exactement comme SellService.postSell : sans elle
                    // l'annonce reste invisible sur tous les autres serveurs jusqu'au prochain
                    // redemarrage.
                    this.plugin.getAuctionClusterBridge().notifyItemListed(item)
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().severe("Failed to broadcast admin-added listing " + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                });
    }

    private void addExpired(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        // Le 3e argument est la DATE LIMITE DE RECUPERATION, pas l'instant courant :
        // passer System.currentTimeMillis() creait un item DEJA perime, detruit par
        // ExpireService a la premiere ouverture d'inventaire. Meme convention que
        // ZAuctionManager.removeListedItem (0 = jamais).
        long expiration = this.plugin.getConfiguration().getExpireExpiration().getExpiration(target);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        var creationFuture = this.plugin.getStorageManager().createAuctionItem(target, price, expiredAt, List.of(cloned), economy);

        // C-063 : meme compensation que dans addListed, branchee sur le future de CREATION.
        creationFuture.exceptionally(throwable -> {
            restoreToAdmin(admin, cloned, throwable);
            return null;
        });

        creationFuture
                .thenAccept(item -> {
                    item.setStatus(ItemStatus.REMOVED);
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_EXPIRED, PlayerCacheKey.ITEMS_SELLING);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "expired");

                    // createAuctionItem insere TOUJOURS la ligne en LISTED : on la bascule en
                    // base en compare-and-set PUIS on diffuse. La diffusion est chainee sur le
                    // future d'ecriture parce que ItemRemovedListener relit la ligne en base
                    // pour replacer l'item dans le bon store cote distant : elle doit etre
                    // committee avant.
                    //
                    // NE PAS diffuser notifyItemListed ici : cela poserait state=AVAILABLE et
                    // ferait addItem(LISTED, ...) sur tous les autres noeuds, affichant comme
                    // ACHETABLE un item deja expire, le temps d'un aller-retour Redis complet.
                    this.plugin.getStorageManager().updateItem(item, StorageType.LISTED, StorageType.EXPIRED)
                            .thenCompose(ignored -> this.plugin.getAuctionClusterBridge().removeItem(item, StorageType.LISTED, StorageType.EXPIRED))
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().severe("Failed to persist or broadcast admin-added expired item " + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                });
    }

    private void addPurchased(Player target, ItemStack cloned, BigDecimal price, AuctionEconomy economy, Player admin) {
        // Meme convention que ZAuctionManager.purchaseAuctionItem pour la date limite de
        // recuperation : 0 = jamais.
        long expiration = this.plugin.getConfiguration().getPurchaseExpiration().getExpiration(target);
        long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;

        var creationFuture = this.plugin.getStorageManager().createAuctionItem(admin, price, expiredAt, List.of(cloned), economy);

        // C-063 : meme compensation que dans addListed, branchee sur le future de CREATION.
        creationFuture.exceptionally(throwable -> {
            restoreToAdmin(admin, cloned, throwable);
            return null;
        });

        creationFuture
                .thenAccept(item -> {
                    item.setBuyer(target);
                    item.setStatus(ItemStatus.PURCHASED);
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.PURCHASED, item);
                    this.auctionManager.clearPlayerCache(target, PlayerCacheKey.ITEMS_PURCHASED);
                    this.auctionManager.message(admin, Message.ADMIN_ITEM_ADDED, "%items%", item.getItemDisplay(), "%target%", target.getName(), "%type%", "purchased");

                    // Meme raisonnement que addExpired : commit d'abord, diffusion ensuite,
                    // et surtout PAS de notifyItemListed sur un item deja achete.
                    this.plugin.getStorageManager().updateItem(item, StorageType.LISTED, StorageType.PURCHASED)
                            .thenCompose(ignored -> this.plugin.getAuctionClusterBridge().removeItem(item, StorageType.LISTED, StorageType.PURCHASED))
                            .exceptionally(throwable -> {
                                this.plugin.getLogger().severe("Failed to persist or broadcast admin-added purchased item " + item.getId() + ": " + throwable.getMessage());
                                return null;
                            });
                });
    }

    /**
     * Rend a l'administrateur l'item que {@code removeItemInHand} lui a retire, lorsque la
     * creation de l'annonce echoue (C-063).
     * <p>
     * La main est videe AVANT la moindre ecriture en base : sans cette compensation, un
     * {@code createAuctionItem} en echec (base injoignable, serialisation impossible) detruit
     * definitivement l'item. La remise est confinee au thread proprietaire de l'entite (Folia)
     * et le surplus, si l'inventaire est plein, est droppe aux pieds de l'administrateur.
     *
     * @param admin     administrateur a dedommager
     * @param cloned    copie exacte de l'item retire de sa main
     * @param throwable cause de l'echec, journalisee en SEVERE
     */
    private void restoreToAdmin(Player admin, ItemStack cloned, Throwable throwable) {

        this.plugin.getLogger().log(Level.SEVERE, "Failed to create the admin-added item, giving it back to " + admin.getName(), throwable);

        this.plugin.getScheduler().runAtEntity(admin, wrappedTask -> {

            if (!admin.isOnline()) {
                this.plugin.getLogger().severe("[ZAH] " + admin.getName() + " is offline, x" + cloned.getAmount()
                        + " " + cloned.getType().name() + " could NOT be given back after the failed /ah admin add.");
                return;
            }

            admin.getInventory().addItem(cloned.clone())
                    .forEach((slot, dropItemStack) -> admin.getWorld().dropItem(admin.getLocation(), dropItemStack));

            message(this.plugin, admin, Message.SELL_ERROR_INVALID_ITEM);
        });
    }
}
