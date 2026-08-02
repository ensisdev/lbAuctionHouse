package fr.maxlego08.zauctionhouse.api.item;

import java.util.Comparator;

/**
 * Defines the available sorting options for auction items.
 * <p>
 * Players can cycle through these options to sort the auction house
 * listings according to their preference.
 */
public enum SortItem {

    /**
     * Sort by expiration date, newest first (most time remaining).
     */
    DECREASING_DATE,

    /**
     * Sort by price, highest first.
     */
    DECREASING_PRICE,

    /**
     * Sort by expiration date, oldest first (least time remaining).
     */
    ASCENDING_DATE,

    /**
     * Sort by price, lowest first.
     */
    ASCENDING_PRICE,

    ;

    /**
     * Gets the next sort option in the cycle.
     * Cycles back to the first option after the last one.
     *
     * @return the next sort option
     */
    public SortItem next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    /**
     * Gets a comparator for sorting items according to this sort option.
     *
     * @return a comparator for the specified sort order
     */
    public Comparator<Item> getComparator() {
        return switch (this) {
            case ASCENDING_DATE -> Comparator.comparing(Item::getExpiredAt);
            case DECREASING_DATE -> Comparator.comparing(Item::getExpiredAt).reversed();
            case ASCENDING_PRICE -> Comparator.comparing(Item::getPrice);
            case DECREASING_PRICE -> Comparator.comparing(Item::getPrice).reversed();
        };
    }

}
