package fr.maxlego08.zauctionhouse.api.history;

import java.util.Comparator;

/**
 * Defines the available sorting options for sales history.
 * <p>
 * Players can sort their sales history by date, price, or buyer name
 * in ascending or descending order.
 */
public enum HistorySortType {

    /**
     * Sort by date, newest sales first.
     */
    DATE_DESC("Newest First", Comparator.comparing((ItemLog log) -> log.log().created_at()).reversed()),

    /**
     * Sort by date, oldest sales first.
     */
    DATE_ASC("Oldest First", Comparator.comparing(log -> log.log().created_at())),

    /**
     * Sort by price, highest price first.
     */
    PRICE_DESC("Highest Price", Comparator.comparing((ItemLog log) -> log.log().price()).reversed()),

    /**
     * Sort by price, lowest price first.
     */
    PRICE_ASC("Lowest Price", Comparator.comparing(log -> log.log().price())),

    /**
     * Sort by buyer name alphabetically, A to Z.
     */
    BUYER_ASC("Buyer A-Z", Comparator.comparing(log -> log.item().getBuyerName() != null ? log.item().getBuyerName() : "")),

    /**
     * Sort by buyer name alphabetically, Z to A.
     */
    BUYER_DESC("Buyer Z-A", Comparator.comparing((ItemLog log) -> log.item().getBuyerName() != null ? log.item().getBuyerName() : "").reversed());

    private final String defaultDisplayName;
    private final Comparator<ItemLog> comparator;

    HistorySortType(String defaultDisplayName, Comparator<ItemLog> comparator) {
        this.defaultDisplayName = defaultDisplayName;
        this.comparator = comparator;
    }

    /**
     * Gets the default display name for this sort type.
     *
     * @return the human-readable display name
     */
    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    /**
     * Gets the comparator for sorting item logs according to this sort type.
     *
     * @return the comparator
     */
    public Comparator<ItemLog> getComparator() {
        return comparator;
    }
}
