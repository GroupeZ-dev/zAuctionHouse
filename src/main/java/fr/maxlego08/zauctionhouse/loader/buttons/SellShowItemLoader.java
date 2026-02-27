package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.buttons.sell.SellShowItemButton;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SellShowItemLoader extends ButtonLoader {

    private final AuctionPlugin plugin;

    public SellShowItemLoader(AuctionPlugin plugin) {
        super(plugin, "ZAUCTIONHOUSE_SELL_SHOW_ITEM");
        this.plugin = plugin;
    }

    @Override
    public @Nullable Button load(@NotNull YamlConfiguration configuration, @NotNull String path, @NotNull DefaultButtonValue defaultButtonValue) {

        int emptySlot = configuration.getInt(path + "empty-slot", 0);

        return new SellShowItemButton(plugin, emptySlot);
    }
}
