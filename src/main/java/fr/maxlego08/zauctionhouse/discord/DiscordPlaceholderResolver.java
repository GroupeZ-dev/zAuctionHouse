package fr.maxlego08.zauctionhouse.discord;

import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.configuration.discord.DiscordConfiguration;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleContext;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DiscordPlaceholderResolver {

    private final Map<String, Supplier<String>> placeholders = new HashMap<>();
    private String cachedImageUrl;
    private String cachedDominantColor;

    public DiscordPlaceholderResolver(String serverName, AuctionItem auctionItem, Player seller, Player buyer, String itemImageUrlPattern, boolean extractDominantColor, String defaultColor, ColorExtractor colorExtractor, List<DiscordConfiguration.CustomImageRule> customImages) {
        registerItemPlaceholders(auctionItem);
        registerSellerPlaceholders(auctionItem, seller);
        registerBuyerPlaceholders(auctionItem, buyer);
        registerPricePlaceholders(auctionItem);
        registerTimePlaceholders(auctionItem);
        registerServerPlaceholders(serverName);
        registerCategoryPlaceholders(auctionItem);
        registerImagePlaceholders(auctionItem, itemImageUrlPattern, extractDominantColor, defaultColor, colorExtractor, customImages);
    }

    /**
     * Registers placeholders related to the auction item.
     * The method registers placeholders for the item ID, material, amount, display name, lore, enchantments, custom model data.
     * If the auction item's item stack is null, the placeholders are replaced with default values.
     *
     * @param auctionItem the auction item to register placeholders for
     */
    private void registerItemPlaceholders(AuctionItem auctionItem) {
        placeholders.put("%item_id%", () -> String.valueOf(auctionItem.getId()));

        ItemStack itemStack = auctionItem.getItemStack();
        if (itemStack != null) {
            placeholders.put("%item_material%", () -> itemStack.getType().name());
            placeholders.put("%item_amount%", () -> String.valueOf(auctionItem.getAmount()));
            placeholders.put("%item_display%", () -> ComponentMessageHelper.componentMessage.stripColor(auctionItem.getItemDisplay()));

            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                placeholders.put("%item_lore%", () -> {
                    var lore = ComponentMessageHelper.componentMessage.getItemStackLore(itemStack);
                    return lore != null ? String.join("\n", lore) : "";
                });

                placeholders.put("%item_enchantments%", () -> {
                    if (meta.hasEnchants()) {
                        return meta.getEnchants().entrySet().stream().map(e -> formatEnchantment(e.getKey()) + " " + e.getValue()).collect(Collectors.joining(", "));
                    }
                    return "None";
                });

                placeholders.put("%item_custom_model_data%", () -> {
                    if (meta.hasCustomModelData()) {
                        return String.valueOf(meta.getCustomModelData());
                    }
                    return "0";
                });
            } else {
                placeholders.put("%item_lore%", () -> "");
                placeholders.put("%item_enchantments%", () -> "None");
                placeholders.put("%item_custom_model_data%", () -> "0");
            }
        } else {
            placeholders.put("%item_material%", () -> "UNKNOWN");
            placeholders.put("%item_amount%", () -> "0");
            placeholders.put("%item_display%", () -> "Unknown Item");
            placeholders.put("%item_lore%", () -> "");
            placeholders.put("%item_enchantments%", () -> "None");
            placeholders.put("%item_custom_model_data%", () -> "0");
        }
    }

    /**
     * Registers placeholders related to the seller of the auction item.
     * The method registers placeholders for the seller's name and UUID.
     * If the seller is null, the placeholders are replaced with default values.
     *
     * @param auctionItem the auction item to register placeholders for
     * @param seller      the seller of the auction item, or null if unknown
     */
    private void registerSellerPlaceholders(AuctionItem auctionItem, Player seller) {
        if (seller != null) {
            placeholders.put("%seller_name%", seller::getName);
            placeholders.put("%seller_uuid%", () -> seller.getUniqueId().toString());
        } else {
            placeholders.put("%seller_name%", auctionItem::getSellerName);
            placeholders.put("%seller_uuid%", () -> {
                UUID uuid = auctionItem.getSellerUniqueId();
                return uuid != null ? uuid.toString() : "";
            });
        }
    }

    /**
     * Registers placeholders related to the buyer of the auction item.
     * The method registers placeholders for the buyer's name and UUID.
     * If the buyer is null, the placeholders are replaced with default values.
     *
     * @param auctionItem the auction item to register placeholders for
     * @param buyer       the buyer of the auction item, or null if unknown
     */
    private void registerBuyerPlaceholders(AuctionItem auctionItem, Player buyer) {
        if (buyer != null) {
            placeholders.put("%buyer_name%", buyer::getName);
            placeholders.put("%buyer_uuid%", () -> buyer.getUniqueId().toString());
        } else {
            String buyerName = auctionItem.getBuyerName();
            UUID buyerUuid = auctionItem.getBuyerUniqueId();
            placeholders.put("%buyer_name%", () -> buyerName != null ? buyerName : "Unknown");
            placeholders.put("%buyer_uuid%", () -> buyerUuid != null ? buyerUuid.toString() : "");
        }
    }

    /**
     * Registers placeholders related to the auction item's price.
     * The method registers placeholders for the price, formatted price, economy name and economy display name.
     * If the auction item's economy is null, the placeholders are replaced with default values.
     *
     * @param auctionItem the auction item to register placeholders for
     */
    private void registerPricePlaceholders(AuctionItem auctionItem) {
        placeholders.put("%price%", () -> auctionItem.getPrice().toPlainString());
        placeholders.put("%formatted_price%", auctionItem::getFormattedPrice);

        var economy = auctionItem.getAuctionEconomy();
        if (economy != null) {
            placeholders.put("%economy_name%", economy::getName);
            placeholders.put("%economy_display_name%", economy::getDisplayName);
        } else {
            placeholders.put("%economy_name%", () -> "default");
            placeholders.put("%economy_display_name%", () -> "Default");
        }
    }

    /**
     * Registers placeholders related to the auction item's timestamps.
     * The method registers placeholders for the created at timestamp, expires at timestamp, remaining time and the current timestamp.
     * If the auction item's created at timestamp is null, the placeholder is replaced with an empty string.
     *
     * @param auctionItem the auction item to register placeholders for
     */
    private void registerTimePlaceholders(AuctionItem auctionItem) {
        placeholders.put("%created_at%", () -> {
            var createdAt = auctionItem.getCreatedAt();
            return createdAt != null ? createdAt.toString() : "";
        });

        placeholders.put("%expires_at%", () -> {
            var expiredAt = auctionItem.getExpiredAt();
            if (expiredAt == null || expiredAt.getTime() == 0) {
                return auctionItem.getFormattedExpireDate();
            }
            // Discord dynamic timestamp: rendered by Discord in each viewer's local timezone (e.g. "12 June 2026 18:55").
            // The value is in seconds since epoch, not milliseconds. The ":f" style is "short date/time".
            return "<t:" + (expiredAt.getTime() / 1000L) + ":f>";
        });
        placeholders.put("%remaining_time%", auctionItem::getRemainingTime);
        placeholders.put("%timestamp%", () -> Instant.now().toString());
    }

    /**
     * Registers placeholders related to the server name.
     *
     * @param serverName the name of the server to register placeholders for
     */
    private void registerServerPlaceholders(String serverName) {
        placeholders.put("%server_name%", () -> serverName);
    }

    /**
     * Registers placeholders related to the auction item's categories.
     *
     * @param auctionItem the auction item to register placeholders for
     */
    private void registerCategoryPlaceholders(AuctionItem auctionItem) {
        placeholders.put("%category_names%", () -> {
            var categories = auctionItem.getCategories();
            if (categories == null || categories.isEmpty()) {
                return "None";
            }
            return categories.stream().map(Category::getDisplayName).collect(Collectors.joining(", "));
        });

        placeholders.put("%category_count%", () -> {
            var categories = auctionItem.getCategories();
            return String.valueOf(categories != null ? categories.size() : 0);
        });
    }

    /**
     * Registers placeholders related to the item image.
     * <p>
     * A custom image rule (see {@code custom-images} in discord.yml) takes precedence: if the
     * item matches one, its image is used. Otherwise the URL is built from the material-based
     * pattern. The resolved value is exposed via {@code %item_image_url%}.
     *
     * @param auctionItem          the auction item to register placeholders for
     * @param itemImageUrlPattern  the pattern to use for building the item image URL
     * @param extractDominantColor whether to extract the dominant color from the item image
     * @param defaultColor         the default color to use if the dominant color extraction fails
     * @param colorExtractor       the color extractor to use for extracting the dominant color
     * @param customImages         the per-item custom image rules, checked before the pattern
     */
    private void registerImagePlaceholders(AuctionItem auctionItem, String itemImageUrlPattern, boolean extractDominantColor, String defaultColor, ColorExtractor colorExtractor, List<DiscordConfiguration.CustomImageRule> customImages) {
        ItemStack itemStack = auctionItem.getItemStack();
        String material = itemStack != null ? itemStack.getType().name() : "UNKNOWN";

        // A custom image rule overrides the material-based pattern when the item matches.
        String customImage = resolveCustomImage(itemStack, customImages);
        String colorCacheKey;

        if (customImage != null && !customImage.isEmpty()) {
            cachedImageUrl = customImage;
            // Cache the extracted color by URL: custom items can share a material (e.g. PAPER)
            // while having different images, so the material alone is not a unique key.
            colorCacheKey = customImage;
        } else if (itemImageUrlPattern != null && !itemImageUrlPattern.isEmpty()) {
            cachedImageUrl = itemImageUrlPattern.replace("%item_material%", material).replace("%ITEM_MATERIAL%", material);
            colorCacheKey = material;
        } else {
            cachedImageUrl = "";
            colorCacheKey = material;
        }

        placeholders.put("%item_image_url%", () -> cachedImageUrl);

        if (extractDominantColor && colorExtractor != null && !cachedImageUrl.isEmpty()) {
            cachedDominantColor = colorExtractor.getColorForMaterial(colorCacheKey, cachedImageUrl);
        } else {
            cachedDominantColor = defaultColor;
        }

        placeholders.put("%item_dominant_color%", () -> cachedDominantColor);
    }

    /**
     * Finds the first custom image whose rule matches the given item.
     *
     * @param itemStack    the item stack to match against, or null
     * @param customImages the configured custom image rules
     * @return the matching image URL, or null if none matches
     */
    private String resolveCustomImage(ItemStack itemStack, List<DiscordConfiguration.CustomImageRule> customImages) {
        if (itemStack == null || customImages == null || customImages.isEmpty()) {
            return null;
        }

        ItemRuleContext context = new ZItemRuleContext(itemStack);
        for (DiscordConfiguration.CustomImageRule customImage : customImages) {
            if (customImage.rule().matches(context)) {
                return customImage.image();
            }
        }

        return null;
    }

    /**
     * Resolves placeholders in a given text.
     * The method goes through all the registered placeholders and replaces the placeholder with the corresponding value.
     * If the value is null, the placeholder is replaced with an empty string.
     *
     * @param text the text to resolve placeholders in
     * @return the resolved text
     */
    public String resolve(String text) {

        if (text == null || text.isEmpty()) return text;

        String result = text;
        for (Map.Entry<String, Supplier<String>> entry : placeholders.entrySet()) {
            if (result.contains(entry.getKey())) {
                String value = entry.getValue().get();
                result = result.replace(entry.getKey(), value != null ? value : "");
            }
        }

        return result;
    }

    /**
     * Formats an enchantment key into a human-readable string.
     * The method splits the key by underscores and capitalizes the first letter of each part.
     * The formatted parts are then concatenated with a space separator.
     * If a part is empty, it is ignored.
     *
     * @param enchantment the enchantment to format
     * @return the formatted enchantment key
     */
    private String formatEnchantment(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                formatted.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase()).append(" ");
            }
        }
        return formatted.toString().trim();
    }
}
