package fr.maxlego08.zauctionhouse.placeholder;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocalPlaceholderTest {

    AuctionPlugin plugin;
    Player player;
    LocalPlaceholder placeholder;

    @BeforeEach
    void setUp() {
        plugin = mock(AuctionPlugin.class);
        player = mock(Player.class);
        placeholder = new LocalPlaceholder(plugin);
    }

    @Test
    @DisplayName("prefixe fixe 'zauctionhouse', plugin conserve, aucun placeholder au depart")
    void initialState() {
        assertEquals("zauctionhouse", placeholder.getPrefix());
        assertSame(plugin, placeholder.getPlugin());
        assertTrue(placeholder.getAutoPlaceholders().isEmpty());
    }

    @Test
    @DisplayName("register(Function) et register(BiFunction, args) ajoutent des AutoPlaceholder dans l'ordre")
    void register_addsInOrder() {
        placeholder.register("listed_items", p -> "1", "desc one");
        placeholder.register("category_count_", (p, value) -> value, "desc two", "<category_id>", "<extra>");

        List<AutoPlaceholder> all = placeholder.getAutoPlaceholders();
        assertEquals(2, all.size());
        assertEquals("listed_items", all.get(0).getStartWith());
        assertEquals("desc one", all.get(0).getDescription());
        assertTrue(all.get(0).getArgs().isEmpty());
        assertEquals("category_count_", all.get(1).getStartWith());
        assertEquals(List.of("<category_id>", "<extra>"), all.get(1).getArgs());
    }

    @Test
    @DisplayName("onRequest : Function resolue par egalite, BiFunction par prefixe avec la valeur residuelle")
    void onRequest_resolves() {
        placeholder.register("listed_items", p -> "42", "d");
        placeholder.register("category_count_", (p, value) -> "count:" + value, "d", "<id>");

        assertEquals("42", placeholder.onRequest(player, "listed_items"));
        assertEquals("42", placeholder.onRequest(player, "LISTED_ITEMS"));
        assertEquals("count:blocks", placeholder.onRequest(player, "category_count_blocks"));
        assertEquals("count:", placeholder.onRequest(player, "category_count_"));
    }

    @Test
    @DisplayName("onRequest : inconnu, chaine nulle ou joueur nul rendent null")
    void onRequest_unknownOrNull_returnsNull() {
        placeholder.register("listed_items", p -> "42", "d");

        assertNull(placeholder.onRequest(player, "unknown"));
        assertNull(placeholder.onRequest(player, null));
        assertNull(placeholder.onRequest(null, "listed_items"));
        assertNull(placeholder.onRequest(player, "listed_items_extra"), "suffixe sur une Function : pas de match");
    }

    @Test
    @DisplayName("onRequest : le premier placeholder enregistre qui matche gagne")
    void onRequest_firstMatchWins() {
        placeholder.register("pending_money_", (p, value) -> "bi:" + value, "d", "<economy>");
        placeholder.register("pending_money", p -> "fn", "d");
        placeholder.register("pending_money_", (p, value) -> "shadowed", "d", "<economy>");

        assertEquals("bi:vault", placeholder.onRequest(player, "pending_money_vault"));
        assertEquals("fn", placeholder.onRequest(player, "pending_money"));
    }

    @Test
    @DisplayName("setPlaceholders : remplace avec ou sans le prefixe zauctionhouse_")
    void setPlaceholders_withAndWithoutPrefix() {
        placeholder.register("listed_items", p -> "42", "d");

        assertEquals("Items: 42", placeholder.setPlaceholders(player, "Items: %zauctionhouse_listed_items%"));
        assertEquals("Items: 42", placeholder.setPlaceholders(player, "Items: %listed_items%"));
    }

    @Test
    @DisplayName("setPlaceholders : plusieurs placeholders dans la meme chaine, y compris repetes")
    void setPlaceholders_multiple() {
        placeholder.register("listed_items", p -> "42", "d");
        placeholder.register("category_count_", (p, value) -> value.length() + "", "d", "<id>");

        String result = placeholder.setPlaceholders(player, "%zauctionhouse_listed_items%/%zauctionhouse_category_count_blocks% (%zauctionhouse_listed_items%)");
        assertEquals("42/6 (42)", result);
    }

    @Test
    @DisplayName("setPlaceholders : un placeholder inconnu est laisse intact")
    void setPlaceholders_unknown_isKept() {
        placeholder.register("listed_items", p -> "42", "d");
        assertEquals("%zauctionhouse_unknown% and %other_plugin_x%", placeholder.setPlaceholders(player, "%zauctionhouse_unknown% and %other_plugin_x%"));
    }

    @Test
    @DisplayName("setPlaceholders : null, chaine sans '%' et joueur nul sont rendus tels quels")
    void setPlaceholders_passThrough() {
        placeholder.register("listed_items", p -> "42", "d");

        assertNull(placeholder.setPlaceholders(player, (String) null));
        assertEquals("plain text", placeholder.setPlaceholders(player, "plain text"));
        assertEquals("", placeholder.setPlaceholders(player, ""));
        assertEquals("%zauctionhouse_listed_items%", placeholder.setPlaceholders(null, "%zauctionhouse_listed_items%"), "sans joueur, onRequest rend null : rien n'est remplace");
    }

    @Test
    @DisplayName("setPlaceholders : une valeur nulle rendue par le consumer laisse le placeholder intact")
    void setPlaceholders_nullValue_isKept() {
        placeholder.register("nothing", p -> null, "d");
        assertEquals("%zauctionhouse_nothing%", placeholder.setPlaceholders(player, "%zauctionhouse_nothing%"));
    }

    @Test
    @DisplayName("setPlaceholders(List) applique le remplacement ligne a ligne et rend null pour null")
    void setPlaceholders_list() {
        placeholder.register("listed_items", p -> "42", "d");

        List<String> lore = Arrays.asList("a %zauctionhouse_listed_items%", "b", null);
        assertEquals(Arrays.asList("a 42", "b", null), placeholder.setPlaceholders(player, lore));
        assertNull(placeholder.setPlaceholders(player, (List<String>) null));
        assertTrue(placeholder.setPlaceholders(player, List.of()).isEmpty());
    }

    @Test
    @DisplayName("setPlaceholders : le prefixe est retire partout dans le nom, la valeur residuelle n'est pas affectee")
    void setPlaceholders_biFunctionArgument() {
        placeholder.register("category_count_", (p, value) -> "[" + value + "]", "d", "<id>");
        assertEquals("[enchanted-books]", placeholder.setPlaceholders(player, "%zauctionhouse_category_count_enchanted-books%"));
    }
}
