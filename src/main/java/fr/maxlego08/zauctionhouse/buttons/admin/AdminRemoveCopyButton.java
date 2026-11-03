package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.plugin.Plugin;

public class AdminRemoveCopyButton extends AdminRemoveOptionButton {

    public AdminRemoveCopyButton(Plugin plugin) {
        super(plugin, PlayerCacheKey.ADMIN_REMOVE_COPY);
    }
}
