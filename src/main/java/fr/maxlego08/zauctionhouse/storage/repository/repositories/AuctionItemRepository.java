package fr.maxlego08.zauctionhouse.storage.repository.repositories;

import fr.maxlego08.sarah.DatabaseConnection;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.storage.Repository;
import fr.maxlego08.zauctionhouse.api.storage.Tables;
import fr.maxlego08.zauctionhouse.api.storage.dto.AuctionItemDTO;
import fr.maxlego08.zauctionhouse.api.utils.Base64ItemStack;
import fr.maxlego08.zauctionhouse.items.ZAuctionItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class AuctionItemRepository extends Repository {

    public AuctionItemRepository(AuctionPlugin plugin, DatabaseConnection connection) {
        super(plugin, connection, Tables.AUCTION_ITEMS);
    }

    @SuppressWarnings("deprecation") // delegation interne vers la surcharge historique.
    public AuctionItem create(Player seller, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return create(seller.getUniqueId(), seller.getName(), itemId, price, expiredAt, itemStacks, auctionEconomy);
    }

    /**
     * @param sellerUniqueId seller unique id
     * @param sellerName     seller name
     * @param itemId         parent listing row identifier
     * @param price          listing price
     * @param expiredAt      expiration timestamp in milliseconds
     * @param itemStacks     the listing content
     * @param auctionEconomy economy used by the listing
     * @return the created auction item
     * @deprecated encode les ItemStack au moment de l'ecriture, donc APRES que la ligne parente est
     * commitee : un encodage qui rend null produit alors une annonce sans contenu. Utiliser
     * {@link #create(UUID, String, int, BigDecimal, long, List, List, AuctionEconomy)}, qui recoit
     * les charges utiles deja encodees et validees.
     */
    @Deprecated
    public AuctionItem create(UUID sellerUniqueId, String sellerName, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return create(sellerUniqueId, sellerName, itemId, price, expiredAt, itemStacks, itemStacks.stream().map(Base64ItemStack::encode).toList(), auctionEconomy);
    }

    /**
     * Ecrit les contenus DEJA encodes d'une annonce.
     * <p>
     * Aucun INSERT ne part si le lot n'est pas integralement serialisable : la colonne
     * {@code itemstack} est NOT NULL, et {@code Base64ItemStack.encode} rend desormais
     * {@code null} la ou il rendait une chaine vide (C-067). Sans cette garde, l'INSERT ecrirait
     * NULL et l'annonce serait irrecuperable : elle s'afficherait comme un lot normal, l'acheteur
     * serait debite du prix plein et ne recevrait RIEN.
     *
     * @param sellerUniqueId    seller unique id
     * @param sellerName        seller name
     * @param itemId            parent listing row identifier
     * @param price             listing price
     * @param expiredAt         expiration timestamp in milliseconds
     * @param itemStacks        the listing content
     * @param encodedItemStacks the SAME content, already encoded, in the SAME order
     * @param auctionEconomy    economy used by the listing
     * @return the created auction item
     * @throws IllegalStateException if a payload is missing or empty
     */
    public AuctionItem create(UUID sellerUniqueId, String sellerName, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, List<String> encodedItemStacks, AuctionEconomy auctionEconomy) {

        if (encodedItemStacks == null || encodedItemStacks.size() != itemStacks.size()) {
            throw new IllegalStateException("Encoded payload mismatch for item " + itemId);
        }
        for (String encoded : encodedItemStacks) {
            if (encoded == null || encoded.isEmpty()) {
                throw new IllegalStateException("Empty ItemStack payload for item " + itemId);
            }
        }

        for (String encoded : encodedItemStacks) {
            insert(schema -> {
                schema.object("item_id", itemId);
                schema.string("itemstack", encoded);
            });
        }
        return new ZAuctionItem(this.plugin, itemId, this.plugin.getConfiguration().getServerName(), sellerUniqueId, sellerName, price, auctionEconomy, new Date(), new Date(expiredAt), itemStacks);
    }

    /**
     * Loads the content rows of several listings.
     * <p>
     * Au-dela d'environ 32 766 identifiants (SQLite) ou 65 535 (MySQL), le pilote refusait la
     * requete et {@code Repository.select} convertissait l'echec en LISTE VIDE : tous les items
     * du reseau se chargeaient sans aucun contenu et restaient achetables au prix plein (C-001).
     * On pagine, et on propage l'echec : mieux vaut un demarrage avorte qu'un hotel des ventes
     * qui vend du neant.
     *
     * @param ids the parent listing identifiers
     * @return the content rows of every requested listing
     */
    public List<AuctionItemDTO> select(List<String> ids) {
        return selectInOrFail(AuctionItemDTO.class, "item_id", ids);
    }
}
