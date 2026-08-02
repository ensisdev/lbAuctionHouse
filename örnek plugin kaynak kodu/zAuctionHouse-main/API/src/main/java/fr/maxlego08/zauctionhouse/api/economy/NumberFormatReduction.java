package fr.maxlego08.zauctionhouse.api.economy;

import java.math.BigDecimal;

/**
 * Defines a number format reduction rule for displaying large prices.
 * <p>
 * Reductions are applied to prices to make them more readable, such as
 * converting 1,000,000 to "1M" or 1,500 to "1.5K".
 *
 * @param format    the format pattern to use (e.g., "0.0")
 * @param maxAmount the maximum amount this reduction applies to
 * @param display   the suffix to append (e.g., "K", "M", "B")
 */
public record NumberFormatReduction(String format, BigDecimal maxAmount, String display) {
}