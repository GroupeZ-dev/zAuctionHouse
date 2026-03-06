package fr.maxlego08.zauctionhouse.placeholder;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class DistantPlaceholder extends PlaceholderExpansion {

    private final AuctionPlugin plugin;
    private final Placeholder placeholder;

    public DistantPlaceholder(AuctionPlugin plugin, Placeholder placeholder) {
        this.plugin = plugin;
        this.placeholder = placeholder;
    }

    @Override
    public String getAuthor() {
        var authors = this.plugin.getDescription().getAuthors();
        return authors.isEmpty() ? "Unknown" : authors.get(0);
    }

    @Override
    public String getIdentifier() {
        return this.placeholder.getPrefix();
    }

    @Override
    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return this.placeholder.onRequest(player, params);
    }

}
