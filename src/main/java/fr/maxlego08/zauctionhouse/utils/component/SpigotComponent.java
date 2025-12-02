package fr.maxlego08.zauctionhouse.utils.component;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.component.ComponentMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

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
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return "";

        return ChatColor.stripColor(meta.getDisplayName());
    }

    @Override
    public List<String> getItemStackLore(ItemStack itemStack) {

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null || !meta.hasLore()) return List.of();

        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return List.of();

        return lore.stream().map(ChatColor::stripColor).toList();
    }

    @Override
    public boolean hasDisplayName(ItemStack itemStack) {
        return itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName();
    }

    @Override
    public String getItemStackDisplayName(ItemStack itemStack) {
        return itemStack.getItemMeta().getDisplayName().replace("§", "&");
    }
}
