package fr.maxlego08.zauctionhouse.command;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager extends ZUtils implements CommandExecutor, TabCompleter {

    private static CommandMap commandMap;
    private static Constructor<? extends PluginCommand> constructor;

    static {
        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    private final AuctionPlugin plugin;
    private final List<VCommand> commands = new ArrayList<VCommand>();

    public CommandManager(AuctionPlugin template) {
        this.plugin = template;
    }

    /**
     * Valid commands
     */
    public void validCommands() {
        this.plugin.getLogger().info("Loading " + getUniqueCommand() + " commands");
        this.commandChecking();
        try {
            this.generateMarkdownFile();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Register a command
     *
     * @param command the command to register
     * @return the command
     */
    public VCommand registerCommand(VCommand command) {
        this.commands.add(command);
        return command;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        for (VCommand command : this.commands) {
            if (command.getSubCommands().contains(cmd.getName().toLowerCase())) {
                if ((args.length == 0 || command.isIgnoreParent()) && command.getParent() == null) {
                    CommandType type = processRequirements(command, sender, args);
                    if (!type.equals(CommandType.CONTINUE)) return true;
                }
            } else if (args.length >= 1 && command.getParent() != null && canExecute(args, cmd.getName().toLowerCase(), command)) {
                CommandType type = processRequirements(command, sender, args);
                if (!type.equals(CommandType.CONTINUE)) return true;
            }
        }
        message(this.plugin, sender, Message.COMMAND_NO_ARG);
        return true;
    }

    /**
     * Determines if a command can be executed based on the provided arguments and command structure.
     *
     * @param args    The command arguments.
     * @param cmd     The command name.
     * @param command The command object containing sub-commands and parent command information.
     * @return true if the command can be executed; false otherwise.
     */
    private boolean canExecute(String[] args, String cmd, VCommand command) {
        for (int index = args.length - 1; index > -1; index--) {
            if (command.getSubCommands().contains(args[index].toLowerCase())) {
                if (command.isIgnoreArgs() && (command.getParent() == null || canExecute(args, cmd, command.getParent(), index - 1)))
                    return true;
                if (index < args.length - 1) return false;
                return canExecute(args, cmd, command.getParent(), index - 1);
            }
        }
        return false;
    }

    /**
     * Recursively checks if the provided arguments match the command structure.
     *
     * @param args    The command arguments.
     * @param cmd     The command name.
     * @param command The command object containing sub-commands and parent command information.
     * @param index   The index of the argument to check.
     * @return true if the command can be executed; false otherwise.
     */
    private boolean canExecute(String[] args, String cmd, VCommand command, int index) {
        if (index < 0 && command.getSubCommands().contains(cmd.toLowerCase())) {
            return true;
        } else if (index < 0) {
            return false;
        } else if (command.getSubCommands().contains(args[index].toLowerCase())) {
            return canExecute(args, cmd, command.getParent(), index - 1);
        }
        return false;
    }

    /**
     * Allows you to process an order. First we check if the sender has the
     * permission or if the command has a permission. If yes then we execute the
     * command otherwise we send the message for the permission
     *
     * @param command - Object that contains the command
     * @param sender  - Person who executes the command
     * @param strings - Argument of the command
     * @return CommandType - Return of the command
     */
    private CommandType processRequirements(VCommand command, CommandSender sender, String[] strings) {

        if (!(sender instanceof Player) && !command.isConsoleCanUse()) {
            message(this.plugin, sender, Message.COMMAND_NO_CONSOLE);
            return CommandType.DEFAULT;
        }

        if (command.getPermission() == null || hasPermission(sender, command.getPermission())) {

            if (command.runAsync) {
                this.plugin.getScheduler().runAsync(w -> {
                    CommandType returnType = command.prePerform(this.plugin, sender, strings);
                    if (returnType == CommandType.SYNTAX_ERROR) {
                        message(this.plugin, sender, Message.COMMAND_SYNTAX_ERROR, "%syntax%", command.getSyntax());
                    }
                });
                return CommandType.DEFAULT;
            }

            CommandType returnType = command.prePerform(this.plugin, sender, strings);
            if (returnType == CommandType.SYNTAX_ERROR) {
                message(this.plugin, sender, Message.COMMAND_SYNTAX_ERROR, "%syntax%", command.getSyntax());
            }
            return returnType;
        }
        message(this.plugin, sender, Message.COMMAND_NO_PERMISSION);
        return CommandType.DEFAULT;
    }

    public List<VCommand> getCommands() {
        return this.commands;
    }

    private int getUniqueCommand() {
        return (int) this.commands.stream().filter(command -> command.getParent() == null).count();
    }

    /**
     * Allows you to check if all commands are correct If an command does not
     * have
     */
    private void commandChecking() {
        this.commands.forEach(command -> {
            if (command.sameSubCommands()) {
                this.plugin.getLogger().info(command + " command to an argument similar to its parent command !");
                Bukkit.getPluginManager().disablePlugin(this.plugin);
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String str, String[] args) {

        for (VCommand command : commands) {

            if (command.getSubCommands().contains(cmd.getName().toLowerCase())) {
                if (args.length == 1 && command.getParent() == null) {
                    return proccessTab(sender, command, args);
                }
            } else {
                String[] newArgs = Arrays.copyOf(args, args.length - 1);
                if (newArgs.length >= 1 && command.getParent() != null && canExecute(newArgs, cmd.getName().toLowerCase(), command)) {
                    return proccessTab(sender, command, args);
                }
            }
        }

        return null;
    }

    private List<String> proccessTab(CommandSender sender, VCommand command, String[] args) {

        CommandType type = command.getTabCompleter();
        if (type.equals(CommandType.DEFAULT)) {

            String startWith = args[args.length - 1];

            List<String> tabCompleter = new ArrayList<>();
            for (VCommand vCommand : this.commands) {
                if ((vCommand.getParent() != null && vCommand.getParent() == command)) {
                    String cmd = vCommand.getSubCommands().get(0);
                    if (vCommand.getPermission() == null || sender.hasPermission(vCommand.getPermission())) {
                        if (startWith.isEmpty() || cmd.startsWith(startWith)) {
                            tabCompleter.add(cmd);
                        }
                    }
                }
            }
            return tabCompleter.isEmpty() ? null : tabCompleter;

        } else if (type.equals(CommandType.SUCCESS)) {
            return command.toTab(this.plugin, sender, args);
        }

        return null;
    }

    /**
     * Register spigot command without plugin.yml This method will allow to
     * register a command in the spigot without using the plugin.yml This saves
     * time and understanding, the plugin.yml file is clearer
     *
     * @param plugin   - Plugin
     * @param string   - Main command
     * @param vCommand - Command object
     * @param aliases  - Command aliases
     */
    public void registerCommand(Plugin plugin, String string, VCommand vCommand, List<String> aliases) {
        try {
            PluginCommand command = constructor.newInstance(string, this.plugin);
            command.setExecutor(this);
            command.setTabCompleter(this);
            command.setAliases(aliases);
            if (vCommand.getPermission() != null) {
                command.setPermission(vCommand.getPermission());
            }

            commands.add(vCommand.addSubCommand(string));
            vCommand.addSubCommand(aliases);

            if (!commandMap.register(command.getName(), plugin.getDescription().getName(), command)) {
                plugin.getLogger().info("Unable to add the command " + vCommand.getSyntax());
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Generate a Markdown file listing all the commands.
     * <p>
     * This method creates a file named {@code commands.md} in the plugin's data folder.
     * The file contains a Markdown table with the following columns:
     * <ul>
     * <li>{@code Command}: The command name.
     * <li>{@code Aliases}: The aliases of the command.
     * <li>{@code Permission}: The permission required to execute the command.
     * <li>{@code Description}: The description of the command.
     * </ul>
     *
     * @throws IOException if the file cannot be written.
     */
    public void generateMarkdownFile() throws IOException {

        List<VCommand> commands = new ArrayList<>();
        this.commands.stream().filter(e -> e.getParent() == null).sorted(Comparator.comparing(VCommand::getFirst)).forEach(command -> {
            commands.add(command);
            commands.addAll(this.commands.stream().filter(e -> e.getMainParent() == command).sorted(Comparator.comparing(VCommand::getFirst)).toList());
        });

        StringBuilder sb = new StringBuilder();
        // Markdown table header
        sb.append("| Command | Aliases | Permission | Description |\n");
        sb.append("|---------|---------|------------|-------------|\n");

        for (VCommand command : commands) {
            // Gather command data
            String cmd = command.getSyntax(); // Assuming getSyntax() gives the command
            List<String> aliasesList = new ArrayList<>(command.getSubCommands());
            if (!aliasesList.isEmpty()) {
                aliasesList.removeFirst();  // Remove the first element
            }
            String aliases = aliasesList.stream().map(alias -> "/" + alias)  // Add '/' before each alias
                    .collect(Collectors.joining(", "));
            String perm = command.getPermission(); // getPermission() for permissions
            String desc = command.getDescription(); // getDescription() for the description

            // Escape special Markdown characters in descriptions
            desc = desc == null ? "" : desc.replace("|", "\\|");
            perm = perm == null ? "" : perm;

            // Add row to the Markdown table
            sb.append(String.format("| `%s` | %s | %s | %s |\n", cmd, aliases, perm, desc));
        }

        // Write the StringBuilder content to the file
        var path = new File(plugin.getDataFolder(), "commands.md").toPath();
        Files.writeString(path, sb.toString());
    }

}
