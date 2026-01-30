package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items that have a lore line exactly equal to one of the specified values.
 * Comparison is case-insensitive. Color codes are automatically stripped using MiniMessage.
 */
public class LoreEqualsRule implements Rule {

    private final List<String> loreLines;

    public LoreEqualsRule(List<String> loreLines) {
        this.loreLines = loreLines.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        if (itemStack == null) return false;

        List<String> lore = ComponentMessageHelper.componentMessage.getItemStackLore(itemStack);
        if (lore.isEmpty()) return false;

        for (String line : lore) {
            String cleanLine = line.toLowerCase(Locale.ROOT);
            if (loreLines.contains(cleanLine)) {
                return true;
            }
        }
        return false;
    }
}
