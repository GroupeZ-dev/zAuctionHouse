package fr.maxlego08.zauctionhouse.utils.yaml;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A YAML configuration updater that preserves comments when adding new keys.
 * <p>
 * This class reads YAML files line-by-line, maintaining the original structure
 * and comments while intelligently merging missing keys from a default configuration.
 * </p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Preserves all comments (both preceding and inline)</li>
 *   <li>Preserves blank lines and formatting</li>
 *   <li>Adds missing keys with their associated comments from default config</li>
 *   <li>Inserts new keys at logical positions (after parent section or similar keys)</li>
 *   <li>Supports nested sections and lists</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * YamlUpdater updater = new YamlUpdater(plugin);
 * boolean updated = updater.update("config.yml", "config.yml");
 * }</pre>
 */
public class YamlUpdater {

    private static final Pattern KEY_PATTERN = Pattern.compile("^(\\s*)([^:#\\s][^:]*?):\\s*(.*)$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(\\s*)-\\s*(.*)$");

    private final Plugin plugin;

    public YamlUpdater(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Updates the target configuration file with missing keys from the default resource.
     *
     * @param resourcePath Path to the default resource in the JAR
     * @param targetPath   Path to the target file in the plugin's data folder
     * @return true if the file was updated, false if no updates were needed
     */
    public boolean update(String resourcePath, String targetPath) {
        File targetFile = new File(plugin.getDataFolder(), targetPath);

        if (!targetFile.exists()) {
            plugin.saveResource(resourcePath, false);
            return true;
        }

        try {
            InputStream defaultStream = plugin.getResource(resourcePath);
            if (defaultStream == null) {
                plugin.getLogger().severe("Cannot find resource: " + resourcePath);
                return false;
            }

            List<String> defaultLines = readLines(defaultStream);
            List<String> targetLines = readLines(targetFile);

            YamlConfiguration defaultConfig = loadConfigFromLines(defaultLines);
            YamlConfiguration targetConfig = YamlConfiguration.loadConfiguration(targetFile);

            Set<String> missingKeys = findMissingKeys(defaultConfig, targetConfig);

            if (missingKeys.isEmpty()) {
                return false;
            }

            plugin.getLogger().info("Found " + missingKeys.size() + " missing key(s) in " + targetPath);

            List<String> updatedLines = mergeConfigurations(defaultLines, targetLines, missingKeys, defaultConfig);

            writeLines(targetFile, updatedLines);

            plugin.getLogger().info("Successfully updated " + targetPath + " with new keys while preserving comments.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating " + targetPath, e);
            return false;
        }
    }

    /**
     * Finds all keys that exist in the default config but not in the target config.
     */
    private Set<String> findMissingKeys(YamlConfiguration defaultConfig, YamlConfiguration targetConfig) {
        Set<String> missing = new LinkedHashSet<>();
        collectMissingKeys(defaultConfig, targetConfig, "", missing);
        return missing;
    }

    private void collectMissingKeys(YamlConfiguration defaultConfig, YamlConfiguration targetConfig,
                                    String parentPath, Set<String> missing) {
        Set<String> defaultKeys = parentPath.isEmpty()
                ? defaultConfig.getKeys(false)
                : defaultConfig.getConfigurationSection(parentPath) != null
                ? defaultConfig.getConfigurationSection(parentPath).getKeys(false)
                : Collections.emptySet();

        for (String key : defaultKeys) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;

            if (!targetConfig.contains(fullPath)) {
                missing.add(fullPath);
            } else if (defaultConfig.isConfigurationSection(fullPath)) {
                collectMissingKeys(defaultConfig, targetConfig, fullPath, missing);
            }
        }
    }

    /**
     * Merges the default configuration into the target while preserving structure and comments.
     */
    private List<String> mergeConfigurations(List<String> defaultLines, List<String> targetLines,
                                             Set<String> missingKeys, YamlConfiguration defaultConfig) {
        Map<String, KeyBlock> defaultBlocks = parseKeyBlocks(defaultLines);
        List<String> result = new ArrayList<>(targetLines);

        Map<String, Set<String>> keysByParent = groupKeysByParent(missingKeys);

        for (Map.Entry<String, Set<String>> entry : keysByParent.entrySet()) {
            String parent = entry.getKey();
            Set<String> keys = entry.getValue();

            for (String fullKey : keys) {
                KeyBlock block = defaultBlocks.get(fullKey);
                if (block == null) {
                    block = createKeyBlock(fullKey, defaultConfig, defaultLines);
                }

                if (block != null) {
                    int insertPosition = findInsertPosition(result, parent, fullKey, defaultConfig);
                    result.addAll(insertPosition, block.lines);
                }
            }
        }

        return result;
    }

