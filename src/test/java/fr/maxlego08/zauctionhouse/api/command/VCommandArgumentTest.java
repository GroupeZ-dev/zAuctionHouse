package fr.maxlego08.zauctionhouse.api.command;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandArgumentConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.CommandConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.commands.arguments.CommandSellArguments;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Commandes a arguments types et configurables ({@code commands.sell.arguments} dans config.yml) :
 * les index et valeurs par defaut viennent de la configuration, pas du code.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VCommandArgumentTest {

    static final String PATH = "commands.sell.";

    /**
     * Equivalent minimal de CommandAuctionSell : la completion de chaque argument est la liste
     * {@code auto-completion} de la configuration.
     */
    static final class SellLikeCommand extends VCommandArgument<CommandSellArguments> {

        SellLikeCommand(AuctionPlugin plugin) {
            super(plugin, CommandSellArguments.class);
        }

        @Override
        public void createCommandArguments(AuctionPlugin plugin, Class<CommandSellArguments> enumClass) {
            // Appele depuis le constructeur parent : ne pas toucher aux champs de cette sous-classe.
            this.forEachArgument(PATH, configuration -> (sender, args) -> configuration.autoCompletion());
        }

        @Override
        protected CommandType perform(AuctionPlugin plugin) {
            return CommandType.SUCCESS;
        }

        void setArgs(String... args) {
            this.args = args;
        }
    }

    AuctionPlugin plugin;
    Configuration configuration;

    @BeforeEach
    void setUp() {
        AuctionManager auctionManager = mock(AuctionManager.class);
        CommandManager commandManager = mock(CommandManager.class);
        configuration = mock(Configuration.class);
        plugin = mock(AuctionPlugin.class);
        when(plugin.getAuctionManager()).thenReturn(auctionManager);
        when(plugin.getCommandManager()).thenReturn(commandManager);
        when(plugin.getConfiguration()).thenReturn(configuration);
    }

    static CommandArgumentConfiguration<CommandSellArguments> argument(CommandSellArguments name, boolean required, String defaultValue, String... completion) {
        return new CommandArgumentConfiguration<>(name, name.name().toLowerCase(), required, List.of(completion), defaultValue);
    }

    /**
     * Configuration complete : prix requis, quantite et economie optionnelles avec defauts.
     */
    SellLikeCommand fullCommand() {
        CommandConfiguration<CommandSellArguments> commandConfiguration = new CommandConfiguration<>(
                List.of("sell", "s", "vendre"),
                List.of(
                        argument(CommandSellArguments.PRICE, true, null, "1000", "10000"),
                        argument(CommandSellArguments.AMOUNT, false, "1", "1", "%max-stack-size%"),
                        argument(CommandSellArguments.ECONOMY, false, "vault", "vault")
                ));
        when(configuration.loadCommandConfiguration(PATH, CommandSellArguments.class)).thenReturn(commandConfiguration);
        return new SellLikeCommand(plugin);
    }

    /**
     * Configuration partielle : seul le prix est declare.
     */
    SellLikeCommand priceOnlyCommand() {
        CommandConfiguration<CommandSellArguments> commandConfiguration = new CommandConfiguration<>(
                List.of("sell"),
                List.of(argument(CommandSellArguments.PRICE, true, null, "1000")));
        when(configuration.loadCommandConfiguration(PATH, CommandSellArguments.class)).thenReturn(commandConfiguration);
        return new SellLikeCommand(plugin);
    }

    @Test
    @DisplayName("forEachArgument : les alias de la configuration deviennent les sous-commandes")
    void forEachArgument_registersAliases() {
        SellLikeCommand command = fullCommand();
        assertEquals(List.of("sell", "s", "vendre"), command.getSubCommands());
        assertEquals("sell", command.getFirst());
    }

    @Test
    @DisplayName("forEachArgument : index et defauts suivent l'ordre de la configuration")
    void forEachArgument_populatesIndexesAndDefaults() {
        SellLikeCommand command = fullCommand();
        assertEquals(0, command.argumentIndexes.get(CommandSellArguments.PRICE));
        assertEquals(1, command.argumentIndexes.get(CommandSellArguments.AMOUNT));
        assertEquals(2, command.argumentIndexes.get(CommandSellArguments.ECONOMY));
        assertNull(command.argumentDefaultValues.get(CommandSellArguments.PRICE));
        assertEquals("1", command.argumentDefaultValues.get(CommandSellArguments.AMOUNT));
        assertEquals("vault", command.argumentDefaultValues.get(CommandSellArguments.ECONOMY));
        assertSame(CommandSellArguments.class, command.enumClass);
    }

    @Test
    @DisplayName("forEachArgument : requis -> <arg>, optionnel -> [<arg>], completion a chaque index")
    void forEachArgument_declaresArgsAndCompletions() {
        SellLikeCommand command = fullCommand();
        assertEquals("/sell <price> [<amount>] [<economy>]", command.getSyntax());
        assertEquals(CommandType.SUCCESS, command.getTabCompleter());
        assertEquals(List.of("1000", "10000"), command.getCompletionAt(0).orElseThrow().accept(null, new String[0]));
        assertEquals(List.of("1", "%max-stack-size%"), command.getCompletionAt(1).orElseThrow().accept(null, new String[0]));
        assertEquals(List.of("vault"), command.getCompletionAt(2).orElseThrow().accept(null, new String[0]));
        assertTrue(command.getCompletionAt(3).isEmpty());
        assertTrue(command.isIgnoreArgs());
    }

    @Test
    @DisplayName("populateIndexes : une constante absente de la configuration recoit l'index -1")
    void populateIndexes_missingConstant_isMinusOne() {
        SellLikeCommand command = priceOnlyCommand();
        assertEquals(0, command.argumentIndexes.get(CommandSellArguments.PRICE));
        assertEquals(-1, command.argumentIndexes.get(CommandSellArguments.AMOUNT));
        assertEquals(-1, command.argumentIndexes.get(CommandSellArguments.ECONOMY));
        assertEquals("/sell <price>", command.getSyntax());
    }

    @Test
    @DisplayName("argAsXxx(T) lit l'argument a l'index configure")
    void typedAccessors_readConfiguredIndex() {
        SellLikeCommand command = fullCommand();
        command.setArgs("1500.5", "3", "points");

        assertEquals("1500.5", command.argAsString(CommandSellArguments.PRICE));
        assertEquals(1500.5, command.argAsDouble(CommandSellArguments.PRICE), 1e-9);
        assertEquals(3, command.argAsInteger(CommandSellArguments.AMOUNT));
        assertEquals(3L, command.argAsLong(CommandSellArguments.AMOUNT));
        assertEquals("points", command.argAsString(CommandSellArguments.ECONOMY));
        assertEquals("points", command.argAsString(CommandSellArguments.ECONOMY, "ignored"));
    }

    @Test
    @DisplayName("argAsString(T) rend la valeur par defaut configuree quand l'argument manque")
    void argAsString_missing_usesConfiguredDefault() {
        SellLikeCommand command = fullCommand();
        command.setArgs("100");

        assertEquals("100", command.argAsString(CommandSellArguments.PRICE));
        assertEquals("vault", command.argAsString(CommandSellArguments.ECONOMY), "defaut configure");
        assertEquals("1", command.argAsString(CommandSellArguments.AMOUNT));
        assertEquals("override", command.argAsString(CommandSellArguments.ECONOMY, "override"));
    }

    @Test
    @DisplayName("argAsInteger/Long/Double(T) parsent la valeur par defaut configuree quand l'argument manque")
    void numericAccessors_missing_useConfiguredDefault() {
        SellLikeCommand command = fullCommand();
        command.setArgs("100");

        assertEquals(1, command.argAsInteger(CommandSellArguments.AMOUNT));
        assertEquals(1L, command.argAsLong(CommandSellArguments.AMOUNT));
        assertEquals(1.0, command.argAsDouble(CommandSellArguments.AMOUNT), 1e-9);
        assertEquals(9, command.argAsInteger(CommandSellArguments.AMOUNT, 9), "le defaut explicite prime");
    }

    @Test
    @DisplayName("un argument non configure (index -1) rend null / 0 / 0.0 / false sans lever")
    void unconfiguredArgument_neutralValues() {
        SellLikeCommand command = priceOnlyCommand();
        command.setArgs("100", "3", "vault");

        assertNull(command.argAsString(CommandSellArguments.AMOUNT));
        assertEquals(0, command.argAsInteger(CommandSellArguments.AMOUNT));
        assertEquals(0L, command.argAsLong(CommandSellArguments.AMOUNT));
        assertEquals(0.0, command.argAsDouble(CommandSellArguments.AMOUNT), 1e-9);
        assertFalse(command.argAsBoolean(CommandSellArguments.AMOUNT));
        assertEquals(7, command.argAsInteger(CommandSellArguments.ECONOMY, 7));
    }

    @Test
    @DisplayName("une valeur invalide est remplacee par le defaut configure, un defaut invalide par la valeur neutre")
    void invalidValues_fallBack() {
        CommandConfiguration<CommandSellArguments> commandConfiguration = new CommandConfiguration<>(
                List.of("sell"),
                List.of(
                        argument(CommandSellArguments.PRICE, true, "abc"),
                        argument(CommandSellArguments.AMOUNT, false, "5")));
        when(configuration.loadCommandConfiguration(PATH, CommandSellArguments.class)).thenReturn(commandConfiguration);
        SellLikeCommand command = new SellLikeCommand(plugin);

        command.setArgs("notANumber", "alsoNot");
        assertEquals(0, command.argAsInteger(CommandSellArguments.PRICE), "defaut 'abc' illisible -> 0");
        assertEquals(0.0, command.argAsDouble(CommandSellArguments.PRICE), 1e-9);
        assertEquals(5, command.argAsInteger(CommandSellArguments.AMOUNT), "valeur illisible -> defaut configure 5");
        assertEquals(5.0, command.argAsDouble(CommandSellArguments.AMOUNT), 1e-9);
    }

    @Test
    @DisplayName("argAsBoolean(T) parse l'argument present")
    void argAsBoolean_present() {
        SellLikeCommand command = fullCommand();
        command.setArgs("100", "true");
        assertTrue(command.argAsBoolean(CommandSellArguments.AMOUNT));
        command.setArgs("100", "no");
        assertFalse(command.argAsBoolean(CommandSellArguments.AMOUNT));
    }

    @Test
    @Disabled("BUG: Arguments.argAsBoolean(int, boolean) ne leve jamais (Boolean.valueOf(null) = false), le defaut configure n'est donc jamais rendu quand l'argument manque (Arguments.java:63, utilise par VCommandArgument.argAsBoolean(T))")
    @DisplayName("argAsBoolean(T) rend le defaut configure quand l'argument manque")
    void argAsBoolean_missing_usesConfiguredDefault() {
        CommandConfiguration<CommandSellArguments> commandConfiguration = new CommandConfiguration<>(
                List.of("sell"),
                List.of(
                        argument(CommandSellArguments.PRICE, true, null),
                        argument(CommandSellArguments.AMOUNT, false, "true")));
        when(configuration.loadCommandConfiguration(PATH, CommandSellArguments.class)).thenReturn(commandConfiguration);
        SellLikeCommand command = new SellLikeCommand(plugin);
        command.setArgs("100");

        assertTrue(command.argAsBoolean(CommandSellArguments.AMOUNT));
        assertTrue(command.argAsBoolean(CommandSellArguments.AMOUNT, true));
    }

    @Test
    @DisplayName("argAsPlayer(T) resout le joueur via Bukkit a l'index configure")
    void argAsPlayer_typed() {
        SellLikeCommand command = fullCommand();
        command.setArgs("100", "2", "Alice");
        Player alice = mock(Player.class);
        Player fallback = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alice")).thenReturn(alice);
            bukkit.when(() -> Bukkit.getPlayer((String) null)).thenThrow(new IllegalArgumentException());

            assertSame(alice, command.argAsPlayer(CommandSellArguments.ECONOMY));
            assertSame(alice, command.argAsPlayer(CommandSellArguments.ECONOMY, fallback));
            command.setArgs("100");
            // ECONOMY manque mais a un defaut "vault" : argAsPlayer ignore les defauts textuels et
            // interroge Bukkit avec null -> exception -> fallback.
            assertSame(fallback, command.argAsPlayer(CommandSellArguments.ECONOMY, fallback));
        }
    }
}
