package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public record SoundConfiguration(boolean enable, SoundCategory category, String sound, float volume, float pitch) {

    public static SoundConfiguration of(AuctionPlugin plugin, FileConfiguration configuration, String path) {

        var logger = plugin.getLogger();
        SoundCategory soundCategory = SoundCategory.MASTER;
        try {
            soundCategory = SoundCategory.valueOf(configuration.getString(path + "category", "MASTER"));
        } catch (Exception ignored) {
            logger.warning("The noMoneySound category is not valid for '" + path + "' !, you need to fix that ");
        }

        var sound = configuration.getString(path + "sound", "minecraft:entity.villager.no");

        float volume = (float) configuration.getDouble(path + "volume");
        float pitch = (float) configuration.getDouble(path + "pitch");

        return new SoundConfiguration(configuration.getBoolean(path + "enable"), soundCategory, sound, volume, pitch);
    }

    public void play(Player player) {

        if (!this.enable) return;

        player.playSound(player.getLocation(), this.sound, this.category, this.volume, this.pitch);
    }

}
