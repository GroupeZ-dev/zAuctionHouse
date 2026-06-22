package fr.maxlego08.zauctionhouse.discord;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.discord.DiscordConfiguration;
import fr.maxlego08.zauctionhouse.api.configuration.discord.DiscordEmbed;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhookService {

    private final AuctionPlugin plugin;
    private final HttpClient httpClient;
    private DiscordConfiguration configuration;
    private ColorExtractor colorExtractor;

    public DiscordWebhookService(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        loadConfiguration();
    }

    /**
     * Loads the Discord webhook configuration from the plugin's configuration file.
     * If the webhook feature is enabled, it logs a message to the plugin's logger.
     * If dominant color extraction is enabled, it initializes a color extractor with the default color.
     */
    public void loadConfiguration() {
        this.configuration = DiscordConfiguration.of(plugin);
        if (configuration.enabled()) {
            plugin.getLogger().info("Discord webhooks enabled");

            // Initialize color extractor if dominant color extraction is enabled
            if (configuration.extractDominantColor()) {
                this.colorExtractor = new ColorExtractor(
                        this.plugin.getDataFolder(),
                        this.plugin.getLogger(),
                        configuration.defaultColor()
                );
                this.plugin.getLogger().info("Dominant color extraction enabled (cache: .cache/material-colors.json)");
            } else {
                this.colorExtractor = null;
            }
        }
    }

    /**
     * Returns whether the Discord webhook feature is enabled.
     * The method checks if the configuration is not null and if the enabled flag is set to true.
     *
     * @return true if the Discord webhook feature is enabled, false otherwise
     */
    public boolean isEnabled() {
        return configuration != null && configuration.enabled();
    }

    /**
     * Notifies a Discord webhook that an item has been purchased.
     * The method sends a webhook with the given auction item and buyer information.
     * If the plugin's Discord configuration is disabled, the method returns a completed future.
     *
     * @param seller      the seller of the item
     * @param auctionItem the auction item that was sold
     * @return a future representing the completion of the webhook
     */
    public CompletableFuture<Void> notifyItemSold(Player seller, AuctionItem auctionItem) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        var webhook = configuration.sellWebhook();
        if (!webhook.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        return sendWebhook(webhook, auctionItem, seller, null);
    }

    /**
     * Notifies a Discord webhook that an item has been purchased.
     * The method sends a webhook with the given auction item and buyer information.
     * If the plugin's Discord configuration is disabled, the method returns a completed future.
     * If the purchase webhook configuration is invalid, the method returns a completed future.
     *
     * @param buyer       the buyer of the auction item, or null if unknown
     * @param auctionItem the auction item to notify about
     * @return a future representing the completion of the webhook
     */
    public CompletableFuture<Void> notifyItemPurchased(Player buyer, AuctionItem auctionItem) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        var webhook = configuration.purchaseWebhook();
        if (!webhook.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        return sendWebhook(webhook, auctionItem, null, buyer);
    }

    /**
     * Sends a Discord webhook with the given configuration and auction item.
     *
     * @param webhook     the webhook configuration to use
     * @param auctionItem the auction item to send
     * @param seller      the seller of the auction item, or null if unknown
     * @param buyer       the buyer of the auction item, or null if unknown
     * @return a future representing the completion of the webhook
     */
    private CompletableFuture<Void> sendWebhook(DiscordConfiguration.WebhookConfiguration webhook, AuctionItem auctionItem, Player seller, Player buyer) {
        return CompletableFuture.runAsync(() -> {
            try {
                var resolver = new DiscordPlaceholderResolver(configuration.serverName(), auctionItem, seller, buyer, configuration.itemImageUrl(), configuration.extractDominantColor(), configuration.defaultColor(), colorExtractor, configuration.customImages());

                String json = buildWebhookJson(webhook, resolver);

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(webhook.url())) //
                        .header("Content-Type", "application/json") //
                        .POST(HttpRequest.BodyPublishers.ofString(json)) //
                        .timeout(Duration.ofSeconds(30)) //
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    this.plugin.getLogger().warning("Discord webhook failed with status " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        }, this.plugin.getExecutorService());
    }

    /**
     * Builds a JSON string representing a Discord webhook payload.
     * The method uses the given webhook configuration and placeholder resolver to build the payload.
     * The payload is built according to the Discord Webhook API documentation.
     *
     * @param webhook  the webhook configuration to use for building the payload
     * @param resolver the placeholder resolver to use for resolving placeholders in the webhook configuration
     * @return a JSON string representing the webhook payload
     */
    private String buildWebhookJson(DiscordConfiguration.WebhookConfiguration webhook, DiscordPlaceholderResolver resolver) {
        var embedConfig = webhook.embed();
        // Resolve color placeholder (e.g., %item_dominant_color%)
        String resolvedColor = resolver.resolve(embedConfig.color());

        var builder = DiscordEmbed.builder() //
                .title(resolver.resolve(embedConfig.title())) //
                .description(resolver.resolve(embedConfig.description())) //
                .color(resolvedColor) //
                .timestamp(embedConfig.timestamp()) //
                ;

        // Webhook-level options
        if (webhook.hasUsername()) {
            builder.username(resolver.resolve(webhook.username()));
        }

        if (webhook.hasAvatarUrl()) {
            builder.avatarUrl(resolver.resolve(webhook.avatarUrl()));
        }

        if (webhook.hasContent()) {
            builder.content(resolver.resolve(webhook.content()));
        }

        // Embed fields
        for (var field : embedConfig.fields()) {
            builder.addField(resolver.resolve(field.name()), resolver.resolve(field.value()), field.inline());
        }

        if (embedConfig.footer().hasContent()) {
            builder.footer(resolver.resolve(embedConfig.footer().text()), resolver.resolve(embedConfig.footer().iconUrl()));
        }

        if (embedConfig.author().hasContent()) {
            builder.author(resolver.resolve(embedConfig.author().name()), resolver.resolve(embedConfig.author().url()), resolver.resolve(embedConfig.author().iconUrl()));
        }

        if (embedConfig.thumbnail().hasContent()) {
            builder.thumbnail(resolver.resolve(embedConfig.thumbnail().url()));
        }

        if (embedConfig.image().hasContent()) {
            builder.image(resolver.resolve(embedConfig.image().url()));
        }

        return builder.build().toJson();
    }
}
