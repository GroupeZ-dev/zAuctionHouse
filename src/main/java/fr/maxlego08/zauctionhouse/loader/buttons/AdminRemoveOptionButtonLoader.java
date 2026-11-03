package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.buttons.admin.history.AdminRemoveOptionButton;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdminRemoveOptionButtonLoader<T extends AdminRemoveOptionButton> extends ButtonLoader {

    private final Class<T> clazz;

    public AdminRemoveOptionButtonLoader(@NotNull Plugin plugin, Class<T> clazz, @NotNull String name) {
        super(plugin, name);
        this.clazz = clazz;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {

        var enableMaterial = configuration.getString(path + ".enable-material", "PAPER");
        var disableMaterial = configuration.getString(path + ".disable-material", "BARRIER");

        try {
            var constructor = clazz.getConstructor(Plugin.class, String.class, String.class);
            return constructor.newInstance(this.plugin, enableMaterial, disableMaterial);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return null;
    }
}
