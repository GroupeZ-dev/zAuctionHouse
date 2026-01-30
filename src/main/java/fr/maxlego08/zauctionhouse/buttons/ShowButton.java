package fr.maxlego08.zauctionhouse.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;

public class ShowButton extends Button {

    private final AuctionPlugin plugin;

    public ShowButton(Plugin plugin) {
        this.plugin = (AuctionPlugin) plugin;
    }

    @Override
    public ItemStack getCustomItemStack(@NonNull Player player, @NonNull Placeholders placeholders) {
        Item item = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ITEM_SHOW);
        return item.buildItemStack(player);
    }
}
