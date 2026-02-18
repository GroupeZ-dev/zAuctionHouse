package fr.maxlego08.zauctionhouse.command.commands.admin.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCache;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.item.SortItem;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.command.VCommand;
import fr.maxlego08.zauctionhouse.utils.commands.CommandType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CommandAuctionAdminCacheSet extends VCommand {

    private static final Set<PlayerCacheKey> SETTABLE_KEYS = Set.of(PlayerCacheKey.CURRENT_PAGE, PlayerCacheKey.SELL_AMOUNT, PlayerCacheKey.ITEM_SORT_LOADING, PlayerCacheKey.PURCHASE_ITEM, PlayerCacheKey.SELL_EXPIRED_AT, PlayerCacheKey.SELL_PRICE, PlayerCacheKey.ITEM_SORT, PlayerCacheKey.ADMIN_TARGET_NAME);

    public CommandAuctionAdminCacheSet(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_SET);
        this.setConsoleCanUse(false);

        this.addSubCommand("set");
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addRequireArg("key", (sender, args) -> SETTABLE_KEYS.stream().map(PlayerCacheKey::name).sorted().toList());
        this.addRequireArg("value", (sender, args) -> {
            String keyArg = args.length >= 4 ? args[3] : null;
            if (keyArg == null) return List.of();
            try {
                PlayerCacheKey key = PlayerCacheKey.valueOf(keyArg.toUpperCase());
                return getValueSuggestions(key);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        });
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) {
            return CommandType.SYNTAX_ERROR;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_PLAYER_NOT_ONLINE, "%player%", targetName);
            return CommandType.DEFAULT;
        }

        String keyName = argAsString(1);
        if (keyName == null) {
            return CommandType.SYNTAX_ERROR;
        }

        PlayerCacheKey key;
        try {
            key = PlayerCacheKey.valueOf(keyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_KEY, "%key%", keyName);
            return CommandType.DEFAULT;
        }

        if (!SETTABLE_KEYS.contains(key)) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_KEY_NOT_SETTABLE, "%key%", key.name());
            return CommandType.DEFAULT;
        }

        String valueStr = argAsString(2);
        if (valueStr == null) {
            return CommandType.SYNTAX_ERROR;
        }

        Object parsedValue;
        try {
            parsedValue = parseValue(key, valueStr);
        } catch (Exception e) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_VALUE, "%value%", valueStr, "%key%", key.name());
            return CommandType.DEFAULT;
        }

        PlayerCache cache = this.auctionManager.getCache(target);
        cache.set(key, parsedValue);
        message(this.plugin, this.sender, Message.ADMIN_CACHE_SET, "%key%", key.name(), "%value%", valueStr, "%player%", target.getName());

        return CommandType.SUCCESS;
    }

    private Object parseValue(PlayerCacheKey key, String value) {
        return switch (key) {
            case CURRENT_PAGE, SELL_AMOUNT -> Integer.parseInt(value);
            case ITEM_SORT_LOADING, PURCHASE_ITEM -> Boolean.parseBoolean(value);
            case SELL_EXPIRED_AT -> Long.parseLong(value);
            case SELL_PRICE -> new BigDecimal(value);
            case ITEM_SORT -> SortItem.valueOf(value.toUpperCase());
            case ADMIN_TARGET_NAME -> value;
            default -> throw new IllegalArgumentException("Unsupported key: " + key);
        };
    }

    private List<String> getValueSuggestions(PlayerCacheKey key) {
        return switch (key) {
            case CURRENT_PAGE -> List.of("1", "2", "3");
            case SELL_AMOUNT -> List.of("1", "16", "32", "64");
            case ITEM_SORT_LOADING, PURCHASE_ITEM -> List.of("true", "false");
            case SELL_EXPIRED_AT -> List.of("0");
            case SELL_PRICE -> List.of("0", "100", "1000");
            case ITEM_SORT -> Arrays.stream(SortItem.values()).map(SortItem::name).toList();
            case ADMIN_TARGET_NAME -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            default -> List.of();
        };
    }
}