package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.menu.api.DialogManager;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.button.dialogs.InputButton;
import fr.maxlego08.menu.api.context.DialogRenderContext;
import fr.maxlego08.menu.api.inventory.dialog.DialogInventory;
import fr.maxlego08.menu.api.inventory.dialog.MultiActionDialogInventory;
import fr.maxlego08.menu.api.utils.PaperMetaUpdater;
import fr.maxlego08.menu.api.utils.record.dialogs.ActionButtonRecord;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchDialog {

    private static final String INPUT_KEY = "query";
    private static final int MAX_QUERY_LENGTH = 256;
    private final ZAuctionPlugin plugin;
    private final DialogManager dialogManager;
    private MultiActionDialogInventory inventory;
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();

    public SearchDialog(ZAuctionPlugin plugin) {
        this.plugin = plugin;
        this.dialogManager = plugin.getServer().getServicesManager().load(DialogManager.class);
        reload();
    }

    public void reload() {
        this.sessions.clear();
        this.inventory = null;
        if (this.dialogManager == null) return;

        File file = new File(this.plugin.getDataFolder(), "dialogs/search.yml");
        try {
            if (!file.exists()) this.plugin.saveFile("dialogs/search.yml", false);
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(file);
            if (prepareConfiguration(configuration)) configuration.save(file);
            this.dialogManager.deleteDialog(this.plugin);
            this.inventory = (MultiActionDialogInventory) this.dialogManager.loadInventory(this.plugin, file);
            Component inputLabel = MiniMessage.miniMessage().deserialize(configuration.getString("query-label", "Search query"));
            this.inventory.setInputButtons(List.of(new InputButton() {
                @Override
                public DialogInput build(DialogRenderContext<DialogInput, DialogInventory, PaperMetaUpdater, MenuPlugin> context) {
                    String query = SearchDialog.this.plugin.getAuctionManager().getCache(context.getPlayer()).get(PlayerCacheKey.SEARCH_QUERY, "");
                    String initial = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
                    return DialogInput.text(INPUT_KEY, inputLabel).initial(initial).maxLength(MAX_QUERY_LENGTH).build();
                }
            }));
            this.inventory.getActionButtons().clear();
            this.inventory.setNumberOfColumns(3);
            this.inventory.addActionButton(button(configuration.getString("search-label", "<aqua>Search"), "search"));
            this.inventory.addActionButton(button(configuration.getString("clear-label", "<yellow>Clear"), "clear"));
            this.inventory.addActionButton(button(configuration.getString("cancel-label", "<red>Cancel"), "cancel"));
        } catch (Exception exception) {
            this.inventory = null;
            this.plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to load the search dialog; using chat input", exception);
        }
    }

    static boolean prepareConfiguration(YamlConfiguration configuration) {
        boolean changed = false;
        var actions = configuration.getConfigurationSection("multi-actions");
        if (actions == null || actions.getKeys(false).isEmpty()) {
            // Older search templates omitted the action section required by zMenu's loader.
            configuration.set("multi-actions.search.label", configuration.getString("search-label", "<aqua>Search"));
            configuration.set("multi-actions.clear.label", configuration.getString("clear-label", "<yellow>Clear"));
            configuration.set("multi-actions.cancel.label", configuration.getString("cancel-label", "<red>Cancel"));
            changed = true;
        }
        if (!configuration.contains("after-action") && configuration.contains("after_action")) {
            configuration.set("after-action", configuration.getString("after_action"));
            changed = true;
        }
        if (!configuration.contains("number-of-columns") && configuration.contains("columns")) {
            configuration.set("number-of-columns", configuration.getInt("columns"));
            changed = true;
        }
        return changed;
    }

    public boolean open(Player player) {
        if (this.inventory == null || !this.plugin.isEnabled() || !player.isOnline() || this.plugin.isShuttingDown()) return false;
        this.sessions.put(player.getUniqueId(), UUID.randomUUID());
        player.closeInventory();
        this.dialogManager.openDialog(player, this.inventory);
        return true;
    }

    private ActionButtonRecord button(String label, String operation) {
        return new ActionButtonRecord(label, "", 150, (inputs, player, menuPlugin, engine, button, placeholders) -> {
            UUID session = this.sessions.get(player.getUniqueId());
            return DialogAction.customClick((response, audience) -> {
                if (!(audience instanceof Player respondingPlayer)
                        || !respondingPlayer.getUniqueId().equals(player.getUniqueId())
                        || session == null || !this.plugin.isEnabled() || this.plugin.isShuttingDown()) return;

                String value = response.getText(INPUT_KEY);
                if (value != null && value.length() > MAX_QUERY_LENGTH) return;
                this.plugin.getScheduler().runAtEntity(player, task -> {
                    if (!this.plugin.isEnabled() || !player.isOnline() || this.plugin.isShuttingDown()
                            || !this.sessions.remove(player.getUniqueId(), session)) return;
                    switch (operation) {
                        case "search" -> this.plugin.getAuctionManager().startSearch(player, value == null ? "" : value.trim());
                        case "clear" -> {
                            this.plugin.getAuctionManager().clearSearch(player);
                            this.plugin.getAuctionManager().openMainAuction(player);
                        }
                        default -> this.plugin.getAuctionManager().openMainAuction(player);
                    }
                });
            }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build());
        });
    }

    public void cancel(Player player) {
        this.sessions.remove(player.getUniqueId());
    }

    public void clear() {
        this.sessions.clear();
        if (this.dialogManager != null) this.dialogManager.deleteDialog(this.plugin);
    }

}
