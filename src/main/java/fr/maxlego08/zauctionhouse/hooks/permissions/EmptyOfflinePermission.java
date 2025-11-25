package fr.maxlego08.zauctionhouse.hooks.permissions;

import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermission;
import fr.maxlego08.zauctionhouse.api.hooks.permission.OfflinePermissionResult;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class EmptyOfflinePermission implements OfflinePermission {

    @Override
    public CompletableFuture<List<OfflinePermissionResult>> hasPermissions(OfflinePlayer offlinePlayer, Set<String> permissions) {
        return CompletableFuture.completedFuture(permissions.stream().map(permission -> new OfflinePermissionResult(permission, false)).toList());
    }
}
