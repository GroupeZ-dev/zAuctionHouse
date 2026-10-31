package fr.maxlego08.zauctionhouse.utils.component;

import fr.maxlego08.zauctionhouse.api.messages.messages.TitleMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperComponentTest {

    private final PaperComponent component = new PaperComponent();

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static ItemStack itemWithMeta(ItemMeta meta) {
        ItemStack itemStack = mock(ItemStack.class);
        when(itemStack.hasItemMeta()).thenReturn(true);
        when(itemStack.getItemMeta()).thenReturn(meta);
        return itemStack;
    }

    @Test
    @DisplayName("getComponent traduit les codes & et § en MiniMessage et rend le texte attendu")
    void getComponent_legacyCodes() {
        Component green = component.getComponent("&aHello &lWorld");
        assertEquals("Hello World", plain(green));

        Component gold = component.getComponent("§6Gold");
        assertEquals("Gold", plain(gold));
        assertEquals(NamedTextColor.GOLD, firstColoredChild(gold).color());
    }

    private static Component firstColoredChild(Component root) {
        if (root.color() != null) return root;
        for (Component child : root.children()) {
            Component found = firstColoredChild(child);
            if (found != null) return found;
        }
        return null;
    }

    @Test
    @DisplayName("getComponent met en cache : le meme texte rend la meme instance")
    void getComponent_isCached() {
        Component first = component.getComponent("&bcached");
        Component second = component.getComponent("&bcached");
        assertSame(first, second);
    }

    @Test
    @DisplayName("les couleurs hexadecimales #rrggbb sont converties en balises")
    void getComponent_hexColors() {
        Component colored = component.getComponent("#ff0000Red text");
        assertEquals("Red text", plain(colored));
        Component child = firstColoredChild(colored);
        assertEquals(0xff0000, child.color().value());
    }

    @Test
    @DisplayName("une couleur hexadecimale deja balisee n'est pas doublement convertie")
    void getComponent_alreadyTaggedHex() {
        Component colored = component.getComponent("<#00ff00>Green</#00ff00> and <gradient:#ff0000:#0000ff>grad</gradient>");
        assertEquals("Green and grad", plain(colored));
    }

    @Test
    @DisplayName("stripColor retire les codes legacy, hexadecimaux et les balises MiniMessage")
    void stripColor() {
        assertEquals("Hi there", component.stripColor("&c&lHi #ff0000there"));
        assertEquals("Bold", component.stripColor("<bold>Bold</bold>"));
        assertEquals("plain", component.stripColor("plain"));
    }

    @Test
    @DisplayName("sendMessage et sendActionBar envoient le composant deserialise")
    void sendMessage_sendActionBar() {
        CommandSender sender = mock(CommandSender.class);
        Player player = mock(Player.class);

        component.sendMessage(sender, "&aHello");
        component.sendActionBar(player, "&eBar");

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(sender).sendMessage(captor.capture());
        assertEquals("Hello", plain(captor.getValue()));

        ArgumentCaptor<Component> barCaptor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendActionBar(barCaptor.capture());
        assertEquals("Bar", plain(barCaptor.getValue()));
    }

    @Test
    @DisplayName("sendTitle remplace les arguments et transmet les durees en millisecondes")
    void sendTitle() {
        Player player = mock(Player.class);
        TitleMessage titleMessage = new TitleMessage("&aHello %player%", "&7Sub %n%", 500L, 2000L, 250L);

        component.sendTitle(player, titleMessage, "%player%", "Steve", "%n%", 3);

        ArgumentCaptor<Title> captor = ArgumentCaptor.forClass(Title.class);
        verify(player).showTitle(captor.capture());
        Title title = captor.getValue();
        assertEquals("Hello Steve", plain(title.title()));
        assertEquals("Sub 3", plain(title.subtitle()));
        assertEquals(500L, title.times().fadeIn().toMillis());
        assertEquals(2000L, title.times().stay().toMillis());
        assertEquals(250L, title.times().fadeOut().toMillis());
    }

    @Test
    @DisplayName("getItemStackName rend le nom brut (sans couleur) ou une chaine vide")
    void getItemStackName() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("Excalibur", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        assertEquals("Excalibur", component.getItemStackName(itemWithMeta(meta)));

        ItemMeta unnamed = mock(ItemMeta.class);
        when(unnamed.hasDisplayName()).thenReturn(false);
        assertEquals("", component.getItemStackName(itemWithMeta(unnamed)));

        ItemStack noMeta = mock(ItemStack.class);
        when(noMeta.hasItemMeta()).thenReturn(false);
        assertEquals("", component.getItemStackName(noMeta));
    }

    @Test
    @DisplayName("getItemStackLore rend les lignes en texte brut ou une liste vide")
    void getItemStackLore() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(Component.text("Line 1", NamedTextColor.GRAY), Component.text("Line 2")));
        assertEquals(List.of("Line 1", "Line 2"), component.getItemStackLore(itemWithMeta(meta)));

        ItemMeta noLore = mock(ItemMeta.class);
        when(noLore.hasLore()).thenReturn(false);
        assertTrue(component.getItemStackLore(itemWithMeta(noLore)).isEmpty());

        ItemStack noMeta = mock(ItemStack.class);
        when(noMeta.hasItemMeta()).thenReturn(false);
        assertTrue(component.getItemStackLore(noMeta).isEmpty());
    }

    @Test
    @DisplayName("hasDisplayName exige une meta ET un nom ; getItemStackDisplayName serialise en MiniMessage")
    void hasDisplayName_getItemStackDisplayName() {
        ItemMeta named = mock(ItemMeta.class);
        when(named.hasDisplayName()).thenReturn(true);
        when(named.displayName()).thenReturn(Component.text("Sword", NamedTextColor.RED));
        ItemStack namedStack = itemWithMeta(named);
        assertTrue(component.hasDisplayName(namedStack));
        assertEquals("<red>Sword", component.getItemStackDisplayName(namedStack));

        ItemMeta unnamed = mock(ItemMeta.class);
        when(unnamed.hasDisplayName()).thenReturn(false);
        assertFalse(component.hasDisplayName(itemWithMeta(unnamed)));

        ItemStack noMeta = mock(ItemStack.class);
        when(noMeta.hasItemMeta()).thenReturn(false);
        assertFalse(component.hasDisplayName(noMeta));
    }
}
