package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.AuctionItemRepository;
import fr.maxlego08.zauctionhouse.storage.repository.repositories.ItemRepository;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Insertion d'annonces de test par les repositories de production (ligne parente + contenus).
 * <p>
 * Les contenus sont des charges utiles deja encodees, selon la convention de {@link Base64Stubs}
 * ({@code "payload-N"} = N diamants).
 */
public final class ListingFixtures {

    public static final BigDecimal PRICE = new BigDecimal("12.50");

    private ListingFixtures() {
    }

    /**
     * Horodatage d'expiration dans une heure : l'annonce n'est pas expiree.
     */
    public static long inOneHour() {
        return System.currentTimeMillis() + 3_600_000L;
    }

    /**
     * Cree la ligne parente dans le conteneur demande puis une ligne de contenu par charge utile.
     *
     * @return l'identifiant de la ligne parente
     */
    public static int listing(ItemRepository items, AuctionItemRepository auctionItems, AuctionEconomy economy,
                              UUID seller, String sellerName, StorageType storageType, long expiredAt, String... payloads) {
        int itemId = items.create(seller, ItemType.AUCTION, PRICE, expiredAt, economy, storageType);
        if (itemId <= 0) throw new IllegalStateException("insertion de la ligne parente echouee : id = " + itemId);
        if (payloads.length > 0) {
            List<ItemStack> itemStacks = new ArrayList<>(payloads.length);
            for (int index = 0; index < payloads.length; index++) itemStacks.add(TestItems.diamond(index + 1));
            auctionItems.create(seller, sellerName, itemId, PRICE, expiredAt, itemStacks, List.of(payloads), economy);
        }
        return itemId;
    }

    public static int listing(ItemRepository items, AuctionItemRepository auctionItems, AuctionEconomy economy,
                              UUID seller, StorageType storageType, String... payloads) {
        return listing(items, auctionItems, economy, seller, "Seller", storageType, inOneHour(), payloads);
    }

    /**
     * Annonce LISTED, non expiree, avec les contenus donnes.
     */
    public static int listed(ItemRepository items, AuctionItemRepository auctionItems, AuctionEconomy economy, UUID seller, String... payloads) {
        return listing(items, auctionItems, economy, seller, StorageType.LISTED, payloads);
    }
}
