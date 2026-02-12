package fr.maxlego08.zauctionhouse.buttons.sell;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.economy.AuctionEconomy;
import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SellBuyButton extends Button {

    private final ZAuctionPlugin plugin;

    public SellBuyButton(Plugin plugin) {
        this.plugin = (ZAuctionPlugin) plugin;
    }

    @Override
    public void onClick(Player player, InventoryClickEvent event, InventoryEngine inventory, int page, Placeholders placeholders) {
        super.onClick(player, event, inventory, page, placeholders);

        List<ItemStack> itemStacks = new ArrayList<>();

        Inventory spigotInventory = inventory.getSpigotInventory();
        Optional<SellSlotButton> optional = inventory.getButtons().stream().filter(e -> e instanceof SellSlotButton).map(e -> (SellSlotButton) e).findFirst();

        if (optional.isEmpty()) {
            player.sendMessage("§cError with your inventory, impossible to find SellSlotButton");
            return;
        }

        SellSlotButton button = optional.get();

        for (int slot : button.getSlots()) {
            ItemStack itemStack = spigotInventory.getItem(slot);
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                itemStacks.add(itemStack.clone());
            }
            spigotInventory.setItem(slot, new ItemStack(Material.AIR));
        }

        if (itemStacks.isEmpty()) {
            this.plugin.getAuctionManager().message(player, Message.SELL_INVENTORY_EMPTY);
            return;
        }

        AuctionPlugin auctionPlugin = this.plugin;
        var manager = auctionPlugin.getAuctionManager();
        var cache = manager.getCache(player);
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy auctionEconomy = cache.get(PlayerCacheKey.SELL_ECONOMY, auctionPlugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION));
        long expiredAt = cache.get(PlayerCacheKey.SELL_EXPIRED_AT, 0L);

        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        manager.getSellService().sellAuctionItems(player, price, expiredAt, itemStacks, auctionEconomy);

        player.closeInventory();
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        MenuItemStack menuItemStack = this.getItemStack();

        var manager = this.plugin.getAuctionManager();
        var cache = manager.getCache(player);
        BigDecimal price = cache.get(PlayerCacheKey.SELL_PRICE, BigDecimal.ZERO);
        AuctionEconomy auctionEconomy = cache.get(PlayerCacheKey.SELL_ECONOMY, this.plugin.getEconomyManager().getDefaultEconomy(ItemType.AUCTION));
        int amount = cache.get(PlayerCacheKey.SELL_AMOUNT, 1);

        placeholders.register("price", plugin.getEconomyManager().format(auctionEconomy, price));
        placeholders.register("economy", auctionEconomy.getName());
        placeholders.register("amount", String.valueOf(amount));

        return menuItemStack.build(player, false, placeholders);
    }
}
