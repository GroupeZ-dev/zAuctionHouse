package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.ZAuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.filter.SearchField;
import fr.maxlego08.zauctionhouse.api.filter.SearchFilterType;
import fr.maxlego08.zauctionhouse.api.filter.SearchQuery;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Locale;

public class SearchDialogService implements Listener {

    private static final String INPUT_QUERY = "query";
    private static final String INPUT_FIELD = "field";
    private static final String INPUT_MODE = "mode";
    private static final String FIELD_ALL = "all";
    private static final String MODE_CONTAINS = "contains";
    private static final String MODE_EXACT = "exact";
    private static final Key ACTION_SEARCH = Key.key("zauctionhouse", "search/apply");
    private static final Key ACTION_CLEAR = Key.key("zauctionhouse", "search/clear");
    private static final Key ACTION_CANCEL = Key.key("zauctionhouse", "search/cancel");

    private final ZAuctionPlugin plugin;

    public SearchDialogService(ZAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void openSearchDialog(Player player) {
        openSearchDialog(player, currentQuery(player));
    }

    public void openSearchDialog(Player player, String initialQuery) {
        SearchQuery initial = SearchQuery.parse(initialQuery, this.plugin.getConfiguration().getSearchFilter());
        String field = initialField(initial);
        String mode = initialMode(initial);
        String query = initial.value();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Auction Search"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(Component.text("Choose where to search, enter text, and confirm."))))
                        .inputs(List.of(
                                DialogInput.text(INPUT_QUERY, Component.text("Search text"))
                                        .width(300)
                                        .initial(query)
                                        .maxLength(128)
                                        .build(),
                                DialogInput.singleOption(INPUT_FIELD, Component.text("Field"), List.of(
                                                option(FIELD_ALL, "Everything", FIELD_ALL.equals(field)),
                                                option(SearchField.NAME.getKey(), "Name", SearchField.NAME.getKey().equals(field)),
                                                option(SearchField.LORE.getKey(), "Lore", SearchField.LORE.getKey().equals(field)),
                                                option(SearchField.MATERIAL.getKey(), "Material", SearchField.MATERIAL.getKey().equals(field)),
                                                option(SearchField.SELLER.getKey(), "Seller", SearchField.SELLER.getKey().equals(field))
                                        ))
                                        .width(300)
                                        .build(),
                                DialogInput.singleOption(INPUT_MODE, Component.text("Match"), List.of(
                                                option(MODE_CONTAINS, "Contains", MODE_CONTAINS.equals(mode)),
                                                option(MODE_EXACT, "Exact", MODE_EXACT.equals(mode))
                                        ))
                                        .width(300)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                        actionButton("Search", "Apply this search.", ACTION_SEARCH),
                        actionButton("Clear", "Clear the active search.", ACTION_CLEAR)
                ), actionButton("Cancel", "Return without changing search.", ACTION_CANCEL), 2))
        );

        player.showDialog(dialog);
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }

        Player player = connection.getPlayer();
        Key identifier = event.getIdentifier();

        if (ACTION_SEARCH.equals(identifier)) {
            handleSearch(player, event.getDialogResponseView());
        } else if (ACTION_CLEAR.equals(identifier)) {
            this.plugin.getAuctionManager().clearSearch(player);
            openAuction(player);
        } else if (ACTION_CANCEL.equals(identifier)) {
            openAuction(player);
        }
    }

    private void handleSearch(Player player, DialogResponseView responseView) {
        String query = safeText(responseView.getText(INPUT_QUERY)).trim();
        if (query.isBlank()) {
            this.plugin.getAuctionManager().startSearch(player, query);
            return;
        }

        String field = safeKey(responseView.getText(INPUT_FIELD));
        String mode = safeKey(responseView.getText(INPUT_MODE));

        SearchField searchField = SearchField.fromKey(field);
        if (searchField == null) {
            searchField = SearchField.ALL;
        }

        SearchFilterType filterType = MODE_EXACT.equalsIgnoreCase(mode)
                ? SearchFilterType.EQUALS_IGNORE_CASE
                : SearchFilterType.CONTAINS_IGNORE_CASE;
        String operator = this.plugin.getConfiguration().getSearchFilter().getOperator(filterType);

        this.plugin.getAuctionManager().startSearch(player, searchField.getKey() + " " + operator + " " + query);
    }

    private void openAuction(Player player) {
        int page = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.CURRENT_PAGE, 1);
        this.plugin.getInventoriesLoader().openInventory(player, Inventories.AUCTION, page);
    }

    private String currentQuery(Player player) {
        String query = this.plugin.getAuctionManager().getCache(player).get(PlayerCacheKey.SEARCH_QUERY);
        return query == null ? "" : query;
    }

    private String initialField(SearchQuery query) {
        return query.field() == null ? FIELD_ALL : query.field().getKey();
    }

    private String initialMode(SearchQuery query) {
        if (query.type() == SearchFilterType.EQUALS || query.type() == SearchFilterType.EQUALS_IGNORE_CASE) {
            return MODE_EXACT;
        }
        return MODE_CONTAINS;
    }

    private static SingleOptionDialogInput.OptionEntry option(String id, String label, boolean initial) {
        return SingleOptionDialogInput.OptionEntry.create(id, Component.text(label), initial);
    }

    private static ActionButton actionButton(String label, String tooltip, Key key) {
        return ActionButton.create(
                Component.text(label),
                Component.text(tooltip),
                100,
                DialogAction.customClick(key, null)
        );
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    private static String safeKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
