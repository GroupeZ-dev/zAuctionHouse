package fr.maxlego08.zauctionhouse.hooks.nexo;

import com.nexomc.nexo.api.NexoItems;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rule that matches items created by Nexo plugin.
 * Supports exact IDs and wildcard patterns (e.g., "*_sword", "emerald_*").
 */
public class NexoRule implements Rule {

    private final List<String> itemIds;
    private final List<Pattern> patterns;

    public NexoRule(List<String> itemIds) {
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

        String nexoId = NexoItems.idFromItem(itemStack);
        if (nexoId == null) return false;

        String lowercaseId = nexoId.toLowerCase(Locale.ROOT);

        // Check exact matches
        if (itemIds.contains(lowercaseId)) {
            return true;
        }

        // Check wildcard patterns
        for (Pattern pattern : patterns) {
            if (pattern.matcher(lowercaseId).matches()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isValid() {
        return !this.itemIds.isEmpty() || !this.patterns.isEmpty();
    }
}
