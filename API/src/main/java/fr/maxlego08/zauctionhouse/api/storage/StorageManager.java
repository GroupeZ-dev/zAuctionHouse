package fr.maxlego08.zauctionhouse.api.storage;

import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.log.LogContentType;
import fr.maxlego08.zauctionhouse.api.log.LogType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageManager {

    boolean onEnable();

    void onDisable();

    void loadItems();

    void upsertPlayer(Player player);

    CompletableFuture<AuctionItem> createAuctionItem(Player seller, BigDecimal price, long expiredAt, ItemStack clonedItemStack, AuctionEconomy auctionEconomy);

    <T extends Repository> T with(Class<T> module);

    void updateItem(Item item, StorageType storageType);

    void log(LogType logType, LogContentType contentType, int contentId, Player player, UUID targetUniqueId, ItemStack itemStack,
             BigDecimal price, String economyName, String additionalData);
}
