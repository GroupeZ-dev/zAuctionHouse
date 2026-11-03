package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.ItemPlaceholder;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLoreConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : listes vides, placeholders vides, noms de logs par defaut, statuts de secours")
    void of_defaults() {
        ItemLoreConfiguration lore = ItemLoreConfiguration.of(plugin, new YamlConfiguration());

        assertFalse(lore.forceAmountOne());
        assertTrue(lore.listedAuctionLore().isEmpty());
        assertTrue(lore.listedAuctionPlaceholders().isEmpty());
        assertTrue(lore.multipleListedAuctionLore().isEmpty());
        assertTrue(lore.purchasedLore().isEmpty());
        assertTrue(lore.expiredLore().isEmpty());
        assertTrue(lore.sellingLore().isEmpty());
        assertTrue(lore.beingPurchasedLore().isEmpty());
        assertTrue(lore.historyLore().isEmpty());
        assertTrue(lore.adminLogLore().isEmpty());
        assertTrue(lore.adminLogMultipleLore().isEmpty());
        assertTrue(lore.sellInventoryRemoveItem().isEmpty());
        assertEquals("Item Listed", lore.getLogTypeName(LogType.SALE));
        assertEquals("Item Purchased", lore.getLogTypeName(LogType.PURCHASE));
        assertEquals("Removed from Listing", lore.getLogTypeName(LogType.REMOVE_LISTED));
        assertEquals("Retrieved Selling Item", lore.getLogTypeName(LogType.REMOVE_SELLING));
        assertEquals("Retrieved Expired", lore.getLogTypeName(LogType.REMOVE_EXPIRED));
        assertEquals("Retrieved Purchase", lore.getLogTypeName(LogType.REMOVE_PURCHASED));
        assertEquals(LogType.values().length, lore.logTypeNames().size());
        assertTrue(lore.sellerStatus().contains("ʀᴇᴛʀɪᴇᴠᴇ"));
        assertTrue(lore.buyerStatus().contains("ʙᴜʏ"));
        assertTrue(lore.rightSellerStatus().contains("ʀɪɢʜᴛ"));
        assertTrue(lore.rightBuyerStatus().contains("ʀɪɢʜᴛ"));
    }

    @Test
    @DisplayName("les placeholders presents dans chaque lore sont detectes")
    void of_detectsPlaceholders() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item-lore.force-amount-one", true);
        yaml.set("item-lore.listed-auction-item", List.of("Seller: %seller%", "Price: %price%", "", "%status%"));
        yaml.set("item-lore.multiple-listed-auction-item", List.of("%item_count% items", "%price-price-with-reduction%"));
        yaml.set("item-lore.purchased-item", List.of("%time-remaining%"));
        yaml.set("item-lore.expired-item", List.of("no placeholder here"));
        yaml.set("item-lore.selling-item", List.of("%economy-name% / %economy-display-name%"));
        yaml.set("item-lore.being-purchased-item", List.of("%formatted-expire-date%"));

        ItemLoreConfiguration lore = ItemLoreConfiguration.of(plugin, yaml);

        assertTrue(lore.forceAmountOne());
        assertEquals(Set.of(ItemPlaceholder.SELLER, ItemPlaceholder.PRICE, ItemPlaceholder.STATUS), lore.listedAuctionPlaceholders());
        assertEquals(Set.of(ItemPlaceholder.ITEM_COUNT, ItemPlaceholder.PRICE_WITH_REDUCTION), lore.multipleListedAuctionPlaceholders());
        assertEquals(Set.of(ItemPlaceholder.TIME_REMAINING), lore.purchasedPlaceholders());
        assertTrue(lore.expiredPlaceholders().isEmpty());
        assertEquals(Set.of(ItemPlaceholder.ECONOMY_NAME, ItemPlaceholder.ECONOMY_DISPLAY_NAME), lore.sellingPlaceholders());
        assertEquals(Set.of(ItemPlaceholder.FORMATTED_EXPIRE_DATE), lore.beingPurchasedPlaceholders());
        assertEquals(4, lore.listedAuctionLore().size());
    }

    @Test
    @DisplayName("noms de logs : les cles presentes ecrasent les defauts, les autres restent")
    void of_logTypeNames_overrides() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item-lore.log-type-names.SALE", "Mise en vente");
        yaml.set("item-lore.log-type-names.REMOVE_EXPIRED", "Expire recupere");
        yaml.set("item-lore.log-type-names.UNKNOWN_TYPE", "ignored");

        ItemLoreConfiguration lore = ItemLoreConfiguration.of(plugin, yaml);

        assertEquals("Mise en vente", lore.getLogTypeName(LogType.SALE));
        assertEquals("Expire recupere", lore.getLogTypeName(LogType.REMOVE_EXPIRED));
        assertEquals("Item Purchased", lore.getLogTypeName(LogType.PURCHASE));
        assertEquals(LogType.values().length, lore.logTypeNames().size());
    }

    @Test
    @DisplayName("les statuts configures sont lus")
    void of_statuses() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item-lore.status.seller", "S");
        yaml.set("item-lore.status.buyer", "B");
        yaml.set("item-lore.status.right-seller", "RS");
        yaml.set("item-lore.status.right-buyer", "RB");

        ItemLoreConfiguration lore = ItemLoreConfiguration.of(plugin, yaml);

        assertEquals("S", lore.sellerStatus());
        assertEquals("B", lore.buyerStatus());
        assertEquals("RS", lore.rightSellerStatus());
        assertEquals("RB", lore.rightBuyerStatus());
    }
}
