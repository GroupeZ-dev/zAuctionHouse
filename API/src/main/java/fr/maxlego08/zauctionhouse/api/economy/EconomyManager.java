package fr.maxlego08.zauctionhouse.api.economy;

import fr.maxlego08.zauctionhouse.api.item.ItemType;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EconomyManager {

    Collection<AuctionEconomy> getEconomies();

    boolean registerEconomy(AuctionEconomy economy);

    boolean removeEconomy(AuctionEconomy economy);

    Optional<AuctionEconomy> getEconomy(String economyName);

    void loadEconomies();

    AuctionEconomy getDefaultEconomy(ItemType itemType);

    DecimalFormat getPriceDecimalFormat();

    List<NumberFormatReduction> getPriceReductions();

    String format(PriceFormat priceFormat, Number number);

    String format(AuctionEconomy economy, Number number);
}
