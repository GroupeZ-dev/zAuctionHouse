package fr.maxlego08.zauctionhouse.storage.repository.repositeries;

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

    public AuctionItem create(Player seller, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        return create(seller.getUniqueId(), seller.getName(), itemId, price, expiredAt, itemStacks, auctionEconomy);
    }

    public AuctionItem create(UUID sellerUniqueId, String sellerName, int itemId, BigDecimal price, long expiredAt, List<ItemStack> itemStacks, AuctionEconomy auctionEconomy) {
        for (ItemStack itemStack : itemStacks) {
            insert(schema -> {
                schema.object("item_id", itemId);
                schema.string("itemstack", Base64ItemStack.encode(itemStack));
            });
        }
        return new ZAuctionItem(this.plugin, itemId, this.plugin.getConfiguration().getServerName(), sellerUniqueId, sellerName, price, auctionEconomy, new Date(), new Date(expiredAt), itemStacks);
    }

    public List<AuctionItemDTO> select(List<String> ids) {
        return ids.isEmpty() ? List.of() : select(AuctionItemDTO.class, schema -> schema.whereIn("item_id", ids));
    }
}
