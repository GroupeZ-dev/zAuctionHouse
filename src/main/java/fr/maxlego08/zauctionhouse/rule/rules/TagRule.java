package fr.maxlego08.zauctionhouse.rule.rules;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.utils.tags.TagRegistry;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TagRule implements Rule {

    private final List<Tag<Material>> tags;

    public TagRule(AuctionPlugin auctionPlugin, List<String> tagNames) {
        this.tags = tagNames.stream().map(name -> resolveTag(auctionPlugin, name)).filter(Objects::nonNull).toList();
    }

    private static Tag<Material> resolveTag(AuctionPlugin plugin, String name) {
        if (name == null) return null;

        try {
            return TagRegistry.getTag(name.toLowerCase(Locale.ROOT));
        } catch (Exception exception) {
            plugin.getLogger().severe("Failed to resolve tag '" + name + "': " + exception.getMessage());
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

    @Override
    public boolean isValid() {
        return !this.tags.isEmpty();
    }
}
