package fr.maxlego08.zauctionhouse.premium;

import fr.maxlego08.zauctionhouse.ZAuctionPlugin;

public class PremiumFeaturesImpl implements PremiumFeatures {

    @Override
    public void register(ZAuctionPlugin plugin) {
        plugin.getLogger().info("Premium features registered for the full build.");
    }
}
