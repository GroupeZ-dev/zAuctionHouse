package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.utils.TagRegistry;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TagRule implements Rule {

    private final List<Tag<Material>> tags;

    public TagRule(List<String> tagNames) {
        this.tags = tagNames.stream().map(TagRule::resolveTag).filter(Objects::nonNull).toList();
    }

    private static Tag<Material> resolveTag(String name) {
        if (name == null) return null;

        try {
            return TagRegistry.getTag(name.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
        }

        return null;
    }

    @Override
    public boolean matches(ItemRuleContext context) {
        Material type = context.getMaterial();
        for (Tag<Material> tag : this.tags) {
            if (tag.isTagged(type)) {
                return true;
            }
        }
        return false;
    }
}
