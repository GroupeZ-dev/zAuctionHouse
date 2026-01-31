package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;

import java.util.List;
import java.util.Locale;

/**
 * Rule that matches items whose material name ends with one of the specified suffixes.
 * Useful for matching groups like "_SWORD", "_PICKAXE", "_HELMET", etc.
 */
public class MaterialSuffixRule implements Rule {

    private final List<String> suffixes;

    public MaterialSuffixRule(List<String> suffixes) {
        this.suffixes = suffixes.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        String materialName = context.getMaterial().name();
        for (String suffix : this.suffixes) {
            if (materialName.endsWith(suffix)) {
                return true;
            }
        }
        return !this.suffixes.isEmpty();
    }
}
