package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SortConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("tri par defaut et traductions lus")
    void of_readsDefaultAndTranslations() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("sort-items.default-sort", "ASCENDING_PRICE");
        for (SortItem sortItem : SortItem.values()) {
            configuration.set("sort-items.translations." + sortItem.name(), "t-" + sortItem.name());
        }

        SortConfiguration sort = SortConfiguration.of(plugin, configuration);

        assertEquals(SortItem.ASCENDING_PRICE, sort.defaultSort());
        assertEquals(SortItem.values().length, sort.sortItems().size());
        assertEquals("t-DECREASING_DATE", sort.sortItems().get(SortItem.DECREASING_DATE));
    }

    @ParameterizedTest(name = "\"{0}\" est accepte quelle que soit la casse")
    @ValueSource(strings = {"decreasing_price", "Decreasing_Price", "DECREASING_PRICE"})
    void of_defaultSort_caseInsensitive(String value) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("sort-items.default-sort", value);

        assertEquals(SortItem.DECREASING_PRICE, SortConfiguration.of(plugin, configuration).defaultSort());
    }

    @ParameterizedTest(name = "\"{0}\" invalide => DECREASING_DATE")
    @ValueSource(strings = {"", "RANDOM", "price"})
    void of_invalidDefaultSort_fallsBack(String value) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("sort-items.default-sort", value);

        assertEquals(SortItem.DECREASING_DATE, SortConfiguration.of(plugin, configuration).defaultSort());
    }

    @Test
    @DisplayName("tri par defaut absent => DECREASING_DATE")
    void of_missingDefaultSort_fallsBack() {
        assertEquals(SortItem.DECREASING_DATE, SortConfiguration.of(plugin, new YamlConfiguration()).defaultSort());
    }

    @Test
    @DisplayName("traduction absente => nom brut de l'enum, toutes les cles presentes")
    void of_missingTranslation_usesEnumName() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("sort-items.translations.ASCENDING_DATE", "oldest first");

        SortConfiguration sort = SortConfiguration.of(plugin, configuration);

        assertEquals(SortItem.values().length, sort.sortItems().size());
        assertEquals("oldest first", sort.sortItems().get(SortItem.ASCENDING_DATE));
        assertEquals("DECREASING_PRICE", sort.sortItems().get(SortItem.DECREASING_PRICE));
    }
}
