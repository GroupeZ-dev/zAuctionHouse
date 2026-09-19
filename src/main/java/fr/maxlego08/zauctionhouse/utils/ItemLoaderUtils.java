package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.storage.dto.ItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.AuctionItemRepository;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public abstract class ItemLoaderUtils {

    protected String getPlayerName(Map<UUID, String> players, UUID uniqueId) {
        var playerName = players.get(uniqueId);
        if (playerName == null) {
            throw new IllegalStateException("Unknown player with UUID " + uniqueId);
        }
        return playerName;
    }

    protected List<String> getIDS(List<ItemDTO> itemDTOS, ItemType itemType) {
        return itemDTOS.stream().filter(e -> e.item_type() == itemType).map(ItemDTO::id).map(String::valueOf).toList();
    }

    /**
     * Construit une annonce a partir de sa ligne items et de ses contenus.
     * <p>
     * QUARANTAINE. Une annonce AUCTION sans aucune ligne de contenu, ou dont un seul ItemStack est
     * illisible, ne doit JAMAIS etre publiee : elle s'affichait comme un lot normal, l'acheteur
     * etait debite du prix plein et ne recevait RIEN (C-001 / C-011 / C-038).
     * <p>
     * La ligne en base est laissee INTACTE : un decodage qui echoue peut etre transitoire (cluster
     * de versions Minecraft melangees), une purge automatique detruirait des items parfaitement
     * valides pour les autres noeuds.
     *
     * @param plugin              the plugin instance
     * @param dto                 the parent items row
     * @param sellerName          the resolved seller name
     * @param currentAuctionItems the content rows of this listing
     * @param auctionEconomy      the resolved economy
     * @return l'annonce, ou {@code null} si elle doit etre mise en QUARANTAINE
     */
    protected AuctionItem createAuctionItem(AuctionPlugin plugin, ItemDTO dto, String sellerName, List<AuctionItemDTO> currentAuctionItems, AuctionEconomy auctionEconomy) {

        if (currentAuctionItems == null || currentAuctionItems.isEmpty()) {
            plugin.getLogger().severe("[Quarantine] Auction item #" + dto.id() + " (seller " + sellerName
                    + ") has NO content row in " + Tables.AUCTION_ITEMS + ": it will NOT be published. "
                    + "The database row is left untouched.");
            return null;
        }

        List<ItemStack> itemStacks = new ArrayList<>(currentAuctionItems.size());
        for (AuctionItemDTO auctionItemDTO : currentAuctionItems) {

            ItemStack itemStack;
            try {
                itemStack = Base64ItemStack.decode(auctionItemDTO.itemstack());
            } catch (Throwable throwable) {
                // Ceinture et bretelles : decode() rend desormais null au lieu de propager, mais la
                // voie NMS historique peut encore lever une Error sur un serveur remappe.
                itemStack = null;
                plugin.getLogger().severe("[Quarantine] Unable to decode content #" + auctionItemDTO.id()
                        + " of auction item #" + dto.id() + ": " + throwable);
            }

            if (itemStack == null) {
                plugin.getLogger().severe("[Quarantine] Auction item #" + dto.id() + " has an unreadable ItemStack ("
                        + Tables.AUCTION_ITEMS + " #" + auctionItemDTO.id() + "): it will NOT be published. "
                        + "The database row is left untouched.");
                return null;
            }

            itemStacks.add(itemStack);
        }

        var auctionItem = new ZAuctionItem(plugin, dto.id(), dto.server_name(), dto.seller_unique_id(), sellerName, dto.price(), auctionEconomy, dto.created_at(), dto.expired_at(), itemStacks);
        auctionItem.setStatus(switch (dto.storage_type()) {
            case LISTED -> ItemStatus.AVAILABLE;
            case PURCHASED -> ItemStatus.PURCHASED;
            case EXPIRED -> ItemStatus.REMOVED;
            case DELETED -> ItemStatus.DELETED;
        });
        return auctionItem;
    }

    protected Result createItems(AuctionPlugin plugin, Map<UUID, String> players, List<ItemDTO> items, PerformanceDebug performanceDebug, BiConsumer<StorageType, Item> biConsumer) {

        var categoryManager = plugin.getCategoryManager();
        var storageManager = plugin.getStorageManager();
        var economyManager = plugin.getEconomyManager();

        long auctionItemsStartTime = performanceDebug.start();
        var auctionItems = storageManager.with(AuctionItemRepository.class).select(getIDS(items, ItemType.AUCTION));
        performanceDebug.end("loadItems.loadAuctionItemsFromDB", auctionItemsStartTime, "count=" + auctionItems.size());

        // O(n+m) au lieu de O(n x m) : un SEUL regroupement, au lieu d'un filtre complet de la
        // liste pour CHAQUE item (20 000 x 25 000 comparaisons a chaque demarrage, C-090).
        // groupingBy preserve l'ordre de rencontre a l'interieur d'un groupe : l'ordre des
        // ItemStack d'un lot est identique a celui de l'ancien filter, ce qui compte pour
        // getItemDisplay et pour l'ordre de remise dans giveItem.
        Map<Integer, List<AuctionItemDTO>> auctionItemsByItemId = auctionItems.stream()
                .collect(Collectors.groupingBy(AuctionItemDTO::item_id));

        int amount = 0;
        int quarantined = 0;

        // Process items
        long processStartTime = performanceDebug.start();
        for (ItemDTO dto : items) {

            // getPlayerName levait une IllegalStateException DANS la boucle, sans aucun rattrapage :
            // une seule ligne dont le vendeur manque de la table players faisait echouer TOUT le
            // chargement, donc tout onEnable. Les FK ne sont pas appliquees sous SQLite (Sarah
            // n emet aucun PRAGMA foreign_keys=ON), l etat est atteignable.
            var sellerName = players.get(dto.seller_unique_id());
            if (sellerName == null) {
                plugin.getLogger().severe("[Quarantine] Auction item #" + dto.id() + " references an unknown seller "
                        + dto.seller_unique_id() + " (no row in " + Tables.PLAYERS + "): it will NOT be published.");
                quarantined++;
                continue;
            }

            String buyerName = null;
            if (dto.buyer_unique_id() != null) {
                buyerName = players.get(dto.buyer_unique_id());
                if (buyerName == null) {
                    // Un nom d acheteur manquant n est PAS une raison de cacher l item a son
                    // proprietaire : on degrade l affichage, on ne met pas en quarantaine.
                    plugin.getLogger().warning("Auction item #" + dto.id() + " references an unknown buyer "
                            + dto.buyer_unique_id() + ", displaying it as Unknown.");
                    buyerName = "Unknown";
                }
            }

            var optional = economyManager.getEconomy(dto.economy_name());
            if (optional.isEmpty()) {
                plugin.getLogger().severe("Impossible to find the economy " + dto.economy_name() + " for auction item id " + dto.id() + ", skip it...");
                quarantined++;
                continue;
            }

            switch (dto.item_type()) {
                case AUCTION -> {

                    var currentAuctionItems = auctionItemsByItemId.getOrDefault(dto.id(), List.of());
                    var auctionItem = this.createAuctionItem(plugin, dto, sellerName, currentAuctionItems, optional.get());

                    if (auctionItem == null) {
                        // Item en quarantaine (contenu vide ou illisible) : deja journalise en SEVERE.
                        quarantined++;
                    } else {

                        if (buyerName != null) {
                            auctionItem.setBuyer(dto.buyer_unique_id(), buyerName);
                        }

                        categoryManager.applyCategories(auctionItem);

                        biConsumer.accept(dto.storage_type(), auctionItem);
                        amount++;
                    }
                }
                case BID -> plugin.getLogger().severe("Bid items not implemented");
                case RENT -> plugin.getLogger().severe("Rent items not implemented");
            }
        }
        performanceDebug.end("loadItems.processItems", processStartTime, "processed=" + amount + ", quarantined=" + quarantined);

        if (quarantined > 0) {
            plugin.getLogger().severe("[Quarantine] " + quarantined + " auction item(s) were NOT published because their "
                    + "data is inconsistent. They are still present in the database: see the SEVERE lines above.");
        }

        return new Result(amount, auctionItems.size(), 0, 0, quarantined);
    }

    public record Result(int amount, int auctionItems, int bidItems, int rentItems, int quarantined) {

    }

}
