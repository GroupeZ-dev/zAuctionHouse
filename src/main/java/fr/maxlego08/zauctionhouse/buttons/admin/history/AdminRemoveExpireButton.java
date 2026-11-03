package fr.maxlego08.zauctionhouse.buttons.admin.history;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.plugin.Plugin;

public class AdminRemoveExpireButton extends AdminRemoveOptionButton {

    public AdminRemoveExpireButton(Plugin plugin, String enableMaterial, String disableMaterial) {
        super(plugin, PlayerCacheKey.ADMIN_REMOVE_EXPIRE, enableMaterial, disableMaterial);
    }
}
