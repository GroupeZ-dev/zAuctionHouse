package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.testutil.BukkitMocks;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import fr.maxlego08.zauctionhouse.testutil.RuleTestItems;
import fr.maxlego08.zauctionhouse.utils.tags.TagRegistry;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TagRule} passe par {@link TagRegistry}, qui lit les constantes de {@code org.bukkit.Tag}
 * (registre serveur). Sans serveur, ces constantes sont remplacees par des faux tags via
 * {@link BukkitMocks} ; l'appartenance est definie par le test. Le tag custom de production
 * "blocks" ({@code BlocksTag}) exige {@code Material.isBlock()} donc le registre serveur : il est
 * remplace par un faux tag et n'est pas teste ici.
 */
class TagRuleTest {

    @TempDir
    File tempDir;

    private AuctionPlugin plugin;

    @BeforeAll
    static void initTags() {
        BukkitMocks.ensureTagsInitialized();
        BukkitMocks.registerCustomTag("test_gems", Material.DIAMOND, Material.EMERALD);
        BukkitMocks.overrideBlocksTag(Material.STONE, Material.DIRT, Material.OAK_LOG);
        // Appartenance des tags Bukkit (par cle) : Tag.WOOL -> "wool"
        BukkitMocks.defineTag("wool", Material.WHITE_WOOL, Material.RED_WOOL);
    }

    @BeforeEach
    void setUp() {
        plugin = MockPlugins.plugin(tempDir);
    }

    @Test
    @DisplayName("matches : un tag custom enregistre en minuscules est resolu quelle que soit la casse de la config")
    void matches_customTag_caseInsensitiveName() {
        assertTrue(new TagRule(plugin, List.of("test_gems")).matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(new TagRule(plugin, List.of("TEST_GEMS")).matches(RuleTestItems.context(Material.EMERALD)));
        assertTrue(new TagRule(plugin, List.of("Test_Gems")).matches(RuleTestItems.context(Material.DIAMOND)));
    }

    @Test
    @DisplayName("matches : faux pour un materiau hors du tag")
    void matches_materialNotTagged_returnsFalse() {
        TagRule rule = new TagRule(plugin, List.of("test_gems"));

        assertFalse(rule.matches(RuleTestItems.context(Material.STONE)));
        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND_BLOCK)));
    }

    @Test
    @DisplayName("matches : le tag 'blocks' (tel qu'utilise par categories.yml) est resolu")
    void matches_blocksTag_resolved() {
        TagRule rule = new TagRule(plugin, List.of("BLOCKS"));

        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.STONE)));
        assertFalse(rule.matches(RuleTestItems.context(Material.DIAMOND)));
    }

    @Test
    @DisplayName("matches : plusieurs tags = OU logique, les inconnus sont ignores")
    void matches_multipleTags_unknownIgnored() {
        TagRule rule = new TagRule(plugin, List.of("does_not_exist", "test_gems", "blocks"));

        assertTrue(rule.isValid());
        assertTrue(rule.matches(RuleTestItems.context(Material.DIAMOND)));
        assertTrue(rule.matches(RuleTestItems.context(Material.DIRT)));
        assertFalse(rule.matches(RuleTestItems.context(Material.FEATHER)));
    }

    @Test
    @DisplayName("isValid : faux si aucun nom ne resout vers un tag (liste vide, inconnu, null)")
    void isValid_noResolvedTag_returnsFalse() {
        assertFalse(new TagRule(plugin, List.of()).isValid());
        assertFalse(new TagRule(plugin, List.of("does_not_exist")).isValid());
        assertFalse(new TagRule(plugin, Arrays.asList((String) null)).isValid());
        assertFalse(new TagRule(plugin, List.of("does_not_exist")).matches(RuleTestItems.context(Material.STONE)));
    }

    @Test
    @DisplayName("TagRegistry : les constantes Bukkit sont enregistrees sous leur NOM DE CHAMP (majuscules)")
    void tagRegistry_registersBukkitConstantsByFieldName() {
        assertNotNull(TagRegistry.getTag("WOOL"));
        assertNotNull(TagRegistry.getTag("LOGS"));
        assertNull(TagRegistry.getTag("wool"), "la cle minuscule n'existe pas dans le registre");
    }

    @Test
    @Disabled("BUG: TagRule.resolveTag met le nom en minuscules (TagRule.java:26) alors que TagRegistry enregistre les constantes Bukkit sous leur nom de champ en MAJUSCULES (TagRegistry.java:23) : 'WOOL', 'LOGS'... ne resolvent jamais, seuls les tags custom minuscules ('blocks') fonctionnent")
    @DisplayName("matches : un tag Bukkit standard (WOOL) doit etre resolu et matcher ses membres")
    void matches_bukkitTag_shouldResolve() {
        TagRule rule = new TagRule(plugin, List.of("WOOL"));

        assertTrue(rule.isValid(), "le tag WOOL existe dans org.bukkit.Tag et doit etre resolu");
        assertTrue(rule.matches(RuleTestItems.context(Material.WHITE_WOOL)));
        assertFalse(rule.matches(RuleTestItems.context(Material.STONE)));
    }
}
