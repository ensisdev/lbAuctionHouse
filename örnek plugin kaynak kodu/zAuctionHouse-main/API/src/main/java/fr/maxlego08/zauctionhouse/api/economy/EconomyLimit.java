package fr.maxlego08.zauctionhouse.api.economy;

import java.math.BigDecimal;

/**
 * Defines price limits for a specific economy.
 * <p>
 * Economy limits restrict the minimum and maximum prices that players
 * can set when listing items using a particular economy provider.
 */
public interface EconomyLimit {

	/**
	 * Gets the name of the economy to which this limit applies.
	 *
	 * @return the name of the economy as a string
	 */
	String getEconomyName();

	/**
	 * Gets the maximum allowed price for this economy.
	 *
	 * @return the maximum price, or {@code null} if no maximum is set
	 */
	BigDecimal getMax();

	/**
	 * Gets the minimum allowed price for this economy.
	 *
	 * @return the minimum price, or {@code null} if no minimum is set
	 */
	BigDecimal getMin();
}