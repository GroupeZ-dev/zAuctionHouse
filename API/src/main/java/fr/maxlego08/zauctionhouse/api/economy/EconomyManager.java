package fr.maxlego08.zauctionhouse.api.economy;

import java.util.Collection;
import java.util.Optional;

public interface EconomyManager {

    Collection<AuctionEconomy> getEconomies();

    boolean registerEconomy(AuctionEconomy economy);

    boolean removeEconomy(AuctionEconomy economy);

    Optional<AuctionEconomy> getEconomy(String economyName);

    void loadEconomies();
}
