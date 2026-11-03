package fr.maxlego08.zauctionhouse.buttons.admin.history;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.plugin.Plugin;

public class AdminRemoveCopyButton extends AdminRemoveOptionButton {

    public AdminRemoveCopyButton(Plugin plugin, String enableMaterial, String disableMaterialString) {
        super(plugin, PlayerCacheKey.ADMIN_REMOVE_COPY, enableMaterial, disableMaterialString);
    }
}
