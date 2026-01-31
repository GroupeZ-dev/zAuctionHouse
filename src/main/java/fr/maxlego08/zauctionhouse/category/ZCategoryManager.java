package fr.maxlego08.zauctionhouse.category;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.category.CategoryIcon;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleContext;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Implementation of {@link CategoryManager}.
 * Handles loading categories from YAML files and matching items to categories.
 */
public class ZCategoryManager implements CategoryManager {

    private final AuctionPlugin plugin;
    private final RuleLoaderRegistry ruleLoaderRegistry;
    private final Map<String, Category> categories = new HashMap<>();
    private List<Category> sortedCategories = List.of();
    private Category miscCategory;
    private boolean enabled = true;

    public ZCategoryManager(AuctionPlugin plugin, RuleLoaderRegistry ruleLoaderRegistry) {
        this.plugin = plugin;
        this.ruleLoaderRegistry = ruleLoaderRegistry;
    }

    @Override
    public void loadCategories() {
        this.categories.clear();

        // Save default categories.yml if not exists
        File mainFile = new File(this.plugin.getDataFolder(), "categories.yml");
        if (!mainFile.exists()) {
            this.plugin.saveFile("categories.yml", false);
        }

        // Load main categories.yml
        if (mainFile.exists()) {
            this.loadCategoriesFromFile(mainFile);
        }

        // Load from categories/ directory
        File categoriesDir = new File(this.plugin.getDataFolder(), "categories");
        if (categoriesDir.exists() && categoriesDir.isDirectory()) {
            File[] files = categoriesDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    this.loadCategoriesFromFile(file);
                }
            }
        }

        // Ensure misc category exists
        if (this.miscCategory == null) {
            this.miscCategory = ZCategory.miscellaneous("misc", "&8Miscellaneous", CategoryIcon.of(Material.CHEST));
            this.categories.put("misc", this.miscCategory);
            this.plugin.getLogger().warning("No 'misc' category found, creating default one");
        }

        // Sort categories by priority
        this.sortedCategories = this.categories.values().stream().sorted(Comparator.comparingInt(Category::getPriority)).toList();

        this.plugin.getLogger().info("Loaded " + this.categories.size() + " categories");
    }

    private void loadCategoriesFromFile(File file) {
        try {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);

            // Check for global settings
            if (configuration.contains("settings")) {
                ConfigurationSection settings = configuration.getConfigurationSection("settings");
                if (settings != null) {
                    enabled = settings.getBoolean("enabled", true);
                }
            }

            // Load categories from the root level (each key is a category)
            for (String key : configuration.getKeys(false)) {
                if (key.equals("settings") || key.equals("dynamic-categories") || key.equals("custom-items-support")) {
                    continue;
                }

                ConfigurationSection section = configuration.getConfigurationSection(key);
                if (section != null) {
                    try {
                        Category category = loadCategory(key, section);
                        this.categories.put(key.toLowerCase(Locale.ROOT), category);

                        if (category.isMiscellaneous()) {
                            this.miscCategory = category;
                        }
                    } catch (Exception e) {
                        this.plugin.getLogger().log(Level.WARNING, "Failed to load category '" + key + "' from " + file.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to load categories from " + file.getName(), e);
        }
    }

    private Category loadCategory(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        List<String> description = section.getStringList("description");
        int priority = section.getInt("priority", 100);

        // Load icon
        CategoryIcon icon = loadIcon(section.getConfigurationSection("icon"));

        // Check if this is the misc category (no rules defined)
        List<Map<?, ?>> rulesMapList = section.getMapList("rules");
        if (rulesMapList.isEmpty() && id.equalsIgnoreCase("misc")) {
            return ZCategory.miscellaneous(id, displayName, icon);
        }

        // Load rules
        List<Rule> rules = loadRules(rulesMapList);

        return new ZCategory(id, displayName, description, priority, rules, false, icon);
    }

    private CategoryIcon loadIcon(ConfigurationSection iconSection) {
        if (iconSection == null) {
            return CategoryIcon.defaultIcon();
        }

        String materialName = iconSection.getString("material", "CHEST");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' in category icon, using CHEST");
            material = Material.CHEST;
        }

        int customModelData = iconSection.getInt("custom-model-data", 0);
        boolean glow = iconSection.getBoolean("glow", false);

        return CategoryIcon.of(material, customModelData, glow);
    }

    private List<Rule> loadRules(List<Map<?, ?>> rulesMapList) {
        return ruleLoaderRegistry.loadRulesFromList(rulesMapList);
    }

    // CategoryManager interface implementation

    @Override
    public List<Category> getCategories() {
        return sortedCategories;
    }

    @Override
    public Optional<Category> getCategory(String id) {
        return Optional.ofNullable(categories.get(id.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Category getCategoryFor(ItemStack itemStack) {
        if (itemStack == null) return miscCategory;

        ItemRuleContext context = new ZItemRuleContext(itemStack);
        for (Category category : sortedCategories) {
            if (!category.isMiscellaneous() && category.matches(context)) {
                return category;
            }
        }
        return miscCategory;
    }

    @Override
    public List<Category> getCategoriesFor(ItemStack itemStack) {
        if (itemStack == null) return List.of(miscCategory);

        ItemRuleContext context = new ZItemRuleContext(itemStack);
        List<Category> matching = sortedCategories.stream().filter(c -> !c.isMiscellaneous() && c.matches(context)).toList();

        if (matching.isEmpty()) {
            return List.of(miscCategory);
        }
        return matching;
    }

    @Override
    public boolean matches(ItemStack itemStack, Category category) {
        if (category == null) return false;
        ItemRuleContext context = new ZItemRuleContext(itemStack);
        return category.matches(context);
    }

    @Override
    public Category getMiscCategory() {
        return miscCategory;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getCategoryCount() {
        return categories.size();
    }

    @Override
    public void applyCategories(Item item) {

        if (item == null) return;

        item.getCategories().clear();

        Set<Category> categories = new HashSet<>();

        if (item instanceof AuctionItem auctionItem) {
            for (ItemStack itemStack : auctionItem.getItemStacks()) {
                var itemCategories = this.getCategoriesFor(itemStack);
                categories.addAll(itemCategories);
            }
        }

        plugin.getLogger().info("Categories " + categories);
        item.setCategories(categories);
    }
}
