package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.MaterialRule;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads MaterialRule from configuration.
 * Matches items by their exact material type.
 */
public class MaterialRuleLoader implements RuleLoader {

    @Override
    public String getType() {
        return "material";
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> materials = RuleConfigHelper.getStringList(configuration, "materials");
        if (materials.isEmpty()) {
            return null;
        }

        Set<Material> materialSet = materials.stream()
                .map(name -> {
                    try {
                        return Material.valueOf(name.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (materialSet.isEmpty()) {
            return null;
        }

        return new MaterialRule(materialSet);
    }
}
