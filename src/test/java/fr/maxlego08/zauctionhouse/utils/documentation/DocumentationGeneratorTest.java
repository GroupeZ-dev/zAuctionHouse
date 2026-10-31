package fr.maxlego08.zauctionhouse.utils.documentation;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.placeholder.AutoPlaceholder;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class DocumentationGeneratorTest {

    @TempDir
    File dataFolder;

    private AuctionPlugin plugin;
    private DocumentationGenerator generator;

    @BeforeEach
    void setUp() {
        this.plugin = MockPlugins.plugin(dataFolder);
        this.generator = new DocumentationGenerator(plugin);
    }

    private static VCommand command(VCommand parent, String syntax, List<String> subCommands, String permission, String description) {
        VCommand command = mock(VCommand.class);
        lenient().when(command.getParent()).thenReturn(parent);
        lenient().when(command.getMainParent()).thenReturn(parent);
        lenient().when(command.getFirst()).thenReturn(subCommands.get(0));
        lenient().when(command.getSubCommands()).thenReturn(subCommands);
        lenient().when(command.getSyntax()).thenReturn(syntax);
        lenient().when(command.getPermission()).thenReturn(permission);
        lenient().when(command.getDescription()).thenReturn(description);
        return command;
    }

    private List<String> read(String name) throws Exception {
        return Files.readAllLines(new File(new File(dataFolder, "docs"), name).toPath(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("generate cree le dossier docs avec commands.md et placeholders.md")
    void generate_createsBothFiles() throws Exception {
        generator.generate(new ArrayList<>(), new ArrayList<>());

        File docs = new File(dataFolder, "docs");
        assertTrue(docs.isDirectory());
        assertTrue(new File(docs, "commands.md").isFile());
        assertTrue(new File(docs, "placeholders.md").isFile());

        List<String> commands = read("commands.md");
        assertEquals("| Command | Aliases | Permission | Description |", commands.get(0));
        assertEquals("|---------|---------|------------|-------------|", commands.get(1));
        assertEquals(2, commands.size(), "aucune ligne de donnees");

        List<String> placeholders = read("placeholders.md");
        assertEquals("| Placeholder | Description |", placeholders.get(0));
        assertEquals(2, placeholders.size());
    }

    @Test
    @DisplayName("commands.md : commandes principales triees, sous-commandes sous leur parent, alias sans le premier, pipes echappes")
    void generate_commandsTable() throws Exception {
        VCommand zah = command(null, "/zah", List.of("zah"), "zauctionhouse.admin", "Admin | root");
        VCommand ah = command(null, "/ah", List.of("ah", "hdv", "auction"), "zauctionhouse.use", "Open the auction house");
        VCommand sell = command(ah, "/ah sell <price>", List.of("sell", "s"), null, null);
        VCommand claim = command(ah, "/ah claim", List.of("claim", "c"), "zauctionhouse.claim", "Claim money");

        generator.generate(new ArrayList<>(List.of(zah, sell, ah, claim)), new ArrayList<>());

        List<String> lines = read("commands.md");
        assertEquals(6, lines.size());
        assertEquals("| `/ah` | /hdv, /auction | zauctionhouse.use | Open the auction house |", lines.get(2));
        assertEquals("| `/ah claim` | /c | zauctionhouse.claim | Claim money |", lines.get(3), "sous-commandes triees par premier alias");
        assertEquals("| `/ah sell <price>` | /s |  |  |", lines.get(4), "permission et description nulles rendues vides");
        assertEquals("| `/zah` |  | zauctionhouse.admin | Admin \\| root |", lines.get(5));
    }

    @Test
    @DisplayName("placeholders.md : tries par prefixe, arguments entre chevrons, pipes echappes")
    void generatePlaceholders_table() throws Exception {
        Function<Player, String> noop = player -> "";
        List<AutoPlaceholder> placeholders = new ArrayList<>(List.of(
                new AutoPlaceholder("selling_count", noop, "Number of items | selling"),
                new AutoPlaceholder("category_count_", (player, value) -> "", "Items in a category", List.of("category")),
                new AutoPlaceholder("expired_count", noop, "Expired items")
        ));

        File docs = new File(dataFolder, "docs");
        assertTrue(docs.mkdirs());
        generator.generatePlaceholders(docs, placeholders);

        List<String> lines = read("placeholders.md");
        assertEquals(5, lines.size());
        assertEquals("| `%zauctionhouse_category_count_<category>%` | Items in a category |", lines.get(2));
        assertEquals("| `%zauctionhouse_expired_count%` | Expired items |", lines.get(3));
        assertEquals("| `%zauctionhouse_selling_count%` | Number of items \\| selling |", lines.get(4));
        assertEquals("category_count_", placeholders.get(0).getStartWith(), "la liste passee est triee en place");
    }

    @Test
    @DisplayName("un placeholder a plusieurs arguments les joint par des underscores")
    void generatePlaceholders_multipleArgs() throws Exception {
        List<AutoPlaceholder> placeholders = new ArrayList<>(List.of(
                new AutoPlaceholder("player_", (player, value) -> "", "Per player", List.of("player", "type"))
        ));
        File docs = new File(dataFolder, "docs");
        assertTrue(docs.mkdirs());

        generator.generatePlaceholders(docs, placeholders);

        assertEquals("| `%zauctionhouse_player_<player>_<type>%` | Per player |", read("placeholders.md").get(2));
    }

    @Test
    @DisplayName("generate ecrase les fichiers d'une generation precedente")
    void generate_overwritesPreviousFiles() throws Exception {
        generator.generate(new ArrayList<>(List.of(command(null, "/old", List.of("old"), "p", "d"))), new ArrayList<>());
        assertTrue(String.join("\n", read("commands.md")).contains("/old"));

        generator.generate(new ArrayList<>(List.of(command(null, "/new", List.of("new"), "p", "d"))), new ArrayList<>());

        String content = String.join("\n", read("commands.md"));
        assertTrue(content.contains("/new"));
        assertFalse(content.contains("/old"));
    }
}
