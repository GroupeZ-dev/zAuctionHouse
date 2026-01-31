package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import org.bukkit.Material;

import java.util.Set;

public class MaterialRule implements Rule {

    private final Set<Material> materials;

    public MaterialRule(Set<Material> materials) {
        this.materials = materials;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        return this.materials.contains(context.getMaterial());
    }

    @Override
    public boolean isValid() {
        return !this.materials.isEmpty();
    }
}
