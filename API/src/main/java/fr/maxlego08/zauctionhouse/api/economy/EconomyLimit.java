package fr.maxlego08.zauctionhouse.api.economy;

import java.math.BigDecimal;

public interface EconomyLimit {

	/**
	 * Gets the name of the economy to which this limit applies.
	 *
	 * @return the name of the economy as a string.
	 */
	String getEconomyName();

	BigDecimal getMax();

	BigDecimal getMin();
}