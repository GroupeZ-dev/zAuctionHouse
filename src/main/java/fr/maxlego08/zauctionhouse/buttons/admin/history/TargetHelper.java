package fr.maxlego08.zauctionhouse.buttons.admin.history;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public abstract class TargetHelper extends Button {

    protected final AuctionPlugin plugin;

    public TargetHelper(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    protected Optional<UUID> getTargetUniqueId(Player player) {
        return Optional.ofNullable(this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ADMIN_TARGET_UUID));
    }

    protected Optional<String> getTargetName(Player player) {
        return Optional.ofNullable(this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.ADMIN_TARGET_NAME));
    }
}
