package fr.maxlego08.zauctionhouse.configuration;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.configuration.Configuration;
import fr.maxlego08.zauctionhouse.api.messages.MessageColor;
import fr.maxlego08.zauctionhouse.utils.YamlLoader;

import java.util.ArrayList;
import java.util.List;

public class MainConfiguration extends YamlLoader implements Configuration {

    private final AuctionPlugin plugin;
    private final List<MessageColor> messageColors = new ArrayList<>();
    private boolean enableDebug;

    public MainConfiguration(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void load() {

        super.loadYamlConfirmation(this.plugin, this.plugin.getConfig());

    }

    @Override
    public boolean isEnableDebug() {
        return this.enableDebug;
    }

    @Override
    public List<MessageColor> getMessageColors() {
        return this.messageColors;
    }
}