    /**
     * Groups keys by their parent path for ordered insertion.
     */
    private Map<String, Set<String>> groupKeysByParent(Set<String> keys) {
        Map<String, Set<String>> grouped = new LinkedHashMap<>();

        for (String key : keys) {
            String parent = key.contains(".") ? key.substring(0, key.lastIndexOf('.')) : "";
            grouped.computeIfAbsent(parent, k -> new LinkedHashSet<>()).add(key);
        }

        return grouped;
    }

    /**
     * Finds the best position to insert a new key.
     */
    private int findInsertPosition(List<String> lines, String parentPath, String fullKey, YamlConfiguration defaultConfig) {
        String keyName = fullKey.contains(".") ? fullKey.substring(fullKey.lastIndexOf('.') + 1) : fullKey;
        int targetIndent = (int) fullKey.chars().filter(c -> c == '.').count() * 2;

        if (parentPath.isEmpty()) {
            return findPositionAtRootLevel(lines, keyName, defaultConfig);
        }

        int parentLine = findKeyLine(lines, parentPath);
        if (parentLine == -1) {
            return lines.size();
        }

        int sectionEnd = findSectionEnd(lines, parentLine, targetIndent);
        return sectionEnd;
    }

    /**
     * Finds the position for a root-level key.
     */
    private int findPositionAtRootLevel(List<String> lines, String keyName, YamlConfiguration defaultConfig) {
        List<String> defaultRootKeys = new ArrayList<>(defaultConfig.getKeys(false));
        int keyIndex = defaultRootKeys.indexOf(keyName);

        if (keyIndex > 0) {
            String previousKey = defaultRootKeys.get(keyIndex - 1);
            int previousKeyLine = findKeyLine(lines, previousKey);
            if (previousKeyLine != -1) {
                return findSectionEnd(lines, previousKeyLine, 0);
            }
        }

        return lines.size();
    }

