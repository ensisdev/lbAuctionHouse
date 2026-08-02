package fr.maxlego08.zauctionhouse.api.item;

/**
 * Represents the lifecycle status of an auction item.
 * <p>
 * Items transition through various states during their lifecycle in the auction house,
 * from being available for purchase to being removed or purchased.
 */
public enum ItemStatus {

    /**
     * The item is available for purchase or removal by its owner.
     */
    AVAILABLE,

    /**
     * The item is in a removal confirmation state.
     * The owner has initiated removal but hasn't confirmed yet.
     */
    IS_REMOVE_CONFIRM,

    /**
     * The item is in a purchase confirmation state.
     * A buyer has initiated purchase but hasn't confirmed yet.
     */
    IS_PURCHASE_CONFIRM,

    /**
     * The item is currently being removed from the auction house.
     * This is a transient state during the removal process.
     */
    IS_BEING_REMOVED,

    /**
     * The item is currently being purchased.
     * This is a transient state during the purchase process.
     */
    IS_BEING_PURCHASED,

    /**
     * The item has been removed from the auction house by its owner.
     * The item has been returned to the seller's inventory.
     */
    REMOVED,

    /**
     * The item has been purchased by a buyer.
     * The transaction is complete and the item awaits delivery.
     */
    PURCHASED,

    /**
     * The item has been permanently deleted from the system.
     * This state is typically used for administrative removals.
     */
    DELETED,

    ;

}
