package fr.maxlego08.zauctionhouse.utils.component;

import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
class SpigotComponentTest {

    private final SpigotComponent component = new SpigotComponent();

    private static ItemStack itemWithMeta(ItemMeta meta) {
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.hasItemMeta()).thenReturn(meta != null);
        when(itemStack.getItemMeta()).thenReturn(meta);
        return itemStack;
    }

    @Test
    @DisplayName("sendMessage et sendActionBar transmettent la chaine brute")
    void sendMessage_sendActionBar() {
        CommandSender sender = mock(CommandSender.class);
        Player player = mock(Player.class);

        component.sendMessage(sender, "§aHello");
        component.sendActionBar(player, "§eBar");

        verify(sender).sendMessage("§aHello");
        verify(player).sendActionBar("§eBar");
    }

    @Test
    @DisplayName("sendTitle et sendBossBar sont des no-op sur Spigot")
    void sendTitle_sendBossBar_noop() {
        Player player = mock(Player.class);
        component.sendTitle(player, new TitleMessage("t", "s", 1L, 2L, 3L));
        component.sendBossBar(null, player, null);
        verifyNoInteractions(player);
    }

    @Test
    @DisplayName("getItemStackName retire les couleurs, ou rend une chaine vide sans meta/nom")
    void getItemStackName() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§c§lExcalibur");
        assertEquals("Excalibur", component.getItemStackName(itemWithMeta(meta)));

        ItemMeta unnamed = mock(ItemMeta.class);
        when(unnamed.hasDisplayName()).thenReturn(false);
        assertEquals("", component.getItemStackName(itemWithMeta(unnamed)));

        assertEquals("", component.getItemStackName(itemWithMeta(null)));
    }

    @Test
    @DisplayName("getItemStackLore retire les couleurs de chaque ligne, ou rend une liste vide")
    void getItemStackLore() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("§7Line 1", "Line 2"));
        assertEquals(List.of("Line 1", "Line 2"), component.getItemStackLore(itemWithMeta(meta)));

        ItemMeta emptyLore = mock(ItemMeta.class);
        when(emptyLore.hasLore()).thenReturn(true);
        when(emptyLore.getLore()).thenReturn(List.of());
        assertTrue(component.getItemStackLore(itemWithMeta(emptyLore)).isEmpty());

        ItemMeta noLore = mock(ItemMeta.class);
        when(noLore.hasLore()).thenReturn(false);
        assertTrue(component.getItemStackLore(itemWithMeta(noLore)).isEmpty());

        assertTrue(component.getItemStackLore(itemWithMeta(null)).isEmpty());
    }

    @Test
    @DisplayName("hasDisplayName exige meta et nom ; getItemStackDisplayName convertit § en &")
    void hasDisplayName_getItemStackDisplayName() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§aSword");
        ItemStack named = itemWithMeta(meta);
        assertTrue(component.hasDisplayName(named));
        assertEquals("&aSword", component.getItemStackDisplayName(named));

        ItemMeta unnamed = mock(ItemMeta.class);
        when(unnamed.hasDisplayName()).thenReturn(false);
        assertFalse(component.hasDisplayName(itemWithMeta(unnamed)));
        assertFalse(component.hasDisplayName(itemWithMeta(null)));
    }

    @Test
    @DisplayName("stripColor remplace seulement le caractere section par &")
    void stripColor() {
        assertEquals("&a&lHi", component.stripColor("§a§lHi"));
        assertEquals("plain", component.stripColor("plain"));
    }
}
