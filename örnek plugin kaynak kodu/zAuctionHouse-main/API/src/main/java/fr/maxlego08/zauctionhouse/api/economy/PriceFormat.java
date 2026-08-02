package fr.maxlego08.zauctionhouse.api.economy;

public enum PriceFormat {

    /**
     * Represents a raw price format.
     */
    PRICE_RAW,

    /**
     * Represents a price format without decimal places. (10000.50 -> 10000)
     */
    PRICE_WITHOUT_DECIMAL,

    /**
     * Represents a price format with decimal formatting. (10000 -> 10 000)
     */
    PRICE_WITH_DECIMAL_FORMAT,

    /**
     * Represents a price format with reduction applied. (10000 -> 10.0k)
     */
    PRICE_WITH_REDUCTION,

}