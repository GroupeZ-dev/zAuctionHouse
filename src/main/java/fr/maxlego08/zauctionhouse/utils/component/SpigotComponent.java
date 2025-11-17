package fr.maxlego08.zauctionhouse.utils.component;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SpigotComponent implements ComponentMessage {

    @Override
    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    @Override
    public void sendActionBar(Player player, String message) {
        player.sendActionBar(message);
    }

    @Override
    public void sendTitle(Player player, TitleMessage titleMessage, Object... args) {

    }

    @Override
    public void sendBossBar(AuctionPlugin plugin, Player player, BossBarMessage bossBarMessage) {

    }

    @Override
    public String getItemStackName(ItemStack itemStack) {
        return "";
    }

}
