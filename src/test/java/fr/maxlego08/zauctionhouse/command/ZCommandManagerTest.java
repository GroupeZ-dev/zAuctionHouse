package fr.maxlego08.zauctionhouse.command;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.RecordingCommand;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.records.CooldownConfiguration;
import fr.maxlego08.zauctionhouse.testutil.MockPlugins;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
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
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Routage, permissions, cooldowns et completion de {@link ZCommandManager}, sans serveur Bukkit :
 * le bloc statique (CommandMap par reflexion) echoue silencieusement et n'est pas necessaire ici.
 * <p>
 * Les messages partent par {@code ComponentMessage.sendMessage(sender, Component)} : on verifie
 * leur envoi avec {@code sender.sendMessage(any(Component.class))}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZCommandManagerTest {

    static final String BYPASS = "zauctionhouse.cooldown.command.bypass";

    AuctionPlugin plugin;
    Configuration configuration;
    ZCommandManager manager;
    CooldownConfiguration cooldown;
    Command bukkitCommand;
    Player player;
    CommandSender console;

    @BeforeEach
    void setUp() {
        AuctionManager auctionManager = mock(AuctionManager.class);
        configuration = mock(Configuration.class);
        plugin = mock(AuctionPlugin.class);
        when(plugin.getAuctionManager()).thenReturn(auctionManager);
        when(plugin.getConfiguration()).thenReturn(configuration);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ZCommandManagerTest"));
        when(plugin.getScheduler()).thenReturn(MockPlugins.immediateScheduler());
        cooldown(0, Map.of());

        manager = new ZCommandManager(plugin);
        when(plugin.getCommandManager()).thenReturn(manager);

        bukkitCommand = mock(Command.class);
        when(bukkitCommand.getName()).thenReturn("ah");

        player = MockPlugins.player("Alice");
        console = mock(CommandSender.class);
    }

    void cooldown(long defaultMs, Map<String, Long> perCommand) {
        cooldown = new CooldownConfiguration(defaultMs, BYPASS, perCommand);
        when(configuration.getCooldown()).thenReturn(cooldown);
    }

    RecordingCommand command(String... names) {
        return new RecordingCommand(plugin, names);
    }

    /**
     * Reproduit l'ordre de production : les enfants sont enregistres par addSubCommand pendant la
     * construction de la racine, la racine est ajoutee ensuite par registerCommand(Plugin, ...).
     */
    RecordingCommand registerRoot(RecordingCommand root) {
        manager.registerCommand(root);
        return root;
    }

    boolean run(CommandSender sender, String... args) {
        return manager.onCommand(sender, bukkitCommand, "ah", args);
    }

    List<String> tab(CommandSender sender, String... args) {
        return manager.onTabComplete(sender, bukkitCommand, "ah", args);
    }

    // ================================================================== enregistrement

    @Test
    @DisplayName("registerCommand ajoute la commande et la rend ; getCommands expose la liste")
    void registerCommand_addsAndReturns() {
        RecordingCommand root = command("ah");
        assertSame(root, manager.registerCommand(root));
        assertEquals(List.of(root), manager.getCommands());
    }

    @Test
    @DisplayName("addSubCommand sur une racine enregistre l'enfant dans ce manager")
    void addSubCommand_registersChildHere() {
        RecordingCommand root = command("ah");
        RecordingCommand sell = command("sell");
        root.child(sell);
        registerRoot(root);
        assertEquals(List.of(sell, root), manager.getCommands(), "l'enfant est enregistre avant la racine");
    }

    @Test
    @DisplayName("registerCommand(Plugin, ...) sans serveur Bukkit ne leve pas (echec journalise)")
    void registerBukkitCommand_withoutServer_doesNotThrow() {
        RecordingCommand root = command();
        assertDoesNotThrow(() -> manager.registerCommand(plugin, "ah", root, List.of("hdv")));
    }

    // ================================================================== routage

    @Nested
    @DisplayName("onCommand : routage")
    class Routing {

        @Test
        @DisplayName("la racine sans argument s'execute et onCommand rend true")
        void root_noArgs_performs() {
            RecordingCommand root = registerRoot(command("ah"));
            assertTrue(run(player));
            assertEquals(1, root.performCount);
            assertSame(player, root.lastPlayer);
            verify(player, never()).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("la racine n'est pas executee avec des arguments inconnus : COMMAND_NO_ARG est envoye")
        void root_unknownArgs_sendsNoArgMessage() {
            RecordingCommand root = registerRoot(command("ah"));
            assertTrue(run(player, "foo"));
            assertEquals(0, root.performCount);
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("une racine avec arguments declares (ignoreParent) recoit ses arguments")
        void root_withDeclaredArgs_receivesThem() {
            RecordingCommand root = registerRoot(command("ah").requireArg("page"));
            assertTrue(run(player, "3"));
            assertEquals(List.of("3"), root.lastArguments);
        }

        @Test
        @DisplayName("le premier argument route vers l'enfant, insensible a la casse, avec ses arguments")
        void child_isRoutedWithArgs() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell", "s").requireArg("price").optionalArg("amount");
            RecordingCommand claim = command("claim");
            root.child(sell).child(claim);
            registerRoot(root);

            assertTrue(run(player, "SELL", "100", "2"));
            assertEquals(1, sell.performCount);
            assertEquals(List.of("100", "2"), sell.lastArguments);
            assertEquals(0, root.performCount);
            assertEquals(0, claim.performCount);

            assertTrue(run(player, "s", "50"));
            assertEquals(2, sell.performCount);
            assertEquals(List.of("50"), sell.lastArguments);

            assertTrue(run(player, "claim"));
            assertEquals(1, claim.performCount);
        }

        @Test
        @DisplayName("un enfant sans argument declare refuse les arguments en trop : COMMAND_NO_ARG")
        void child_withoutArgs_rejectsExtraArgs() {
            RecordingCommand root = command("ah");
            RecordingCommand claim = command("claim");
            root.child(claim);
            registerRoot(root);

            assertTrue(run(player, "claim", "extra"));
            assertEquals(0, claim.performCount);
            assertEquals(0, root.performCount);
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("routage sur trois niveaux : /ah admin reload")
        void nested_threeLevels() {
            RecordingCommand root = command("ah");
            RecordingCommand admin = command("admin");
            RecordingCommand reload = command("reload");
            RecordingCommand open = command("open").requireArg("player");
            admin.child(reload).child(open);
            root.child(admin);
            registerRoot(root);

            assertTrue(run(player, "admin", "reload"));
            assertEquals(1, reload.performCount);
            assertEquals(0, admin.performCount);

            assertTrue(run(player, "admin"));
            assertEquals(1, admin.performCount);

            assertTrue(run(player, "admin", "open", "Bob"));
            assertEquals(List.of("Bob"), open.lastArguments);

            assertTrue(run(player, "admin", "unknown"));
            assertEquals(1, admin.performCount, "argument inconnu : ni admin ni ses enfants");
            assertEquals(1, reload.performCount);
        }

        @Test
        @DisplayName("un alias partage est capture par la premiere commande enregistree")
        void sharedAlias_firstRegisteredWins() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell", "s").requireArg("price");
            RecordingCommand search = command("search", "s").requireArg("query");
            root.child(sell).child(search);
            registerRoot(root);

            assertTrue(run(player, "s", "diamond"));
            assertEquals(1, sell.performCount, "sell est enregistree avant search");
            assertEquals(0, search.performCount, "search n'est jamais atteignable via 's'");
        }

        @Test
        @DisplayName("un SYNTAX_ERROR de prePerform envoie la syntaxe et consomme la commande")
        void syntaxError_sendsSyntaxMessage() {
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell").requireArg("price");
            root.child(sell);
            registerRoot(root);

            assertTrue(run(player, "sell"));
            assertEquals(0, sell.performCount);
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("un CONTINUE rendu par la commande laisse la main aux suivantes puis COMMAND_NO_ARG")
        void continueResult_fallsThrough() {
            RecordingCommand root = registerRoot(command("ah").result(CommandType.CONTINUE));
            assertTrue(run(console));
            assertEquals(1, root.performCount);
            verify(console).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("une commande asynchrone s'execute via le scheduler et onCommand rend true")
        void asyncCommand_runsThroughScheduler() {
            RecordingCommand root = registerRoot(command("ah").async());
            assertTrue(run(player));
            assertEquals(1, root.performCount);
            verify(plugin.getScheduler()).runAsync(any(java.util.function.Consumer.class));
        }
    }

    // ================================================================== prerequis

    @Nested
    @DisplayName("onCommand : console et permissions")
    class Requirements {

        @Test
        @DisplayName("la console est refusee sur une commande onlyPlayers")
        void console_rejectedOnPlayersOnly() {
            RecordingCommand root = registerRoot(command("ah").playersOnly());
            assertTrue(run(console));
            assertEquals(0, root.performCount);
            verify(console).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("la console est acceptee par defaut et n'a pas de cooldown")
        void console_acceptedByDefault_noCooldown() {
            cooldown(60_000, Map.of());
            RecordingCommand root = registerRoot(command("ah"));
            assertTrue(run(console));
            assertTrue(run(console));
            assertEquals(2, root.performCount);
            assertNull(root.lastPlayer);
        }

        @Test
        @DisplayName("sans permission la commande n'est pas executee et COMMAND_NO_PERMISSION est envoye")
        void permission_denied() {
            RecordingCommand root = registerRoot(command("ah").permission("zauctionhouse.use"));
            when(player.hasPermission("zauctionhouse.use")).thenReturn(false);
            assertTrue(run(player));
            assertEquals(0, root.performCount);
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("avec la permission la commande s'execute")
        void permission_granted() {
            RecordingCommand root = registerRoot(command("ah").permission("zauctionhouse.use"));
            when(player.hasPermission("zauctionhouse.use")).thenReturn(true);
            assertTrue(run(player));
            assertEquals(1, root.performCount);
        }

        @Test
        @DisplayName("la permission d'un enfant est verifiee independamment de celle de la racine")
        void permission_childChecked() {
            RecordingCommand root = command("ah");
            RecordingCommand admin = command("admin").permission("zauctionhouse.admin");
            root.child(admin);
            registerRoot(root);
            when(player.hasPermission("zauctionhouse.admin")).thenReturn(false);

            assertTrue(run(player, "admin"));
            assertEquals(0, admin.performCount);
            assertEquals(0, root.performCount);
        }
    }

    // ================================================================== cooldowns

    @Nested
    @DisplayName("Cooldowns")
    class Cooldowns {

        @Test
        @DisplayName("sans cooldown configure (0) les executions s'enchainent")
        void noCooldown_allowsRepeat() {
            RecordingCommand root = registerRoot(command("ah"));
            run(player);
            run(player);
            assertEquals(2, root.performCount);
        }

        @Test
        @DisplayName("un cooldown actif bloque la seconde execution et envoie COMMAND_COOLDOWN")
        void cooldown_blocksSecondCall() {
            cooldown(60_000, Map.of());
            RecordingCommand root = registerRoot(command("ah"));
            run(player);
            run(player);
            assertEquals(1, root.performCount);
            verify(player).sendMessage(any(Component.class));
        }

        @Test
        @DisplayName("le cooldown est par joueur")
        void cooldown_isPerPlayer() {
            cooldown(60_000, Map.of());
            RecordingCommand root = registerRoot(command("ah"));
            Player bob = MockPlugins.player("Bob");
            run(player);
            run(bob);
            assertEquals(2, root.performCount);
        }

        @Test
        @DisplayName("le cooldown est par commande (premier alias) : /ah et /ah sell sont independants")
        void cooldown_isPerCommand() {
            cooldown(60_000, Map.of());
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell");
            root.child(sell);
            registerRoot(root);
            run(player);
            run(player, "sell");
            assertEquals(1, root.performCount);
            assertEquals(1, sell.performCount);
        }

        @Test
        @DisplayName("la permission de bypass ignore le cooldown")
        void cooldown_bypassPermission() {
            cooldown(60_000, Map.of());
            when(player.hasPermission(BYPASS)).thenReturn(true);
            RecordingCommand root = registerRoot(command("ah"));
            run(player);
            run(player);
            assertEquals(2, root.performCount);
        }

        @Test
        @DisplayName("clearCooldowns libere le joueur")
        void clearCooldowns_resets() {
            cooldown(60_000, Map.of());
            RecordingCommand root = registerRoot(command("ah"));
            run(player);
            manager.clearCooldowns(player.getUniqueId());
            run(player);
            assertEquals(2, root.performCount);
            manager.clearCooldowns(UUID.randomUUID()); // inconnu : sans effet
            run(player);
            assertEquals(2, root.performCount);
        }

        @Test
        @DisplayName("la surcharge par commande prime sur le defaut (defaut 0, sell 60s)")
        void cooldown_perCommandOverride() {
            cooldown(0, Map.of("sell", 60_000L));
            RecordingCommand root = command("ah");
            RecordingCommand sell = command("sell");
            root.child(sell);
            registerRoot(root);

            run(player);
            run(player);
            run(player, "sell");
            run(player, "sell");
            assertEquals(2, root.performCount, "pas de cooldown sur la racine");
            assertEquals(1, sell.performCount, "cooldown specifique sur sell");
        }

        @Test
        @DisplayName("une surcharge a 0 desactive le cooldown d'une commande malgre un defaut non nul")
        void cooldown_perCommandZero_disables() {
            cooldown(60_000, Map.of("ah", 0L));
            RecordingCommand root = registerRoot(command("ah"));
            run(player);
            run(player);
            assertEquals(2, root.performCount);
        }

        @Test
        @DisplayName("cooldown differe : non consomme par l'execution, pose par applyCooldown")
        void deferredCooldown_appliedExplicitly() {
            cooldown(60_000, Map.of());
            RecordingCommand root = registerRoot(command("ah").deferredCooldown());
            run(player);
            run(player);
            assertEquals(2, root.performCount, "la commande ne pose pas le cooldown elle-meme");

            manager.applyCooldown(player, root);
            run(player);
            assertEquals(2, root.performCount, "bloque apres applyCooldown");
        }

        @Test
        @DisplayName("applyCooldown est sans effet quand la commande n'a pas de cooldown")
        void applyCooldown_zero_noEffect() {
            RecordingCommand root = registerRoot(command("ah"));
            manager.applyCooldown(player, root);
            run(player);
            assertEquals(1, root.performCount);
        }

        @Test
        @DisplayName("une execution refusee pour permission ne pose pas de cooldown")
        void deniedExecution_doesNotStartCooldown() {
            cooldown(60_000, Map.of());
            RecordingCommand root = registerRoot(command("ah").permission("zauctionhouse.use"));
            when(player.hasPermission("zauctionhouse.use")).thenReturn(false);
            run(player);
            when(player.hasPermission("zauctionhouse.use")).thenReturn(true);
            run(player);
            assertEquals(1, root.performCount);
        }
    }

    // ================================================================== tab completion

    @Nested
    @DisplayName("onTabComplete")
    class TabCompletion {

        RecordingCommand root;
        RecordingCommand sell;
        RecordingCommand admin;
        RecordingCommand reload;

        @BeforeEach
        void tree() {
            root = command("ah");
            sell = command("sell", "s").requireArg("price", (sender, args) -> List.of("1000", "5000"));
            RecordingCommand claim = command("claim");
            admin = command("admin").permission("zauctionhouse.admin");
            reload = command("reload");
            admin.child(reload);
            root.child(sell).child(claim).child(admin);
            registerRoot(root);
        }

        @Test
        @DisplayName("au premier argument : les enfants visibles, filtres par prefixe (premier alias seulement)")
        void firstArgument_listsVisibleChildren() {
            when(player.hasPermission("zauctionhouse.admin")).thenReturn(false);
            assertEquals(List.of("sell", "claim"), tab(player, ""));
            assertEquals(List.of("sell"), tab(player, "se"));
            assertNull(tab(player, "s_"), "aucun enfant : null");
            assertNull(tab(player, "s_zz"));
        }

        @Test
        @DisplayName("un enfant protege par permission n'apparait que si le sender l'a")
        void firstArgument_respectsPermission() {
            when(player.hasPermission("zauctionhouse.admin")).thenReturn(true);
            assertEquals(List.of("sell", "claim", "admin"), tab(player, ""));
            assertEquals(List.of("admin"), tab(player, "ad"));
        }

        @Test
        @DisplayName("sur un enfant a completion : la completion de l'index courant est utilisee")
        void childWithCompletion_usesIt() {
            assertEquals(List.of("1000", "5000"), tab(player, "sell", ""));
            assertEquals(List.of("5000"), tab(player, "s", "5"));
            assertNull(tab(player, "sell", "1000", ""), "pas de completion au-dela des arguments declares");
        }

        @Test
        @DisplayName("sur un enfant sans completion : ses propres enfants sont proposes")
        void childWithoutCompletion_listsGrandChildren() {
            assertEquals(List.of("reload"), tab(player, "admin", ""));
            assertEquals(List.of("reload"), tab(player, "admin", "re"));
            assertNull(tab(player, "admin", "zz"));
            assertNull(tab(player, "claim", ""), "claim n'a ni completion ni enfant");
        }

        @Test
        @DisplayName("un chemin inconnu rend null")
        void unknownPath_returnsNull() {
            assertNull(tab(player, "unknown", ""));
            assertNull(tab(player, "unknown", "x", ""));
        }

        @Test
        @DisplayName("une autre commande Bukkit (nom different) rend null")
        void otherBukkitCommand_returnsNull() {
            Command other = mock(Command.class);
            when(other.getName()).thenReturn("other");
            assertNull(manager.onTabComplete(player, other, "other", new String[]{""}));
            assertTrue(manager.onCommand(player, other, "other", new String[0]));
            assertEquals(0, root.performCount);
        }
    }

    @Test
    @DisplayName("getCommands contient aussi les commandes imbriquees enregistrees via addSubCommand")
    void getCommands_containsNested() {
        RecordingCommand root = command("ah");
        RecordingCommand admin = command("admin");
        RecordingCommand reload = command("reload");
        admin.child(reload);
        root.child(admin);
        registerRoot(root);
        List<VCommand> commands = manager.getCommands();
        assertEquals(3, commands.size());
        assertTrue(commands.containsAll(List.of(root, admin, reload)));
    }
}