    /**
     * Finds the line number where a key is defined.
     */
    private int findKeyLine(List<String> lines, String fullPath) {
        String[] parts = fullPath.split("\\.");
        int currentLine = 0;
        int expectedIndent = 0;

        for (String part : parts) {
            boolean found = false;
            for (int i = currentLine; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = KEY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    int indent = matcher.group(1).length();
                    String key = matcher.group(2).trim();

                    if (indent == expectedIndent && key.equals(part)) {
                        currentLine = i;
                        expectedIndent += 2;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return -1;
        }

        return currentLine;
    }

    /**
     * Finds where a section ends (line number after last child).
     */
    private int findSectionEnd(List<String> lines, int startLine, int baseIndent) {
        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            Matcher keyMatcher = KEY_PATTERN.matcher(line);
            Matcher listMatcher = LIST_ITEM_PATTERN.matcher(line);

            int lineIndent;
            if (keyMatcher.matches()) {
                lineIndent = keyMatcher.group(1).length();
            } else if (listMatcher.matches()) {
                lineIndent = listMatcher.group(1).length();
            } else {
                continue;
            }

            if (lineIndent <= baseIndent) {
                return i;
            }
        }

        return lines.size();
    }

    /**
     * Parses default lines into blocks of content associated with each key.
     */
    private Map<String, KeyBlock> parseKeyBlocks(List<String> lines) {
        Map<String, KeyBlock> blocks = new LinkedHashMap<>();
        List<String> pendingComments = new ArrayList<>();
        Deque<String> pathStack = new ArrayDeque<>();
        Deque<Integer> indentStack = new ArrayDeque<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                pendingComments.add(line);
                continue;
            }

            Matcher matcher = KEY_PATTERN.matcher(line);
            if (matcher.matches()) {
                int indent = matcher.group(1).length();
                String key = matcher.group(2).trim();

                while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                    indentStack.pop();
                    pathStack.pop();
                }

                pathStack.push(key);
                indentStack.push(indent);

                String fullPath = buildPath(pathStack);

                KeyBlock block = new KeyBlock();
                block.lines.addAll(pendingComments);
                block.lines.add(line);
                block.startLine = i - pendingComments.size();

                int blockEnd = findKeyBlockEnd(lines, i, indent);
                for (int j = i + 1; j < blockEnd; j++) {
                    block.lines.add(lines.get(j));
                }

                blocks.put(fullPath, block);
                pendingComments.clear();
            }
        }

        return blocks;
    }

    private String buildPath(Deque<String> stack) {
        List<String> parts = new ArrayList<>(stack);
        Collections.reverse(parts);
        return String.join(".", parts);
    }

    private int findKeyBlockEnd(List<String> lines, int startLine, int baseIndent) {
        for (int i = startLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);

            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            Matcher keyMatcher = KEY_PATTERN.matcher(line);
            if (keyMatcher.matches()) {
                int indent = keyMatcher.group(1).length();
                if (indent <= baseIndent) {
                    return i;
                }
            }
        }
        return lines.size();
    }

    /**
     * Creates a key block from a missing key and its value.
     */
    private KeyBlock createKeyBlock(String fullKey, YamlConfiguration defaultConfig, List<String> defaultLines) {
        KeyBlock block = new KeyBlock();

        int indent = (int) fullKey.chars().filter(c -> c == '.').count() * 2;
        String indentStr = " ".repeat(indent);
        String keyName = fullKey.contains(".") ? fullKey.substring(fullKey.lastIndexOf('.') + 1) : fullKey;

        int keyLine = findKeyLineInDefault(defaultLines, fullKey);
        if (keyLine != -1) {
            List<String> comments = extractPrecedingComments(defaultLines, keyLine);
            for (String comment : comments) {
                if (indent > 0 && !comment.trim().isEmpty()) {
                    block.lines.add(indentStr + comment.trim());
                } else {
                    block.lines.add(comment);
                }
            }
        }

        Object value = defaultConfig.get(fullKey);
        if (defaultConfig.isConfigurationSection(fullKey)) {
            block.lines.add(indentStr + keyName + ":");
        } else if (value instanceof List) {
            block.lines.add(indentStr + keyName + ":");
            for (Object item : (List<?>) value) {
                block.lines.add(indentStr + "  - " + formatValue(item));
            }
        } else {
            block.lines.add(indentStr + keyName + ": " + formatValue(value));
        }

        if (!block.lines.isEmpty() && !block.lines.get(0).trim().isEmpty()) {
            block.lines.add(0, "");
        }

        return block;
    }

    private int findKeyLineInDefault(List<String> lines, String fullPath) {
        String[] parts = fullPath.split("\\.");
        int currentLine = 0;
        int expectedIndent = 0;

        for (String part : parts) {
            for (int i = currentLine; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = KEY_PATTERN.matcher(line);
                if (matcher.matches()) {
                    int indent = matcher.group(1).length();
                    String key = matcher.group(2).trim();

                    if (indent == expectedIndent && key.equals(part)) {
                        currentLine = i;
                        expectedIndent += 2;
                        break;
                    }
                }
            }
        }

        return currentLine;
    }

    private List<String> extractPrecedingComments(List<String> lines, int keyLine) {
        List<String> comments = new ArrayList<>();

        for (int i = keyLine - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.trim().startsWith("#") || line.trim().isEmpty()) {
                comments.add(0, line);
            } else {
                break;
            }
        }

        while (!comments.isEmpty() && comments.get(0).trim().isEmpty()) {
            comments.remove(0);
        }

        return comments;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String str) {
            if (str.isEmpty()) {
                return "''";
            }
            if (needsQuoting(str)) {
                return "'" + str.replace("'", "''") + "'";
            }
            return str;
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private boolean needsQuoting(String str) {
        if (str.startsWith(" ") || str.endsWith(" ")) return true;
        if (str.contains(":") || str.contains("#") || str.contains("'") || str.contains("\"")) return true;
        if (str.contains("\n") || str.contains("\r")) return true;

        String lower = str.toLowerCase();
        if (lower.equals("true") || lower.equals("false") || lower.equals("null") ||
                lower.equals("yes") || lower.equals("no") || lower.equals("on") || lower.equals("off")) {
            return true;
        }

        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException ignored) {
        }

        return false;
    }

    private List<String> readLines(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<String> readLines(File file) throws IOException {
        return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    }

    private void writeLines(File file, List<String> lines) throws IOException {
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    private YamlConfiguration loadConfigFromLines(List<String> lines) {
        String content = String.join("\n", lines);
        return YamlConfiguration.loadConfiguration(new StringReader(content));
    }

    /**
     * Represents a block of lines associated with a single key.
     */
    private static class KeyBlock {
        List<String> lines = new ArrayList<>();
        int startLine;
    }
}
