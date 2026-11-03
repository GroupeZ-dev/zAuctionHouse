package fr.maxlego08.zauctionhouse.loader.buttons;

import fr.maxlego08.zauctionhouse.api.filter.DateFilter;
import fr.maxlego08.zauctionhouse.buttons.admin.TransactionDateFilterButton;
import fr.maxlego08.zauctionhouse.testutil.LoaderTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TransactionDateFilterLoaderTest extends LoaderTestBase {

    private TransactionDateFilterLoader loader;

    @BeforeEach
    void setUp() {
        this.loader = new TransactionDateFilterLoader(this.plugin);
    }

    @Test
    @DisplayName("nom du loader : ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_DATE")
    void name() {
        assertEquals("ZAUCTIONHOUSE_ADMIN_TRANSACTIONS_FILTER_DATE", loader.getName());
    }

    @Test
    @DisplayName("filtres, textes et noms personnalises lus")
    void fullConfiguration() {
        TransactionDateFilterButton button = loadInline(loader, "filter", """
                enable-text: "<green>%date%"
                disable-text: "<gray>%date%"
                filters:
                  - this_month
                  - ALL
                filter-names:
                  this_month: "Ce mois"
                """, TransactionDateFilterButton.class);

        assertEquals("<green>%date%", field(button, "enableText"));
        assertEquals("<gray>%date%", field(button, "disableText"));
        assertEquals(List.of(DateFilter.THIS_MONTH, DateFilter.ALL), field(button, "dateFilters"));
        Map<DateFilter, String> names = field(button, "filterNames");
        assertEquals("Ce mois", names.get(DateFilter.THIS_MONTH));
        assertEquals("All Time", names.get(DateFilter.ALL));
        assertEquals(DateFilter.values().length, names.size());
        assertSame(plugin, field(button, "plugin"));
    }

    @Test
    @DisplayName("valeurs par defaut : tous les filtres, noms par defaut, textes &a/&7")
    void defaults() {
        TransactionDateFilterButton button = loadInline(loader, "filter", "slot: 51", TransactionDateFilterButton.class);

        assertEquals("&a%date%", field(button, "enableText"));
        assertEquals("&7%date%", field(button, "disableText"));
        assertEquals(List.of(DateFilter.values()), field(button, "dateFilters"));
        Map<DateFilter, String> names = field(button, "filterNames");
        for (DateFilter filter : DateFilter.values()) {
            assertEquals(filter.getDisplayName(), names.get(filter));
        }
    }

    @Test
    @DisplayName("filtre inconnu ignore ; uniquement des inconnus -> tous les filtres")
    void invalidFilters() {
        assertEquals(List.of(DateFilter.TODAY), field(loadInline(loader, "filter", "filters: [today, nope]", TransactionDateFilterButton.class), "dateFilters"));
        assertEquals(List.of(DateFilter.values()), field(loadInline(loader, "filter", "filters: [nope]", TransactionDateFilterButton.class), "dateFilters"));
    }
}
