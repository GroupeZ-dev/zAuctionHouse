package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Socle des tests de chargeurs de boutons zMenu : un plugin mocke, une
 * {@link DefaultButtonValue} standard et un raccourci qui charge un bouton depuis une section
 * {@code items.<cle>} et verifie son type concret.
 * <p>
 * Les boutons du plugin n'exposent pas de getters : les champs sont lus par reflexion via
 * {@link #field(Object, String)}.
 */
public abstract class LoaderTestBase {

    @TempDir
    protected File dataFolder;

    protected AuctionPlugin plugin;
    protected DefaultButtonValue defaultButtonValue;

    @BeforeEach
    void setUpLoaderBase() {
        this.plugin = MockPlugins.plugin(this.dataFolder);
        this.defaultButtonValue = TestResources.defaultButtonValue(new File(this.dataFolder, "inventory.yml"));
    }

    /**
     * Charge le bouton {@code items.<key>} de la configuration et verifie qu'il est du type attendu.
     */
    protected <T extends Button> T load(ButtonLoader loader, YamlConfiguration configuration, String key, Class<T> expectedType) {
        Button button = loader.load(configuration, TestResources.buttonPath(key), this.defaultButtonValue);
        assertNotNull(button, "loader " + loader.getName() + " returned null for items." + key);
        return assertInstanceOf(expectedType, button);
    }

    /**
     * Charge un bouton depuis un fragment YAML inline decrivant la section {@code items.<key>}.
     */
    protected <T extends Button> T loadInline(ButtonLoader loader, String key, String sectionYaml, Class<T> expectedType) {
        return load(loader, inline(key, sectionYaml), key, expectedType);
    }

    /**
     * Construit une configuration {@code items.<key>: {...}} a partir d'un fragment YAML indente.
     */
    protected static YamlConfiguration inline(String key, String sectionYaml) {
        StringBuilder builder = new StringBuilder("items:\n  ").append(key).append(":\n");
        if (sectionYaml == null || sectionYaml.isBlank()) {
            builder.append("    type: TEST\n");
        } else {
            for (String line : sectionYaml.stripTrailing().split("\n")) {
                builder.append("    ").append(line).append('\n');
            }
        }
        return TestResources.yamlOf(builder.toString());
    }

    protected static <T> T field(Object target, String name) {
        return ReflectionTestUtils.field(target, name);
    }
}
