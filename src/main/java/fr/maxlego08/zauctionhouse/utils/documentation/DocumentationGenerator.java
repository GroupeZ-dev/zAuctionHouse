package fr.maxlego08.zauctionhouse.utils.documentation;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.placeholder.AutoPlaceholder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentationGenerator {

    private final AuctionPlugin plugin;

    public DocumentationGenerator(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void generate(List<VCommand> auctionCommands, List<AutoPlaceholder> placeholders) {

        var folder = new File(this.plugin.getDataFolder(), "docs");
        if (!folder.exists()) folder.mkdirs();

        try {
            this.generateCommands(folder, auctionCommands);
            this.generatePlaceholders(folder, placeholders);
        } catch (Exception exception) {
            this.plugin.getLogger().severe("Error while generating documentation: " + exception.getMessage());
        }
    }

    private void generateCommands(File folder, List<VCommand> auctionCommands) throws IOException {

        List<VCommand> commands = new ArrayList<>();
        auctionCommands.stream().filter(e -> e.getParent() == null).sorted(Comparator.comparing(VCommand::getFirst)).forEach(command -> {
            commands.add(command);
            commands.addAll(auctionCommands.stream().filter(e -> e.getMainParent() == command).sorted(Comparator.comparing(VCommand::getFirst)).toList());
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

        var path = new File(folder, "commands.md").toPath();
        Files.writeString(path, sb.toString());
    }

    public void generatePlaceholders(File folder, List<AutoPlaceholder> placeholders) throws IOException {
        StringBuilder sb = new StringBuilder();
        // Markdown table header
        sb.append("| Placeholder | Description |\n");
        sb.append("|--------------|-------------|\n");

        placeholders.sort(Comparator.comparing(AutoPlaceholder::getStartWith));
        for (AutoPlaceholder placeholder : placeholders) {
            // Format placeholder with arguments
            String placeholderText = "%zauctionhouse_" + placeholder.getStartWith();
            if (!placeholder.getArgs().isEmpty()) {
                String args = placeholder.getArgs().stream().map(argument -> "<" + argument + ">").collect(Collectors.joining("_"));
                placeholderText += args;
            }
            placeholderText += "%";

            // Escape Markdown special characters in descriptions
            String desc = placeholder.getDescription().replace("|", "\\|");

            // Add row to the Markdown table
            sb.append(String.format("| `%s` | %s |\n", placeholderText, desc));
        }

        var file = new File(folder, "placeholders.md");

        // Write the StringBuilder content to the file
        Files.writeString(file.toPath(), sb.toString());
    }
}
