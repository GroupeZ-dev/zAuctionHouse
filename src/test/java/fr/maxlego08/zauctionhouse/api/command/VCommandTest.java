package fr.maxlego08.zauctionhouse.api.command;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VCommandTest {

    AuctionPlugin plugin;
    AuctionManager auctionManager;
    CommandManager commandManager;
    CommandSender console;
    Player player;

    @BeforeEach
    void setUp() {
        auctionManager = mock(AuctionManager.class);
        commandManager = mock(CommandManager.class);
        plugin = mock(AuctionPlugin.class);
        when(plugin.getAuctionManager()).thenReturn(auctionManager);
        when(plugin.getCommandManager()).thenReturn(commandManager);
        console = mock(CommandSender.class);
        player = mock(Player.class);
    }

    RecordingCommand command(String... names) {
        return new RecordingCommand(plugin, names);
    }

    // ================================================================== construction & reglages

    @Nested
    @DisplayName("Construction et reglages")
    class Settings {

        @Test
        @DisplayName("le constructeur capture l'AuctionManager du plugin")
        void constructor_capturesAuctionManager() {
            RecordingCommand command = command("ah");
            assertSame(auctionManager, command.auctionManager);
            assertSame(plugin, command.plugin);
        }

        @Test
        @DisplayName("valeurs par defaut : console autorisee, pas de permission, DEFAULT tab, 'no description'")
        void defaults() {
            RecordingCommand command = command("ah");
            assertTrue(command.isConsoleCanUse());
            assertNull(command.getPermission());
            assertEquals(CommandType.DEFAULT, command.getTabCompleter());
            assertEquals("no description", command.getDescription());
            assertFalse(command.isRunAsync());
            assertFalse(command.isDeferredCooldown());
            assertFalse(command.isIgnoreArgs());
            assertFalse(command.isIgnoreParent());
            assertNull(command.getParent());
            assertNull(command.getMainParent());
            assertNull(command.getSender());
            assertNull(command.getPlayer());
        }

        @Test
        @DisplayName("setPermission(Permission) convertit l'enum en 'zauctionhouse.use'")
        void setPermission_fromEnum() {
            RecordingCommand command = command("ah");
            command.setPermission(Permission.ZAUCTIONHOUSE_USE);
            assertEquals("zauctionhouse.use", command.getPermission());
            command.setPermission("custom.perm");
            assertEquals("custom.perm", command.getPermission());
        }

        @Test
        @DisplayName("setDescription(Message) lit le texte par defaut du message")
        void setDescription_fromMessage() {
            RecordingCommand command = command("page");
            command.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_PAGE);
            assertEquals("Open auction house at a specific page", command.getDescription());
            command.description("custom");
            assertEquals("custom", command.getDescription());
        }

        @Test
        @DisplayName("onlyPlayers, setRunAsync, setDeferredCooldown, setIgnore* basculent les drapeaux")
        void flags() {
            RecordingCommand command = command("ah");
            command.playersOnly().async().deferredCooldown();
            command.setIgnoreArgs(true);
            command.setIgnoreParent(true);
            assertFalse(command.isConsoleCanUse());
            assertTrue(command.isRunAsync());
            assertTrue(command.isDeferredCooldown());
            assertTrue(command.isIgnoreArgs());
            assertTrue(command.isIgnoreParent());
            command.setConsoleCanUse(true);
            assertTrue(command.isConsoleCanUse());
        }

        @Test
        @DisplayName("toString expose permission, alias, console et description")
        void toString_containsSettings() {
            RecordingCommand command = command("ah", "hdv").permission("p.x").description("desc");
            String text = command.toString();
            assertTrue(text.contains("permission=p.x"), text);
            assertTrue(text.contains("[ah, hdv]"), text);
            assertTrue(text.contains("consoleCanUse=true"), text);
            assertTrue(text.contains("description=desc"), text);
        }
    }

    // ================================================================== alias / sous-commandes

    @Nested
    @DisplayName("Alias et arbre de sous-commandes")
    class SubCommands {

        @Test
        @DisplayName("addSubCommand(String), (String...) et (List) alimentent la meme liste ; getFirst rend le premier")
        void addSubCommand_strings() {
            RecordingCommand command = command();
            command.addSubCommand("ah");
            command.addSubCommand("hdv", "auction");
            command.addSubCommand(List.of("zauction"));
            assertEquals(List.of("ah", "hdv", "auction", "zauction"), command.getSubCommands());
            assertEquals("ah", command.getFirst());
        }

        @Test
        @DisplayName("getFirst sans alias leve NoSuchElementException")
        void getFirst_empty_throws() {
            assertThrows(NoSuchElementException.class, () -> command().getFirst());
        }

        @Test
        @DisplayName("addSubCommand(VCommand) fixe le parent, enregistre l'enfant dans le CommandManager et le garde")
        void addSubCommand_vcommand_registersAndLinks() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell");

            VCommand returned = root.addSubCommand(sell);

            assertSame(root, returned, "fluent");
            assertSame(root, sell.getParent());
            assertSame(root, sell.getMainParent());
            assertTrue(root.subVCommands.contains(sell));
            verify(commandManager).registerCommand(sell);
            verify(commandManager, never()).registerCommand(root);
        }

        @Test
        @DisplayName("getMainParent remonte jusqu'a la racine sur trois niveaux")
        void getMainParent_threeLevels() {
            RecordingCommand root = command("ah");
            RecordingCommand admin = command("admin");
            RecordingCommand reload = command("reload");
            root.child(admin);
            admin.child(reload);

            assertSame(admin, reload.getParent());
            assertSame(root, reload.getMainParent());
            assertSame(root, admin.getMainParent());
            assertNull(root.getMainParent());
        }

        @Test
        @DisplayName("sameSubCommands detecte un alias partage avec le parent (et jamais pour une racine)")
        void sameSubCommands() {
            RecordingCommand root = command("ah", "s");
            RecordingCommand sell = command("sell", "s");
            RecordingCommand claim = command("claim");
            root.child(sell).child(claim);

            assertFalse(root.sameSubCommands(), "une racine n'a pas de parent");
            assertTrue(sell.sameSubCommands(), "'s' est partage avec le parent");
            assertFalse(claim.sameSubCommands());
        }
    }

    // ================================================================== arguments & syntaxe

    @Nested
    @DisplayName("Arguments declares et syntaxe generee")
    class ArgumentsAndSyntax {

        @Test
        @DisplayName("addRequireArg sur une racine : ignoreArgs=true et ignoreParent=true")
        void addRequireArg_root_setsIgnoreFlags() {
            RecordingCommand command = command("ah").requireArg("price");
            assertTrue(command.isIgnoreArgs());
            assertTrue(command.isIgnoreParent(), "une racine avec arguments doit etre traitee meme si args.length > 0");
        }

        @Test
        @DisplayName("addOptionalArg sur un enfant deja rattache : ignoreArgs=true mais ignoreParent=false")
        void addOptionalArg_child_keepsParent() {
            RecordingCommand root = command("ah");
            RecordingCommand page = command("page");
            root.child(page);
            page.optionalArg("page");
            assertTrue(page.isIgnoreArgs());
            assertFalse(page.isIgnoreParent());
        }

        @Test
        @DisplayName("la syntaxe par defaut d'une racine liste <requis> puis [<optionnels>]")
        void getSyntax_root() {
            RecordingCommand command = command("ah", "hdv").requireArg("price").requireArg("amount").optionalArg("economy");
            assertEquals("/ah <price> <amount> [<economy>]", command.getSyntax());
        }

        @Test
        @DisplayName("la syntaxe d'un enfant remonte la chaine des parents")
        void getSyntax_child() {
            RecordingCommand root = command("ah");
            RecordingCommand admin = command("admin");
            RecordingCommand add = command("add").requireArg("player").requireArg("price").optionalArg("amount");
            root.child(admin);
            admin.child(add);
            assertEquals("/ah admin add <player> <price> [<amount>]", add.getSyntax());
            assertEquals("/ah admin", admin.getSyntax());
            assertEquals("/ah", root.getSyntax());
        }

        @Test
        @DisplayName("setSyntax remplace la syntaxe generee")
        void setSyntax_overrides() {
            RecordingCommand command = command("ah").requireArg("x").syntax("/ah custom");
            assertEquals("/ah custom", command.getSyntax());
        }

        @Test
        @DisplayName("la syntaxe generee est calculee une fois puis mise en cache")
        void getSyntax_isCached() {
            RecordingCommand command = command("ah");
            assertEquals("/ah", command.getSyntax());
            command.requireArg("late");
            assertEquals("/ah", command.getSyntax(), "deja generee : l'argument ajoute apres n'apparait pas");
        }

        @Test
        @DisplayName("les completions sont indexees dans l'ordre requis puis optionnels et activent le tab SUCCESS")
        void completions_indexedInDeclarationOrder() {
            CollectionBiConsumer first = (sender, args) -> List.of("1");
            CollectionBiConsumer second = (sender, args) -> List.of("2");
            CollectionBiConsumer third = (sender, args) -> List.of("3");
            RecordingCommand command = command("sell").requireArg("price", first).optionalArg("amount", second).optionalArg("economy", third);

            assertEquals(CommandType.SUCCESS, command.getTabCompleter());
            assertSame(first, command.getCompletionAt(0).orElseThrow());
            assertSame(second, command.getCompletionAt(1).orElseThrow());
            assertSame(third, command.getCompletionAt(2).orElseThrow());
            assertTrue(command.getCompletionAt(3).isEmpty());
            assertTrue(command.getCompletionAt(-1).isEmpty());
        }

        @Test
        @DisplayName("addCompletion ecrase l'index et bascule le tab en SUCCESS")
        void addCompletion_direct() {
            RecordingCommand command = command("ah");
            CollectionBiConsumer completion = (sender, args) -> List.of("x");
            command.addCompletion(0, completion);
            assertEquals(CommandType.SUCCESS, command.getTabCompleter());
            assertSame(completion, command.getCompletionAt(0).orElseThrow());
        }
    }

    // ================================================================== prePerform

    @Nested
    @DisplayName("prePerform : bornes, delegation, contexte")
    class PrePerform {

        @Test
        @DisplayName("une racine sans argument declare accepte n'importe quel nombre d'arguments")
        void prePerform_noDeclaredArgs_acceptsAnyLength() {
            RecordingCommand command = command("ah");
            assertEquals(CommandType.SUCCESS, command.prePerform(plugin, console, new String[0]));
            assertEquals(CommandType.SUCCESS, command.prePerform(plugin, console, new String[]{"a", "b", "c"}));
            assertEquals(2, command.performCount);
            assertEquals(0, command.lastArgsMinLength);
            assertEquals(0, command.lastArgsMaxLength);
        }

        @Test
        @DisplayName("trop peu d'arguments : SYNTAX_ERROR sans appeler perform")
        void prePerform_tooFewArgs_syntaxError() {
            RecordingCommand command = command("ah").requireArg("price");
            assertEquals(CommandType.SYNTAX_ERROR, command.prePerform(plugin, console, new String[0]));
            assertEquals(0, command.performCount);
        }

        @Test
        @DisplayName("trop d'arguments : SYNTAX_ERROR, sauf si extendedArgs est actif")
        void prePerform_tooManyArgs_syntaxErrorUnlessExtended() {
            RecordingCommand command = command("ah").requireArg("price").optionalArg("amount");
            assertEquals(CommandType.SYNTAX_ERROR, command.prePerform(plugin, console, new String[]{"1", "2", "3"}));
            assertEquals(0, command.performCount);

            command.setExtendedArgs(true);
            assertEquals(CommandType.SUCCESS, command.prePerform(plugin, console, new String[]{"1", "2", "3"}));
            assertEquals(List.of("1", "2", "3"), command.lastArguments);
        }

        @Test
        @DisplayName("bornes exactes : min = requis, max = requis + optionnels")
        void prePerform_boundsAreInclusive() {
            RecordingCommand command = command("ah").requireArg("price").optionalArg("amount");
            assertEquals(CommandType.SUCCESS, command.prePerform(plugin, console, new String[]{"1"}));
            assertEquals(CommandType.SUCCESS, command.prePerform(plugin, console, new String[]{"1", "2"}));
            assertEquals(1, command.lastArgsMinLength);
            assertEquals(2, command.lastArgsMaxLength);
        }

        @Test
        @DisplayName("les bornes d'un enfant incluent le nombre de parents et argAsString(0) saute l'alias")
        void prePerform_child_shiftsByParentCount() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell").requireArg("price").optionalArg("amount");
            root.child(sell);

            assertEquals(CommandType.SYNTAX_ERROR, sell.prePerform(plugin, console, new String[]{"sell"}), "prix manquant");
            assertEquals(CommandType.SUCCESS, sell.prePerform(plugin, console, new String[]{"sell", "100"}));
            assertEquals(List.of("100"), sell.lastArguments);
            assertEquals(2, sell.lastArgsMinLength);
            assertEquals(3, sell.lastArgsMaxLength);
            assertEquals(CommandType.SYNTAX_ERROR, sell.prePerform(plugin, console, new String[]{"sell", "100", "2", "extra"}));
        }

        @Test
        @DisplayName("le premier argument qui est l'alias d'un enfant rend CONTINUE sans executer la racine (insensible a la casse)")
        void prePerform_delegatesToChild_caseInsensitive() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell", "s");
            root.child(sell);

            assertEquals(CommandType.CONTINUE, root.prePerform(plugin, console, new String[]{"sell"}));
            assertEquals(CommandType.CONTINUE, root.prePerform(plugin, console, new String[]{"SELL", "100"}));
            assertEquals(CommandType.CONTINUE, root.prePerform(plugin, console, new String[]{"S"}));
            assertEquals(0, root.performCount);
            assertEquals(0, sell.performCount, "prePerform de la racine ne lance pas l'enfant : c'est le CommandManager qui le fait");
        }

        @Test
        @DisplayName("un premier argument inconnu n'est pas une delegation : la racine s'execute")
        void prePerform_unknownFirstArg_runsRoot() {
            RecordingCommand root = command("ah");
            root.child(command("sell"));
            assertEquals(CommandType.SUCCESS, root.prePerform(plugin, console, new String[]{"foo"}));
            assertEquals(1, root.performCount);
        }

        @Test
        @DisplayName("sender et player sont renseignes (player null pour la console)")
        void prePerform_setsSenderAndPlayer() {
            RecordingCommand command = command("ah");

            command.prePerform(plugin, console, new String[0]);
            assertSame(console, command.lastSender);
            assertNull(command.lastPlayer);
            assertSame(console, command.getSender());

            command.prePerform(plugin, player, new String[0]);
            assertSame(player, command.lastSender);
            assertSame(player, command.lastPlayer);
            assertSame(player, command.getPlayer());
        }

        @Test
        @DisplayName("une exception dans perform est absorbee et devient SYNTAX_ERROR")
        void prePerform_performThrows_syntaxError() {
            RecordingCommand command = command("ah").failing(new IllegalStateException("boom"));
            assertEquals(CommandType.SYNTAX_ERROR, command.prePerform(plugin, console, new String[0]));
            assertEquals(1, command.performCount);
        }

        @Test
        @DisplayName("le code retour de perform est propage tel quel")
        void prePerform_propagatesResult() {
            assertEquals(CommandType.DEFAULT, command("ah").result(CommandType.DEFAULT).prePerform(plugin, console, new String[0]));
            assertEquals(CommandType.CONTINUE, command("ah").result(CommandType.CONTINUE).prePerform(plugin, console, new String[0]));
        }

        @Test
        @DisplayName("prePerform genere la syntaxe si elle ne l'est pas encore")
        void prePerform_generatesSyntax() {
            RecordingCommand command = command("ah").requireArg("price");
            command.prePerform(plugin, console, new String[0]);
            assertEquals("/ah <price>", command.getSyntax());
        }
    }

    // ================================================================== tab completion

    @Nested
    @DisplayName("Completion : generateList et toTab")
    class TabCompletion {

        RecordingCommand command;

        @BeforeEach
        void setUp() {
            command = command("ah");
        }

        @Test
        @DisplayName("CONTAINS (defaut) filtre sur la sous-chaine, insensible a la casse")
        void generateList_contains() {
            assertEquals(List.of("vault", "playerpoints"), command.generateList(List.of("vault", "playerpoints", "coins"), "A"));
            assertEquals(List.of("coins"), command.generateList("OIN", "vault", "coins"));
        }

        @Test
        @DisplayName("START filtre sur le prefixe, insensible a la casse")
        void generateList_start() {
            assertEquals(List.of("playerpoints"), command.generateList(List.of("vault", "playerpoints", "coins"), "P", Tab.START));
            assertEquals(List.of("vault"), command.generateList(Tab.START, "VA", "vault", "playerpoints"));
            assertNull(command.generateList(Tab.START, "oin", "coins"), "'oin' n'est pas un prefixe");
        }

        @Test
        @DisplayName("un filtre vide rend toute la liste ; aucune correspondance rend null (pas une liste vide)")
        void generateList_emptyFilterAndNoMatch() {
            assertEquals(List.of("a", "b"), command.generateList(List.of("a", "b"), ""));
            assertNull(command.generateList(List.of("a", "b"), "zzz"));
            assertNull(command.generateList(List.of(), ""));
        }

        @Test
        @DisplayName("toTab appelle la completion de l'index courant et filtre en CONTAINS")
        void toTab_usesCompletionAtCurrentIndex() {
            command.requireArg("price", (sender, args) -> List.of("1000", "10000", "25000"))
                    .optionalArg("economy", (sender, args) -> List.of("vault", "coins"));

            assertEquals(List.of("1000", "10000", "25000"), command.toTab(plugin, console, new String[]{""}));
            assertEquals(List.of("25000"), command.toTab(plugin, console, new String[]{"25"}));
            assertEquals(List.of("vault"), command.toTab(plugin, console, new String[]{"1000", "VAU"}));
            assertNull(command.toTab(plugin, console, new String[]{"1000", "vault", ""}), "pas de completion a l'index 2");
        }

        @Test
        @DisplayName("toTab decale l'index du nombre de parents")
        void toTab_child_shiftsByParentCount() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell").requireArg("price", (sender, args) -> List.of("1000", "5000"));
            root.child(sell);

            assertEquals(List.of("5000"), sell.toTab(plugin, console, new String[]{"sell", "5"}));
            assertNull(sell.toTab(plugin, console, new String[]{"sell", "5000", ""}));
        }

        @Test
        @DisplayName("toTab transmet le sender et les arguments bruts a la completion")
        void toTab_passesSenderAndArgs() {
            CommandSender[] seenSender = new CommandSender[1];
            String[][] seenArgs = new String[1][];
            command.requireArg("x", (sender, args) -> {
                seenSender[0] = sender;
                seenArgs[0] = args;
                return List.of("ok");
            });

            command.toTab(plugin, player, new String[]{"o"});
            assertSame(player, seenSender[0]);
            assertEquals(1, seenArgs[0].length);
            assertEquals("o", seenArgs[0][0]);
        }

        @Test
        @DisplayName("toTab rend null quand la completion rend une liste vide")
        void toTab_emptyCompletion_returnsNull() {
            command.requireArg("x", (sender, args) -> List.of());
            assertNull(command.toTab(plugin, console, new String[]{""}));
        }
    }

    // ================================================================== syntaxMessage

    @Nested
    @DisplayName("syntaxMessage")
    class SyntaxMessage {

        @Test
        @DisplayName("envoie COMMAND_SYNTAX_HELP pour chaque enfant visible, avec syntaxe et description")
        void syntaxMessage_sendsHelpForEachVisibleChild() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell").requireArg("price").description("Sell");
            RecordingCommand admin = command("admin").permission("zauctionhouse.admin").description("Admin");
            RecordingCommand claim = command("claim").permission("zauctionhouse.claim").description("Claim");
            root.child(sell).child(admin).child(claim);
            when(player.hasPermission("zauctionhouse.admin")).thenReturn(false);
            when(player.hasPermission("zauctionhouse.claim")).thenReturn(true);

            root.syntaxMessage(player);

            verify(plugin).sendMessage(player, Message.COMMAND_SYNTAX_HELP, "%syntax%", "/ah sell <price>", "%description%", "Sell");
            verify(plugin).sendMessage(player, Message.COMMAND_SYNTAX_HELP, "%syntax%", "/ah claim", "%description%", "Claim");
            verify(plugin, never()).sendMessage(eq(player), eq(Message.COMMAND_SYNTAX_HELP), eq("%syntax%"), eq("/ah admin"), any(), any());
        }

        @Test
        @DisplayName("une commande sans enfant n'envoie rien")
        void syntaxMessage_noChildren_sendsNothing() {
            command("ah").syntaxMessage(console);
            verify(plugin, never()).sendMessage(any(), any(), any(Object[].class));
        }
    }
}
