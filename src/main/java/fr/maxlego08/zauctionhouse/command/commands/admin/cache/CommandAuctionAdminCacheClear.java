package fr.maxlego08.zauctionhouse.command.commands.admin.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class CommandAuctionAdminCacheClear extends VCommand {

    /**
     * Cles qui portent une operation en cours et que la purge « all » ne doit jamais toucher.
     * <p>
     * {@link PlayerCacheKey#ITEM_SHOW} est la plus critique : c'est l'unique reference
     * utilisee par {@code ConfirmHelper} pour restaurer le statut d'un item en IS_*_CONFIRM.
     * L'effacer gele l'item sur tout le cluster jusqu'au balayage de maintenance.
     * Les cles SELL_* portent le panier de mise en vente : les effacer fait perdre au joueur
     * sa selection en cours. PURCHASE_ITEM est le garde anti-double-clic d'achat.
     */
    private static final EnumSet<PlayerCacheKey> PROTECTED_KEYS = EnumSet.of(
            PlayerCacheKey.ITEM_SHOW,
            PlayerCacheKey.PURCHASE_ITEM,
            PlayerCacheKey.SELL_ITEMS,
            PlayerCacheKey.SELL_PRICE,
            PlayerCacheKey.SELL_ECONOMY,
            PlayerCacheKey.SELL_EXPIRED_AT,
            PlayerCacheKey.SELL_AMOUNT
    );

    /** Cles reellement purgeables par le mot-cle « all ». */
    private static final PlayerCacheKey[] CLEARABLE_KEYS = Arrays.stream(PlayerCacheKey.values())
            .filter(key -> !PROTECTED_KEYS.contains(key))
            .toArray(PlayerCacheKey[]::new);

    public CommandAuctionAdminCacheClear(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_CLEAR);
        this.setConsoleCanUse(true);

        this.addSubCommand("clear");
        this.addRequireArg("player", (sender, args) -> {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("*");
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
            return suggestions;
        });
        this.addOptionalArg("key", (sender, args) -> {
            List<String> keys = new ArrayList<>();
            keys.add("all");
            // Les cles protegees ne sont pas proposees : « all » ne les touche pas, et les
            // demander explicitement reste possible mais reste un geste delibere.
            Arrays.stream(CLEARABLE_KEYS).map(PlayerCacheKey::name).forEach(keys::add);
            return keys;
        });
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) {
            return CommandType.SYNTAX_ERROR;
        }

        String keyName = argAsString(1);

        // Handle wildcard for all online players
        if (targetName.equals("*")) {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            int count = onlinePlayers.size();

            if (keyName == null || keyName.equalsIgnoreCase("all")) {
                for (Player target : onlinePlayers) {
                    this.auctionManager.clearPlayerCache(target, CLEARABLE_KEYS);
                }
                message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL_PLAYERS_ALL, "%count%", String.valueOf(count));
            } else {
                PlayerCacheKey key;
                try {
                    key = PlayerCacheKey.valueOf(keyName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_KEY, "%key%", keyName);
                    return CommandType.DEFAULT;
                }
                for (Player target : onlinePlayers) {
                    // Meme raison que sur la voie mono-joueur ci-dessous : vider ITEM_SHOW sans
                    // liberer la confirmation gelerait l'item en IS_*_CONFIRM sur tout le cluster.
                    if (key == PlayerCacheKey.ITEM_SHOW) {
                        ConfirmHelper.releaseConfirmation(this.plugin, target);
                    }
                    this.auctionManager.clearPlayerCache(target, key);
                }
                message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL_PLAYERS, "%key%", key.name(), "%count%", String.valueOf(count));
            }
            return CommandType.SUCCESS;
        }

        // Handle single player
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_PLAYER_NOT_ONLINE, "%player%", targetName);
            return CommandType.DEFAULT;
        }

        if (keyName == null || keyName.equalsIgnoreCase("all")) {
            this.auctionManager.clearPlayerCache(target, CLEARABLE_KEYS);
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL, "%player%", target.getName());
        } else {
            PlayerCacheKey key;
            try {
                key = PlayerCacheKey.valueOf(keyName.toUpperCase());
            } catch (IllegalArgumentException e) {
                message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_KEY, "%key%", keyName);
                return CommandType.DEFAULT;
            }
            // Un admin peut toujours demander explicitement une cle protegee. Pour ITEM_SHOW
            // on libere d'abord proprement la confirmation en cours, sinon vider la cle
            // gelerait l'item en IS_*_CONFIRM sur tout le cluster (meme defaut que la purge
            // « all » que ce correctif vient de fermer).
            if (key == PlayerCacheKey.ITEM_SHOW) {
                ConfirmHelper.releaseConfirmation(this.plugin, target);
            }
            this.auctionManager.clearPlayerCache(target, key);
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED, "%key%", key.name(), "%player%", target.getName());
        }

        return CommandType.SUCCESS;
    }
}
