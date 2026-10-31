package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import fr.maxlego08.zauctionhouse.testutil.ConfigTestSupport;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFilterConfigurationTest {

    private AuctionPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = ConfigTestSupport.loggingPlugin();
    }

    @Test
    @DisplayName("configuration vide : les operateurs par defaut de l'enum")
    void of_defaults() {
        SearchFilterConfiguration configuration = SearchFilterConfiguration.of(plugin, new YamlConfiguration());

        assertEquals(SearchFilterType.values().length, configuration.operators().size());
        for (SearchFilterType type : SearchFilterType.values()) {
            assertEquals(type.getDefaultOperator(), configuration.getOperator(type));
        }
        assertEquals("~", configuration.getOperator(SearchFilterType.CONTAINS));
        assertEquals("=", configuration.getOperator(SearchFilterType.EQUALS));
        assertEquals("~=", configuration.getOperator(SearchFilterType.CONTAINS_IGNORE_CASE));
        assertEquals("==", configuration.getOperator(SearchFilterType.EQUALS_IGNORE_CASE));
    }

    @Test
    @DisplayName("operateurs personnalises lus, les autres restent par defaut")
    void of_customOperators() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("search-filters.CONTAINS", "contains:");
        yaml.set("search-filters.EQUALS", "is:");

        SearchFilterConfiguration configuration = SearchFilterConfiguration.of(plugin, yaml);

        assertEquals("contains:", configuration.getOperator(SearchFilterType.CONTAINS));
        assertEquals("is:", configuration.getOperator(SearchFilterType.EQUALS));
        assertEquals("~=", configuration.getOperator(SearchFilterType.CONTAINS_IGNORE_CASE));
    }

    @Test
    @DisplayName("orderedByLength : l'operateur le plus long est teste en premier")
    void orderedByLength_longestFirst() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("search-filters.CONTAINS", "~");
        yaml.set("search-filters.EQUALS", "===");
        yaml.set("search-filters.CONTAINS_IGNORE_CASE", "~~~~");
        yaml.set("search-filters.EQUALS_IGNORE_CASE", "==");

        Map.Entry<SearchFilterType, String>[] ordered = SearchFilterConfiguration.of(plugin, yaml).orderedByLength();

        assertEquals(4, ordered.length);
        assertEquals("~~~~", ordered[0].getValue());
        assertEquals("===", ordered[1].getValue());
        assertEquals("==", ordered[2].getValue());
        assertEquals("~", ordered[3].getValue());
        for (int index = 1; index < ordered.length; index++) {
            assertTrue(ordered[index - 1].getValue().length() >= ordered[index].getValue().length());
        }
    }

    @Test
    @DisplayName("orderedByLength par defaut : '~=' et '==' avant '~' et '='")
    void orderedByLength_defaults() {
        Map.Entry<SearchFilterType, String>[] ordered = SearchFilterConfiguration.of(plugin, new YamlConfiguration()).orderedByLength();

        assertEquals(2, ordered[0].getValue().length());
        assertEquals(2, ordered[1].getValue().length());
        assertEquals(1, ordered[2].getValue().length());
        assertEquals(1, ordered[3].getValue().length());
    }
}
