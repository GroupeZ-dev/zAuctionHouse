package fr.maxlego08.zauctionhouse.hooks.itemsadder;

import dev.lone.itemsadder.api.CustomStack;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rule that matches items created by ItemsAdder plugin.
 * Supports exact IDs and wildcard patterns (e.g., "namespace:*", "*:item_id").
 */
public class ItemsAdderRule implements Rule {

    private final List<String> itemIds;
    private final List<Pattern> patterns;

    public ItemsAdderRule(List<String> itemIds) {
        this.itemIds = itemIds.stream().filter(id -> !id.contains("*")).map(id -> id.toLowerCase(Locale.ROOT)).toList();

        this.patterns = itemIds.stream().filter(id -> id.contains("*")).map(this::wildcardToPattern).toList();
    }

    private Pattern wildcardToPattern(String wildcard) {
        String regex = wildcard.toLowerCase(Locale.ROOT).replace(".", "\\.").replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        ItemStack itemStack = context.getItemStack();
        if (itemStack == null) return false;

        CustomStack customStack = CustomStack.byItemStack(itemStack);
        if (customStack == null) return false;

        String namespacedId = customStack.getNamespacedID().toLowerCase(Locale.ROOT);

        // Check exact matches
        if (itemIds.contains(namespacedId)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(namespacedId).matches()) {
                return true;
            }
        }

        return false;
    }
}
