package fr.maxlego08.zauctionhouse.rule.loaders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.RuleConfigHelper;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoader;
import fr.maxlego08.zauctionhouse.rule.rules.TagRule;

import java.util.List;
import java.util.Map;

/**
 * Loads TagRule from configuration.
 * Matches items by Minecraft material tags (e.g., LOGS, PLANKS, WOOL).
 */
public class TagRuleLoader implements RuleLoader {

    private final AuctionPlugin auctionPlugin;

    public TagRuleLoader(AuctionPlugin auctionPlugin) {
        this.auctionPlugin = auctionPlugin;
    }

    @Override
    public String getType() {
        return "material-tag";
    }

    @Override
    public List<String> getAliases() {
        return List.of("tag");
    }

    @Override
    public Rule load(Map<?, ?> configuration) {
        List<String> tags = RuleConfigHelper.getStringList(configuration, "tags");
        if (tags.isEmpty()) {
            String tag = RuleConfigHelper.getString(configuration, "tag");
            if (tag != null) {
                tags = List.of(tag);
            }
        }

        System.out.printf("tags: " + tags);

        if (tags.isEmpty()) {
            return null;
        }
        return new TagRule(this.auctionPlugin, tags);
    }
}
