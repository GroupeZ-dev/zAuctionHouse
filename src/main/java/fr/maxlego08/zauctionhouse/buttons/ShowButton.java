package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.items.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ShowButton extends Button {

    private final AuctionPlugin plugin;

    public ShowButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public ItemStack getCustomItemStack(Player player) {
        Item item = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ITEM_SHOW);
        return item.buildItemStack(player);
    }
}
