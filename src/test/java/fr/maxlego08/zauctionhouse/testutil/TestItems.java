package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Fabrique d'items d'hotel des ventes pour les tests.
 * <p>
 * Les {@link ItemStack} sont des doublures Mockito : sans serveur Bukkit, un vrai ItemStack ne
 * peut ni se construire (ItemFactory) ni se serialiser. Seuls {@code getType()} et
 * {@code getAmount()} sont stubbes.
 */
public final class TestItems {

    private TestItems() {
    }

    public static ItemStack itemStack(Material material, int amount) {
        ItemStack itemStack = mock(ItemStack.class);
        lenient().when(itemStack.getType()).thenReturn(material);
        lenient().when(itemStack.getAmount()).thenReturn(amount);
        lenient().when(itemStack.clone()).thenReturn(itemStack);
        return itemStack;
    }

    public static ItemStack diamond(int amount) {
        return itemStack(Material.DIAMOND, amount);
    }

    /**
     * Charge utile deja encodee, telle que {@code AuctionItemRepository.create} l'attend : son
     * contenu n'est jamais decode par la couche storage.
     */
    public static String encodedPayload(int index) {
        return "payload-" + index;
    }

    public static ZAuctionItem auctionItem(AuctionPlugin plugin, int id, UUID sellerUniqueId, String sellerName,
                                           BigDecimal price, AuctionEconomy economy, long expiredAt, List<ItemStack> itemStacks) {
        return new ZAuctionItem(plugin, id, MockPlugins.SERVER_NAME, sellerUniqueId, sellerName, price, economy,
                new Date(), new Date(expiredAt), itemStacks);
    }

    public static ZAuctionItem auctionItem(AuctionPlugin plugin, int id, UUID sellerUniqueId, AuctionEconomy economy, long expiredAt) {
        return auctionItem(plugin, id, sellerUniqueId, "Seller", BigDecimal.TEN, economy, expiredAt, List.of(diamond(1)));
    }
}
