package fr.maxlego08.zauctionhouse.category;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.category.CategoryManager;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.rules.ItemRuleContext;
import fr.maxlego08.zauctionhouse.api.rules.Rule;
import fr.maxlego08.zauctionhouse.api.rules.loader.RuleLoaderRegistry;
import fr.maxlego08.zauctionhouse.rule.ZItemRuleContext;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Implementation of {@link CategoryManager}.
 * Handles loading categories from YAML files and matching items to categories.
 */
public class ZCategoryManager implements CategoryManager {

    private final AuctionPlugin plugin;
    private final RuleLoaderRegistry ruleLoaderRegistry;
    private final PerformanceDebug performanceDebug;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<String, Long> categoryCountCache = new ConcurrentHashMap<>();
    private List<Category> sortedCategories = List.of();
    private Category miscCategory;
    private boolean enabled = true;
    private String allCategoryName = "#0c1719Auction House";

    public ZCategoryManager(AuctionPlugin plugin, RuleLoaderRegistry ruleLoaderRegistry) {
        this.plugin = plugin;
        this.ruleLoaderRegistry = ruleLoaderRegistry;
        this.performanceDebug = new PerformanceDebug(plugin);
    }

    @Override
    public void loadCategories() {
        this.categories.clear();
        this.invalidateCategoryCountCache();

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
            this.miscCategory = ZCategory.miscellaneous("misc", "&8Miscellaneous");
            this.categories.put("misc", this.miscCategory);
            this.plugin.getLogger().warning("No 'misc' category found, creating default one");
        }

        // Categories are stored in load order (LinkedHashMap)
        this.sortedCategories = new ArrayList<>(this.categories.values());

        if (this.isEnabled()) {
            this.plugin.getAuctionManager().getItems(StorageType.LISTED).forEach(this::applyCategories);
        }

        this.plugin.getLogger().info("Loaded " + this.categories.size() + " categories");
    }

    private void loadCategoriesFromFile(File file) {
        try {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);

            // Check for global settings
            if (configuration.contains("settings")) {
                ConfigurationSection settings = configuration.getConfigurationSection("settings");
                if (settings != null) {
                    this.enabled = settings.getBoolean("enabled", true);
                    this.allCategoryName = settings.getString("all-category-name", "#0c1719Auction House");
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

        // Check if this is the misc category (no rules defined)
        List<Map<?, ?>> rulesMapList = section.getMapList("rules");
        if (rulesMapList.isEmpty() && id.equalsIgnoreCase("misc")) {
            return ZCategory.miscellaneous(id, displayName);
        }

        // Load rules
        List<Rule> rules = loadRules(rulesMapList);
        if (rules.isEmpty()) {
            this.plugin.getLogger().warning("No rules found for category '" + id + "'");
        }

        return new ZCategory(id, displayName, rules, false);
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
        long startTime = performanceDebug.start();

        if (item == null || !isEnabled()) {
            performanceDebug.end("applyCategories", startTime, "skipped");
            return;
        }

        item.getCategories().clear();

        Set<Category> categories = new HashSet<>();

        if (item instanceof AuctionItem auctionItem) {
            for (ItemStack itemStack : auctionItem.getItemStacks()) {
                var itemCategories = this.getCategoriesFor(itemStack);
                categories.addAll(itemCategories);
            }
        }

        item.setCategories(categories);
        performanceDebug.end("applyCategories", startTime, "itemId=" + item.getId() + ", categories=" + categories.size());
    }

    @Override
    public long getItemCountForCategory(String categoryId) {
        if (!this.isEnabled()) return 0;
        return this.categoryCountCache.computeIfAbsent(categoryId.toLowerCase(Locale.ROOT), this::computeCategoryCount);
    }

    @Override
    public void invalidateCategoryCountCache() {
        this.categoryCountCache.clear();
    }

    @Override
    public String getAllCategoryName() {
        return this.allCategoryName;
    }

    private long computeCategoryCount(String categoryId) {
        long startTime = performanceDebug.start();

        var manager = this.plugin.getAuctionManager();
        var items = manager.getItems(StorageType.LISTED);

        long count;
        if (categoryId.equals("all")) {
            count = items.size();
        } else {
            // Optimized: use simple iteration instead of stream for better performance
            int c = 0;
            for (var item : items) {
                if (item.hasCategory(categoryId)) {
                    c++;
                }
            }
            count = c;
        }

        performanceDebug.end("computeCategoryCount[" + categoryId + "]", startTime, "total=" + items.size() + ", count=" + count);
        return count;
    }
}
