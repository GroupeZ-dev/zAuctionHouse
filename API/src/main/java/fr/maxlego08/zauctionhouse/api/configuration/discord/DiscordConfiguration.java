package fr.maxlego08.zauctionhouse.api.configuration.discord;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record DiscordConfiguration(boolean enabled, String serverName, String itemImageUrl,
                                   boolean extractDominantColor, String defaultColor,
                                   List<CustomImageRule> customImages,
                                   WebhookConfiguration sellWebhook, WebhookConfiguration purchaseWebhook) {

    public static DiscordConfiguration of(AuctionPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "discord.yml");
        if (!file.exists()) {
            plugin.saveFile("discord.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        boolean enabled = config.getBoolean("enabled", false);
        String serverName = config.getString("server-name", "My Server");
        String itemImageUrl = config.getString("item-image-url", "https://img.groupez.dev/minecraft/%item_material%.png");
        boolean extractDominantColor = config.getBoolean("extract-dominant-color", true);
        String defaultColor = config.getString("default-color", "#5865F2");

        List<CustomImageRule> customImages = loadCustomImages(config, plugin.getRuleLoaderRegistry());

        WebhookConfiguration sellWebhook = WebhookConfiguration.of(config, "webhooks.sell");
        WebhookConfiguration purchaseWebhook = WebhookConfiguration.of(config, "webhooks.purchase");

        return new DiscordConfiguration(enabled, serverName, itemImageUrl, extractDominantColor, defaultColor, customImages, sellWebhook, purchaseWebhook);
    }

    /**
     * Loads the per-item custom image rules from the {@code custom-images} section.
     * <p>
     * Each entry pairs a {@code rule} block (using the same format as category/blacklist
     * rules, e.g. {@code type: material}, {@code type: oraxen}) with an {@code image} URL.
     * When an auction item matches a rule, its image is used instead of the default
     * material-based {@code item-image-url} pattern.
     *
     * @param config              the loaded discord.yml configuration
     * @param ruleLoaderRegistry  the registry used to build rules from their configuration
     * @return the list of valid custom image rules, in declaration order (never null)
     */
    private static List<CustomImageRule> loadCustomImages(YamlConfiguration config, RuleLoaderRegistry ruleLoaderRegistry) {
        List<CustomImageRule> customImages = new ArrayList<>();
        if (ruleLoaderRegistry == null) {
            return customImages;
        }

        for (Map<?, ?> entry : config.getMapList("custom-images")) {
            Object ruleObject = entry.get("rule");
            Object imageObject = entry.get("image");

            if (!(ruleObject instanceof Map<?, ?> ruleMap) || imageObject == null) {
                continue;
            }

            String image = String.valueOf(imageObject);
            if (image.isEmpty()) {
                continue;
            }

            Rule rule = ruleLoaderRegistry.loadRule(ruleMap);
            if (rule != null && rule.isValid()) {
                customImages.add(new CustomImageRule(rule, image));
            }
        }

        return customImages;
    }

    /**
     * Associates an item-matching {@link Rule} with a custom image URL.
     * Used to override the Discord webhook image for specific items.
     *
     * @param rule  the rule an item must match for this image to apply
     * @param image the image URL to use when the rule matches
     */
    public record CustomImageRule(Rule rule, String image) {
    }

    public record WebhookConfiguration(boolean enabled, String url, String username, String avatarUrl, String content, EmbedConfiguration embed) {
        public static WebhookConfiguration of(YamlConfiguration config, String path) {
            boolean enabled = config.getBoolean(path + ".enabled", false);
            String url = config.getString(path + ".url", "");
            String username = config.getString(path + ".username", "");
            String avatarUrl = config.getString(path + ".avatar-url", "");
            String content = config.getString(path + ".content", "");
            EmbedConfiguration embed = EmbedConfiguration.of(config, path + ".embed");
            return new WebhookConfiguration(enabled, url, username, avatarUrl, content, embed);
        }

        public boolean isValid() {
            return enabled && url != null && !url.isEmpty() && url.startsWith("https://discord.com/api/webhooks/");
        }

        public boolean hasUsername() {
            return username != null && !username.isEmpty();
        }

        public boolean hasAvatarUrl() {
            return avatarUrl != null && !avatarUrl.isEmpty();
        }

        public boolean hasContent() {
            return content != null && !content.isEmpty();
        }
    }

    public record EmbedConfiguration(String title, String description, String color, List<FieldConfiguration> fields,
                                     FooterConfiguration footer, AuthorConfiguration author,
                                     ThumbnailConfiguration thumbnail, ImageConfiguration image, boolean timestamp) {
        public static EmbedConfiguration of(YamlConfiguration config, String path) {
            String title = config.getString(path + ".title", "");
            String description = config.getString(path + ".description", "");
            String color = config.getString(path + ".color", "#FFFFFF");

            List<FieldConfiguration> fields = new ArrayList<>();
            List<Map<?, ?>> fieldMaps = config.getMapList(path + ".fields");
            for (Map<?, ?> fieldMap : fieldMaps) {
                Object nameObj = fieldMap.get("name");
                Object valueObj = fieldMap.get("value");
                Object inlineObj = fieldMap.get("inline");

                String name = nameObj != null ? String.valueOf(nameObj) : "";
                String value = valueObj != null ? String.valueOf(valueObj) : "";
                boolean inline = inlineObj != null && Boolean.parseBoolean(String.valueOf(inlineObj));
                fields.add(new FieldConfiguration(name, value, inline));
            }

            FooterConfiguration footer = FooterConfiguration.of(config, path + ".footer");
            AuthorConfiguration author = AuthorConfiguration.of(config, path + ".author");
            ThumbnailConfiguration thumbnail = ThumbnailConfiguration.of(config, path + ".thumbnail");
            ImageConfiguration image = ImageConfiguration.of(config, path + ".image");
            boolean timestamp = config.getBoolean(path + ".timestamp", false);

            return new EmbedConfiguration(title, description, color, fields, footer, author, thumbnail, image, timestamp);
        }
    }

    public record FieldConfiguration(String name, String value, boolean inline) {
    }

    public record FooterConfiguration(String text, String iconUrl) {
        public static FooterConfiguration of(YamlConfiguration config, String path) {
            String text = config.getString(path + ".text", "");
            String iconUrl = config.getString(path + ".icon-url", "");
            return new FooterConfiguration(text, iconUrl);
        }

        public boolean hasContent() {
            return text != null && !text.isEmpty();
        }
    }

    public record AuthorConfiguration(String name, String url, String iconUrl) {
        public static AuthorConfiguration of(YamlConfiguration config, String path) {
            String name = config.getString(path + ".name", "");
            String url = config.getString(path + ".url", "");
            String iconUrl = config.getString(path + ".icon-url", "");
            return new AuthorConfiguration(name, url, iconUrl);
        }

        public boolean hasContent() {
            return name != null && !name.isEmpty();
        }
    }

    public record ThumbnailConfiguration(String url) {
        public static ThumbnailConfiguration of(YamlConfiguration config, String path) {
            String url = config.getString(path + ".url", "");
            return new ThumbnailConfiguration(url);
        }

        public boolean hasContent() {
            return url != null && !url.isEmpty();
        }
    }

    public record ImageConfiguration(String url) {
        public static ImageConfiguration of(YamlConfiguration config, String path) {
            String url = config.getString(path + ".url", "");
            return new ImageConfiguration(url);
        }

        public boolean hasContent() {
            return url != null && !url.isEmpty();
        }
    }
}
