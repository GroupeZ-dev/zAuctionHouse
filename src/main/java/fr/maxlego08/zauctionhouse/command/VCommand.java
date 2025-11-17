package fr.maxlego08.zauctionhouse.command;

import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.utils.commands.Arguments;
import fr.maxlego08.zauctionhouse.utils.commands.CollectionBiConsumer;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import fr.maxlego08.zauctionhouse.utils.commands.Tab;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class VCommand extends Arguments {

    protected final ZAuctionPlugin plugin;
    private final List<String> subCommands = new ArrayList<>();
    private final List<String> requireArgs = new ArrayList<>();
    private final List<String> optionalArgs = new ArrayList<>();
    protected VCommand parent;
    protected List<VCommand> subVCommands = new ArrayList<>();
    protected boolean runAsync = false;
    protected CommandSender sender;
    protected Player player;
    protected Map<Integer, CollectionBiConsumer> tabCompletions = new HashMap<>();
    private String permission;
    private boolean consoleCanUse = true;
    private boolean ignoreParent = false;
    private boolean ignoreArgs = false;
    private boolean extendedArgs = false;
    private CommandType tabCompleter = CommandType.DEFAULT;
    private String syntax;
    private String description;
    private int argsMinLength;
    private int argsMaxLength;

    public VCommand(AuctionPlugin plugin) {
        super();
        this.plugin = (ZAuctionPlugin) plugin;
    }

    public Optional<CollectionBiConsumer> getCompletionAt(int index) {
        return Optional.ofNullable(this.tabCompletions.getOrDefault(index, null));
    }

    public String getPermission() {
        return permission;
    }

    protected VCommand setPermission(String permission) {
        this.permission = permission;
        return this;
    }

    protected VCommand setPermission(Permission permission) {
        this.permission = permission.asPermission();
        return this;
    }

    /**
     * Gets the parent command.
     *
     * @return the parent command.
     */
    public VCommand getParent() {
        return parent;
    }

    public VCommand getMainParent() {
        if (parent == null) return null;
        if (parent.getParent() == null) return parent;
        else return parent.getMainParent();
    }

    /**
     * Sets the parent command.
     *
     * @param parent the parent command to set.
     * @return this VCommand instance.
     */
    protected VCommand setParent(VCommand parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Gets the list of sub-commands.
     *
     * @return the list of sub-commands.
     */
    public List<String> getSubCommands() {
        return subCommands;
    }

    /**
     * Checks if the console can use this command.
     *
     * @return true if the console can use this command, false otherwise.
     */
    public boolean isConsoleCanUse() {
        return consoleCanUse;
    }

    /**
     * Sets whether the console can use this command.
     *
     * @param consoleCanUse true if the console can use this command, false otherwise.
     * @return this VCommand instance.
     */
    protected VCommand setConsoleCanUse(boolean consoleCanUse) {
        this.consoleCanUse = consoleCanUse;
        return this;
    }

    /**
     * Checks if the command should ignore the parent command.
     *
     * @return true if the command should ignore the parent command, false otherwise.
     */
    public boolean isIgnoreParent() {
        return ignoreParent;
    }

    /**
     * Sets whether the command should ignore the parent command.
     *
     * @param ignoreParent true to ignore the parent command, false otherwise.
     */
    public void setIgnoreParent(boolean ignoreParent) {
        this.ignoreParent = ignoreParent;
    }

    /**
     * Gets the sender of the command.
     *
     * @return the sender.
     */
    public CommandSender getSender() {
        return sender;
    }

    /**
     * Gets the minimum number of arguments required for this command.
     *
     * @return the minimum number of arguments.
     */
    public int getArgsMinLength() {
        return argsMinLength;
    }

    /**
     * Gets the maximum number of arguments allowed for this command.
     *
     * @return the maximum number of arguments.
     */
    public int getArgsMaxLength() {
        return argsMaxLength;
    }

    /**
     * Gets the player who sent the command.
     *
     * @return the player.
     */
    public Player getPlayer() {
        return player;
    }

    //
    // SETTER
    //

    /**
     * Gets the syntax for this command.
     *
     * @return the syntax.
     */
    public String getSyntax() {
        if (syntax == null) {
            syntax = generateDefaultSyntax("");
        }
        return syntax;
    }

    /**
     * Sets the syntax for this command.
     *
     * @param syntax the syntax to set.
     * @return this VCommand instance.
     */
    protected VCommand setSyntax(String syntax) {
        this.syntax = syntax;
        return this;
    }

    /**
     * Checks if the command should ignore arguments.
     *
     * @return true if the command should ignore arguments, false otherwise.
     */
    public boolean isIgnoreArgs() {
        return ignoreArgs;
    }

    /**
     * Sets whether the command should ignore arguments.
     *
     * @param ignoreArgs true to ignore arguments, false otherwise.
     */
    public void setIgnoreArgs(boolean ignoreArgs) {
        this.ignoreArgs = ignoreArgs;
    }

    /**
     * Gets the description of this command.
     *
     * @return the description.
     */
    public String getDescription() {
        return description == null ? "no description" : description;
    }

    /**
     * Sets the description of this command.
     *
     * @param description the description to set.
     * @return this VCommand instance.
     */
    protected VCommand setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the description of this command using a {@link Message} enum.
     *
     * @param description the description to set.
     * @return this VCommand instance.
     */
    protected VCommand setDescription(Message description) {
        this.description = description.getMessageAsString();
        return this;
    }

    /**
     * Gets the tab completer type for this command.
     *
     * @return the tab completer type.
     */
    public CommandType getTabCompleter() {
        return tabCompleter;
    }

    /*
     *
     */
    protected void setTabCompleter() {
        this.tabCompleter = CommandType.SUCCESS;
    }

    /**
     * Sets whether the command should allow extended arguments.
     *
     * @param extendedArgs true to allow extended arguments, false otherwise.
     */
    public void setExtendedArgs(boolean extendedArgs) {
        this.extendedArgs = extendedArgs;
    }

    /**
     * Sets the command to be usable only by players.
     *
     * @return this VCommand instance.
     */
    protected VCommand onlyPlayers() {
        this.consoleCanUse = false;
        return this;
    }

    /*
     * Adds a required argument.
     */
    protected void addRequireArg(String message) {
        this.requireArgs.add(message);
        this.ignoreParent = this.parent == null;
        this.ignoreArgs = true;
    }

    /*
     * Adds a required argument with a tab completion.
     */
    protected void addRequireArg(String message, CollectionBiConsumer runnable) {
        this.addRequireArg(message);
        int index = this.requireArgs.size();
        this.addCompletion(index - 1, runnable);
    }

    /**
     * Adds an optional argument with a tab completion.
     *
     * @param message  the optional argument message.
     * @param runnable the tab completion runnable.
     */
    protected void addOptionalArg(String message, CollectionBiConsumer runnable) {
        this.addOptionalArg(message);
        int index = this.requireArgs.size() + this.optionalArgs.size();
        this.addCompletion(index - 1, runnable);
    }

    /**
     * Adds an optional argument.
     *
     * @param message the optional argument message.
     */
    protected void addOptionalArg(String message) {
        this.optionalArgs.add(message);
        this.ignoreParent = this.parent == null;
        this.ignoreArgs = true;
    }

    /**
     * Gets the first sub-command.
     *
     * @return the first sub-command.
     */
    public String getFirst() {
        return this.subCommands.getFirst();
    }

    //
    // OTHER
    //

    /**
     * Adds a sub-command.
     *
     * @param subCommand the sub-command to add.
     * @return this VCommand instance.
     */
    public VCommand addSubCommand(String subCommand) {
        this.subCommands.add(subCommand);
        return this;
    }

    /**
     * Adds a sub-command.
     *
     * @param command the sub-command to add.
     * @return this VCommand instance.
     */
    public VCommand addSubCommand(VCommand command) {
        command.setParent(this);
        this.plugin.getCommandManager().registerCommand(command);
        this.subVCommands.add(command);
        return this;
    }

    /**
     * Adds multiple sub-commands.
     *
     * @param subCommand the sub-commands to add.
     * @return this VCommand instance.
     */
    public VCommand addSubCommand(String... subCommand) {
        this.subCommands.addAll(Arrays.asList(subCommand));
        return this;
    }

    /**
     * Adds a {@link CollectionBiConsumer} to the index for tab completion.
     *
     * @param index    the index.
     * @param runnable the tab completion runnable.
     */
    public void addCompletion(int index, CollectionBiConsumer runnable) {
        this.tabCompletions.put(index, runnable);
        this.setTabCompleter();
    }

    /**
     * Generates the default syntax for the command.
     *
     * @param syntax the initial syntax.
     * @return the generated syntax.
     */
    private String generateDefaultSyntax(String syntax) {

        String tmpString = subCommands.get(0);

        boolean update = syntax.equals("");

        if (requireArgs.size() != 0 && update) {
            for (String requireArg : requireArgs) {
                requireArg = "<" + requireArg + ">";
                syntax += " " + requireArg;
            }
        }
        if (optionalArgs.size() != 0 && update) {
            StringBuilder syntaxBuilder = new StringBuilder(syntax);
            for (String optionalArg : optionalArgs) {
                optionalArg = "[<" + optionalArg + ">]";
                syntaxBuilder.append(" ").append(optionalArg);
            }
            syntax = syntaxBuilder.toString();
        }

        tmpString += syntax;

        if (parent == null) {
            return "/" + tmpString;
        }

        return parent.generateDefaultSyntax(" " + tmpString);
    }

    /**
     * Recursively counts the number of parent commands.
     *
     * @param defaultParent the initial parent count.
     * @return the total number of parent commands.
     */
    private int parentCount(int defaultParent) {
        return parent == null ? defaultParent : parent.parentCount(defaultParent + 1);
    }

    /**
     * Pre-processes the command, checking arguments and permissions.
     *
     * @param plugin        the plugin instance.
     * @param commandSender the sender of the command.
     * @param args          the arguments of the command.
     * @return the command type.
     */
    public CommandType prePerform(AuctionPlugin plugin, CommandSender commandSender, String[] args) {

        // Update the number of arguments according to the number of parents
        this.parentCount = this.parentCount(0);
        this.argsMaxLength = this.requireArgs.size() + this.optionalArgs.size() + this.parentCount;
        this.argsMinLength = this.requireArgs.size() + this.parentCount;

        // Generate the basic syntax if it is not already set
        if (this.syntax == null) {
            this.syntax = generateDefaultSyntax("");
        }

        this.args = args;

        String defaultString = super.argAsString(0);

        if (defaultString != null) {
            for (VCommand subCommand : subVCommands) {
                if (subCommand.getSubCommands().contains(defaultString.toLowerCase())) return CommandType.CONTINUE;
            }
        }

        if ((this.argsMinLength != 0 && args.length < this.argsMinLength) || (this.argsMaxLength != 0 && args.length > this.argsMaxLength && !this.extendedArgs)) {
            return CommandType.SYNTAX_ERROR;
        }

        this.sender = commandSender;
        if (this.sender instanceof Player) {
            this.player = (Player) commandSender;
        }

        try {
            return perform(plugin);
        } catch (Exception exception) {
            exception.printStackTrace();
            return CommandType.SYNTAX_ERROR;
        }
    }

    /**
     * Executes the command.
     *
     * @param plugin the plugin instance.
     * @return the command type.
     */
    protected abstract CommandType perform(AuctionPlugin plugin);

    /**
     * Checks if there are any sub-commands with the same name as this command.
     *
     * @return true if there are sub-commands with the same name, false otherwise.
     */
    public boolean sameSubCommands() {
        if (this.parent == null) {
            return false;
        }
        for (String command : this.subCommands) {
            if (this.parent.getSubCommands().contains(command)) return true;
        }
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "VCommand [permission=" + permission + ", subCommands=" + subCommands + ", consoleCanUse=" + consoleCanUse + ", description=" + description + "]";
    }

    /**
     * Generates tab completions for the command.
     *
     * @param plugin the plugin instance.
     * @param sender the sender of the command.
     * @param args   the arguments of the command.
     * @return the list of tab completions.
     */
    public List<String> toTab(AuctionPlugin plugin, CommandSender sender, String[] args) {

        this.parentCount = this.parentCount(0);

        int currentIndex = (args.length - this.parentCount) - 1;
        Optional<CollectionBiConsumer> optional = this.getCompletionAt(currentIndex);

        if (optional.isPresent()) {
            CollectionBiConsumer collectionRunnable = optional.get();
            String startWith = args[args.length - 1];
            return this.generateList(collectionRunnable.accept(sender, args), startWith);
        }

        return null;
    }

    /**
     * Generates a list of strings for tab completion.
     *
     * @param startWith the starting string.
     * @param strings   the strings to match.
     * @return the list of matching strings.
     */
    protected List<String> generateList(String startWith, String... strings) {
        return generateList(Arrays.asList(strings), startWith);
    }

    /**
     * Generates a list of strings for tab completion.
     *
     * @param tab       the tab completion type.
     * @param startWith the starting string.
     * @param strings   the strings to match.
     * @return the list of matching strings.
     */
    protected List<String> generateList(Tab tab, String startWith, String... strings) {
        return generateList(Arrays.asList(strings), startWith, tab);
    }

    /**
     * Generates a list of strings for tab completion.
     *
     * @param defaultList the default list of strings.
     * @param startWith   the starting string.
     * @return the list of matching strings.
     */
    protected List<String> generateList(List<String> defaultList, String startWith) {
        return generateList(defaultList, startWith, Tab.CONTAINS);
    }

    /**
     * Generates a list of strings for tab completion.
     *
     * @param defaultList the default list of strings.
     * @param startWith   the starting string.
     * @param tab         the tab completion type.
     * @return the list of matching strings.
     */
    protected List<String> generateList(List<String> defaultList, String startWith, Tab tab) {
        List<String> newList = new ArrayList<>();
        for (String str : defaultList) {
            if (startWith.isEmpty() || (tab.equals(Tab.START) ? str.toLowerCase().startsWith(startWith.toLowerCase()) : str.toLowerCase().contains(startWith.toLowerCase()))) {
                newList.add(str);
            }
        }
        return newList.isEmpty() ? null : newList;
    }

    /**
     * Adds a list of aliases as sub-commands.
     *
     * @param aliases the list of aliases.
     */
    public void addSubCommand(List<String> aliases) {
        this.subCommands.addAll(aliases);
    }

    /**
     * Sends the syntax message of the commands to the sender.
     */
    public void syntaxMessage(CommandSender sender) {
        this.subVCommands.forEach(command -> {
            if (command.getPermission() == null || hasPermission(sender, command.getPermission())) {
                message(this.plugin, sender, Message.COMMAND_SYNTAX_HELP, "%syntax%", command.getSyntax(), "%description%", command.getDescription());
            }
        });
    }

}
