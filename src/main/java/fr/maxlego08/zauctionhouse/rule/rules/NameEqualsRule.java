package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items whose display name exactly equals one of the specified values.
 * Supports case-insensitive matching. Color codes are automatically stripped using MiniMessage.
 */
public class NameEqualsRule implements Rule {

    private final List<String> names;
    private final boolean ignoreCase;

    public NameEqualsRule(List<String> names, boolean ignoreCase) {
        this.ignoreCase = ignoreCase;

        if (ignoreCase) {
            this.names = names.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();
        } else {
            this.names = names;
        }
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        if (itemStack == null) return false;

        if (!ComponentMessageHelper.componentMessage.hasDisplayName(itemStack)) return false;

        String displayName = ComponentMessageHelper.componentMessage.getItemStackName(itemStack);
        if (ignoreCase) {
            displayName = displayName.toLowerCase(Locale.ROOT);
        }

        return names.contains(displayName);
    }
}
