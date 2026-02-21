package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public class LoadingSlotLoader extends ButtonLoader {

    private final Class<? extends Button> clazz;

    public LoadingSlotLoader(@NotNull Plugin plugin, @NotNull Class<? extends Button> clazz, String name) {
        super(plugin, name);
        this.clazz = clazz;
    }

    @Override
    @Nullable
    public Button load(@NonNull YamlConfiguration configuration, @NonNull String path, @NonNull DefaultButtonValue defaultButtonValue) {
        try {
            int slot = configuration.getInt(path + "loading-slot", 0);
            return this.clazz.getConstructor(Plugin.class, int.class).newInstance(this.plugin, slot);
        } catch (Exception exception) {
            try {
                return this.clazz.getDeclaredConstructor().newInstance();
            } catch (Exception exception2) {
                exception.printStackTrace();
            }
        }
        return null;
    }

}
