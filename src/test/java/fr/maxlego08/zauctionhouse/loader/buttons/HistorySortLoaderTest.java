package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.api.history.HistorySortType;
import fr.maxlego08.zauctionhouse.buttons.history.HistorySortButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import fr.maxlego08.zauctionhouse.testutil.TestResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistorySortLoaderTest extends LoaderTestBase {

    private HistorySortLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new HistorySortLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_HISTORY_SORT")
    void name() {
        assertEquals("ZAUCTIONHOUSE_HISTORY_SORT", loader.getName());
    }

    @Test
    @DisplayName("history.yml livre : 6 tris dans l'ordre, noms personnalises et textes")
    void shippedFile() {
        HistorySortButton button = load(loader, TestResources.yaml("inventories/history.yml"), "sort", HistorySortButton.class);

        assertEquals(" #F27438➜ %sorting%", field(button, "enableText"));
        assertEquals(" #76CDCD➜ %sorting%", field(button, "disableText"));
        assertEquals(List.of(HistorySortType.DATE_DESC, HistorySortType.DATE_ASC, HistorySortType.PRICE_DESC,
                HistorySortType.PRICE_ASC, HistorySortType.BUYER_ASC, HistorySortType.BUYER_DESC), field(button, "sortTypes"));
        Map<HistorySortType, String> names = field(button, "sortNames");
        assertEquals("Newest First", names.get(HistorySortType.DATE_DESC));
        assertEquals("Buyer Z-A", names.get(HistorySortType.BUYER_DESC));
        assertEquals(HistorySortType.values().length, names.size());
    }

    @Test
    @DisplayName("sans 'sorts' : tous les types dans l'ordre de l'enum ; sans 'sort-names' : noms par defaut")
    void defaults() {
        HistorySortButton button = loadInline(loader, "sort", "slot: 1", HistorySortButton.class);

        assertEquals("&a%sorting%", field(button, "enableText"));
        assertEquals("&7%sorting%", field(button, "disableText"));
        assertEquals(List.of(HistorySortType.values()), field(button, "sortTypes"));
        Map<HistorySortType, String> names = field(button, "sortNames");
        for (HistorySortType type : HistorySortType.values()) {
            assertEquals(type.getDefaultDisplayName(), names.get(type), type.name());
        }
    }

    @Test
    @DisplayName("un tri inconnu est ignore, la casse est tolerante")
    void invalidSort_skipped() {
        HistorySortButton button = loadInline(loader, "sort", """
                sorts:
                  - price_asc
                  - NOPE
                  - Date_Desc
                """, HistorySortButton.class);

        assertEquals(List.of(HistorySortType.PRICE_ASC, HistorySortType.DATE_DESC), field(button, "sortTypes"));
    }

    @Test
    @DisplayName("uniquement des tris invalides -> repli sur tous les tris")
    void onlyInvalidSorts_allTypes() {
        HistorySortButton button = loadInline(loader, "sort", "sorts: [x, y]", HistorySortButton.class);

        assertEquals(List.of(HistorySortType.values()), field(button, "sortTypes"));
    }

    @Test
    @DisplayName("sort-names partiel : cles inconnues ignorees, manquantes completees par le nom par defaut")
    void partialNames() {
        HistorySortButton button = loadInline(loader, "sort", """
                sort-names:
                  date_desc: "Recent"
                  unknown_key: "x"
                """, HistorySortButton.class);

        Map<HistorySortType, String> names = field(button, "sortNames");
        assertEquals("Recent", names.get(HistorySortType.DATE_DESC));
        assertEquals("Oldest First", names.get(HistorySortType.DATE_ASC));
        assertEquals(HistorySortType.values().length, names.size());
    }
}
