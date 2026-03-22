package fr.maxlego08.zauctionhouse.loader.permissibles;

import fr.maxlego08.menu.api.loader.PermissibleLoader;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.requirement.Permissible;
import fr.maxlego08.menu.api.utils.TypedMapAccessor;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.permissible.CategoryPermissible;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class CategoryPermissibleLoader extends PermissibleLoader {

    private final AuctionPlugin plugin;

    public CategoryPermissibleLoader(@NotNull AuctionPlugin plugin) {
        super("zauctionhouse_category");
        this.plugin = plugin;
    }

    @Override
    @Nullable
    public Permissible load(@NotNull String path, @NotNull TypedMapAccessor accessor, @NotNull File file) {

        var buttonManager = this.plugin.getInventoriesLoader().getButtonManager();
        String categoryId = accessor.getString("category", "main");
        List<Action> denyActions = loadAction(buttonManager, accessor, "deny", path, file);
        List<Action> successActions = loadAction(buttonManager, accessor, "success", path, file);

        return new CategoryPermissible(this.plugin, categoryId, denyActions, successActions);
    }
}
