package fr.maxlego08.zauctionhouse.rule;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.event.events.RuleLoadEvent;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleManager;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.api.rules.Rules;
import fr.maxlego08.zauctionhouse.rule.rules.AndRule;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ZItemRuleManager implements ItemRuleManager {

    private final AuctionPlugin plugin;
    private final RuleLoaderRegistry ruleLoaderRegistry;
    private Rules blacklist = Rules.emptyDisabled();
    private Rules whitelist = Rules.emptyDisabled();

    public ZItemRuleManager(AuctionPlugin plugin, RuleLoaderRegistry ruleLoaderRegistry) {
        this.plugin = plugin;
        this.ruleLoaderRegistry = ruleLoaderRegistry;
    }

    @Override
    public boolean isBlacklisted(ItemStack itemStack) {
        ItemRuleContext context = new ZItemRuleContext(itemStack);
        return blacklist.matches(context);
    }

    @Override
    public boolean isWhitelisted(ItemStack itemStack) {
        ItemRuleContext context = new ZItemRuleContext(itemStack);
        return whitelist.matches(context);
    }

    @Override
    public boolean isAllowed(ItemStack itemStack) {
        ItemRuleContext context = new ZItemRuleContext(itemStack);

        if (whitelist.enabled() && whitelist.matches(context)) {
            return true;
        }

        return !blacklist.enabled() || !blacklist.matches(context);
    }

    @Override
    public Rules blacklistRules() {
        return blacklist;
    }

    @Override
    public Rules whitelistRules() {
        return whitelist;
    }

    @Override
    public boolean isBlacklistEnabled() {
        return blacklist.enabled();
    }

    @Override
    public void setBlacklistEnabled(boolean enabled) {
        this.blacklist = this.blacklist.withEnabled(enabled);
    }

    @Override
    public boolean isWhitelistEnabled() {
        return whitelist.enabled();
    }

    @Override
    public void setWhitelistEnabled(boolean enabled) {
        this.whitelist = this.whitelist.withEnabled(enabled);
    }

    @Override
    public void addBlacklistRule(Rule rule) {
        this.blacklist = this.blacklist.withAddedRule(rule);
    }

    @Override
    public void addWhitelistRule(Rule rule) {
        this.whitelist = this.whitelist.withAddedRule(rule);
    }

    @Override
    public void setBlacklistRules(Rules rules) {
        this.blacklist = rules;
    }

    @Override
    public void setWhitelistRules(Rules rules) {
        this.whitelist = rules;
    }

    @Override
    public void loadRules() {

        File file = new File(this.plugin.getDataFolder(), "rules.yml");
        if (!file.exists()) {
            this.plugin.saveFile("rules.yml", false);
        }

        RuleLoadEvent event = new RuleLoadEvent(this);
        event.callEvent();

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);

        this.blacklist = loadRuleSet(configuration, "blacklist", false);
        this.whitelist = loadRuleSet(configuration, "whitelist", true);
    }

    private Rules loadRuleSet(FileConfiguration config, String basePath, boolean defaultEnabled) {
        boolean enabled = config.getBoolean(basePath + ".enabled", defaultEnabled);

        List<Map<?, ?>> maps = config.getMapList(basePath + ".rules");
        if (maps.isEmpty()) {
            return new Rules(enabled, List.of());
        }

        List<Rule> topLevelRules = new ArrayList<>();

        for (Map<?, ?> map : maps) {
            List<Rule> subRules = ruleLoaderRegistry.loadRules(map);
            if (!subRules.isEmpty()) {
                if (subRules.size() == 1) {
                    topLevelRules.add(subRules.getFirst());
                } else {
                    topLevelRules.add(new AndRule(subRules));
                }
            }
        }

        return new Rules(enabled, topLevelRules);
    }
}

