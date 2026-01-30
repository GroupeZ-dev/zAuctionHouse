package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public class NameContainsRule implements Rule {

    private final List<String> needles;

    public NameContainsRule(List<String> needles) {
        this.needles = needles.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        if (itemStack == null) return false;

        var componentMessage = ComponentMessageHelper.componentMessage;
        if (!componentMessage.hasDisplayName(itemStack)) return false;

        String name = componentMessage.getItemStackName(itemStack).toLowerCase(Locale.ROOT);
        return needles.stream().anyMatch(name::contains);
    }
}
