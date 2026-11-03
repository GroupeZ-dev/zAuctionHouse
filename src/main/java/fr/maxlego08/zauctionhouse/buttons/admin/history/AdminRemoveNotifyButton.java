package fr.maxlego08.zauctionhouse.buttons.admin.history;

import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import org.bukkit.plugin.Plugin;

public class AdminRemoveNotifyButton extends AdminRemoveOptionButton {

    public AdminRemoveNotifyButton(Plugin plugin, String enableMaterial, String disableMaterial) {
        super(plugin, PlayerCacheKey.ADMIN_REMOVE_NOTIFY, enableMaterial, disableMaterial);
    }
}
