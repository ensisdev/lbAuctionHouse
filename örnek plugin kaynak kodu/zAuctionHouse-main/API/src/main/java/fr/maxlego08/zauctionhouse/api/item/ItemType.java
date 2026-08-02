package fr.maxlego08.zauctionhouse.api.item;

/**
 * Represents the type of auction listing.
 * <p>
 * Different item types have different behaviors and workflows
 * in the auction house system.
 */
public enum ItemType {

    /**
     * Standard buy-it-now auction listing.
     * Items are sold at a fixed price and purchased immediately.
     */
    AUCTION,

    /**
     * Bidding-based auction listing.
     * Players can place bids and the highest bidder wins when the auction ends.
     */
    BID,

    /**
     * Rental listing.
     * Items are rented for a period of time rather than sold permanently.
     */
    RENT

}
