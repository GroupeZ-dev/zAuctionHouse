package fr.maxlego08.zauctionhouse.placeholder;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AutoPlaceholderTest {

    final Player player = mock(Player.class);

    @Test
    @DisplayName("constructeur BiFunction : garde le consumer, la description et les arguments")
    void constructor_biFunction() {
        AutoPlaceholder placeholder = new AutoPlaceholder("category_count_", (p, value) -> "n=" + value, "desc", List.of("<category_id>"));

        assertEquals("category_count_", placeholder.getStartWith());
        assertEquals("desc", placeholder.getDescription());
        assertEquals(List.of("<category_id>"), placeholder.getArgs());
        assertNotNull(placeholder.getBiConsumer());
        assertNull(placeholder.getConsumer());
    }

    @Test
    @DisplayName("constructeur Function : pas de BiFunction et une liste d'arguments vide")
    void constructor_function() {
        AutoPlaceholder placeholder = new AutoPlaceholder("listed_items", p -> "42", "desc");

        assertEquals("listed_items", placeholder.getStartWith());
        assertNull(placeholder.getBiConsumer());
        assertNotNull(placeholder.getConsumer());
        assertTrue(placeholder.getArgs().isEmpty());
    }

    @Test
    @DisplayName("accept : la Function recoit le joueur et ignore la valeur")
    void accept_function_ignoresValue() {
        Player[] seen = new Player[1];
        AutoPlaceholder placeholder = new AutoPlaceholder("listed_items", p -> {
            seen[0] = p;
            return "42";
        }, "desc");

        assertEquals("42", placeholder.accept(player, "whatever"));
        assertSame(player, seen[0]);
    }

    @Test
    @DisplayName("accept : la BiFunction recoit le joueur et la valeur residuelle")
    void accept_biFunction_receivesValue() {
        AutoPlaceholder placeholder = new AutoPlaceholder("category_count_", (p, value) -> p == player ? "ok:" + value : "ko", "desc", List.of());

        assertEquals("ok:blocks", placeholder.accept(player, "blocks"));
        assertEquals("ok:", placeholder.accept(player, ""));
    }

    @Test
    @DisplayName("accept : sans aucun consumer, un message d'erreur est rendu")
    void accept_noConsumer_returnsError() {
        AutoPlaceholder placeholder = new AutoPlaceholder("x", (java.util.function.BiFunction<Player, String, String>) null, "desc", List.of());
        assertEquals("Error with consumer !", placeholder.accept(player, "v"));
    }

    @Test
    @DisplayName("startsWith : une Function exige l'egalite exacte (insensible a la casse)")
    void startsWith_function_requiresEquality() {
        AutoPlaceholder placeholder = new AutoPlaceholder("pending_money", p -> "0", "desc");

        assertTrue(placeholder.startsWith("pending_money"));
        assertTrue(placeholder.startsWith("PENDING_MONEY"));
        assertFalse(placeholder.startsWith("pending_money_vault"), "un suffixe ne doit pas matcher une Function");
        assertFalse(placeholder.startsWith("pending"));
    }

    @Test
    @DisplayName("startsWith : une BiFunction accepte tout prefixe (sensible a la casse)")
    void startsWith_biFunction_acceptsPrefix() {
        AutoPlaceholder placeholder = new AutoPlaceholder("pending_money_", (p, value) -> value, "desc", List.of("<economy>"));

        assertTrue(placeholder.startsWith("pending_money_vault"));
        assertTrue(placeholder.startsWith("pending_money_"));
        assertFalse(placeholder.startsWith("pending_money"), "il manque le '_' final");
        assertFalse(placeholder.startsWith("PENDING_MONEY_vault"), "String.startsWith est sensible a la casse");
    }
}
