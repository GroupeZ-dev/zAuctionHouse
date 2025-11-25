package fr.maxlego08.zauctionhouse.api.hooks.permission;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Set;

public interface OfflinePermission {

    List<OfflinePermissionResult> hasPermissions(OfflinePlayer offlinePlayer, Set<String> permissions);

}
