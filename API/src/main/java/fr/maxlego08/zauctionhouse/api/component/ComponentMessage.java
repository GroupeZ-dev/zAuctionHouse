package fr.maxlego08.zauctionhouse.api.component;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.messages.BossBarMessage;
import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Interface for handling various types of component-based messages and interactions within the server.
 */
public interface ComponentMessage {

    /**
     * Sends a text message to a specified CommandSender, which could be a player or the console.
     *
     * @param sender  the recipient of the message
     * @param message the message to be sent
     */
    void sendMessage(CommandSender sender, String message);

    /**
     * Sends an action bar message to a specified player.
     *
     * @param player  the player who will receive the action bar message
     * @param message the message to display on the action bar
     */
    void sendActionBar(Player player, String message);

    /**
     * Sends a title message to a specified player, with options for the main title, subtitle, and fade timings.
     *
     * @param player       the player who will receive the title message
     * @param titleMessage the TitleMessage object containing the title, subtitle, and timing information
     * @param args         The arguments
     */
    void sendTitle(Player player, TitleMessage titleMessage, Object... args);

    /**
     * Sends a boss bar message to a specified player, which can be used to display progress or important information.
     *
     * @param plugin         the plugin instance responsible for sending the boss bar
     * @param player         the player who will receive the boss bar message
     * @param bossBarMessage the BossBarMessage object containing the details of the boss bar
     */
    void sendBossBar(AuctionPlugin plugin, Player player, BossBarMessage bossBarMessage);

    /**
     * Gets the name of a specified ItemStack, which may be a translated name depending on the item.
     *
     * @param itemStack the ItemStack for which to retrieve the name
     * @return the name of the ItemStack
     */
    String getItemStackName(ItemStack itemStack);

    List<String> getItemStackLore(ItemStack itemStack);

    boolean hasDisplayName(ItemStack itemStack);

    String getItemStackDisplayName(ItemStack itemStack);
}

