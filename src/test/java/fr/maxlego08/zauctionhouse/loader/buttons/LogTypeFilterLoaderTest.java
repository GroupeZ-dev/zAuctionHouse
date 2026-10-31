package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.api.log.LogType;
import fr.maxlego08.zauctionhouse.buttons.admin.LogTypeFilterButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogTypeFilterLoaderTest extends LoaderTestBase {

    private LogTypeFilterLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new LogTypeFilterLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_TYPE")
    void name() {
        assertEquals("ZAUCTIONHOUSE_ADMIN_LOGS_FILTER_TYPE", loader.getName());
    }

    @Test
    @DisplayName("types, textes, nom 'tous' et noms personnalises lus")
    void fullConfiguration() {
        LogTypeFilterButton button = loadInline(loader, "filter", """
                enable-text: "<green>%type%"
                disable-text: "<gray>%type%"
                all-types-name: "Tous"
                types:
                  - sale
                  - PURCHASE
                type-names:
                  sale: "Vente"
                """, LogTypeFilterButton.class);

        assertEquals("<green>%type%", field(button, "enableText"));
        assertEquals("<gray>%type%", field(button, "disableText"));
        assertEquals("Tous", field(button, "allTypesName"));
        assertEquals(List.of(LogType.SALE, LogType.PURCHASE), field(button, "logTypes"));
        Map<LogType, String> names = field(button, "typeNames");
        assertEquals("Vente", names.get(LogType.SALE));
        assertEquals("Purchase", names.get(LogType.PURCHASE));
        assertEquals("Remove Expired", names.get(LogType.REMOVE_EXPIRED));
        assertEquals(LogType.values().length, names.size());
    }

    @Test
    @DisplayName("valeurs par defaut : tous les types, 'All', textes &a/&7")
    void defaults() {
        LogTypeFilterButton button = loadInline(loader, "filter", "slot: 1", LogTypeFilterButton.class);

        assertEquals("&a%type%", field(button, "enableText"));
        assertEquals("&7%type%", field(button, "disableText"));
        assertEquals("All", field(button, "allTypesName"));
        assertEquals(List.of(LogType.values()), field(button, "logTypes"));
        Map<LogType, String> names = field(button, "typeNames");
        for (LogType type : LogType.values()) {
            assertEquals(type.getDefaultDisplayName(), names.get(type));
        }
    }

    @Test
    @DisplayName("type inconnu ignore ; uniquement des inconnus -> tous les types")
    void invalidTypes() {
        LogTypeFilterButton partial = loadInline(loader, "filter", "types: [REMOVE_LISTED, nope]", LogTypeFilterButton.class);
        assertEquals(List.of(LogType.REMOVE_LISTED), field(partial, "logTypes"));

        LogTypeFilterButton none = loadInline(loader, "filter", "types: [nope]", LogTypeFilterButton.class);
        assertEquals(List.of(LogType.values()), field(none, "logTypes"));
    }
}
