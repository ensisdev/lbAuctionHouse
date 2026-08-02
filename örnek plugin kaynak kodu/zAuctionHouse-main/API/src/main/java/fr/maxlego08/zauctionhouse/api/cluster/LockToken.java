package fr.maxlego08.zauctionhouse.api.cluster;

import fr.maxlego08.zauctionhouse.api.item.Item;

/**
 * Represents a distributed lock token for cluster synchronization.
 * <p>
 * Lock tokens are used to coordinate item operations across multiple server
 * instances in a clustered environment. When a server acquires a lock on an item,
 * it receives a token that must be used to release the lock later.
 *
 * @param value the unique identifier for this lock
 */
public record LockToken(String value) {

    /**
     * Creates a no-operation lock token for single-server deployments.
     * <p>
     * This token is used when cluster synchronization is not needed,
     * such as in local development or single-server setups.
     *
     * @return a no-op lock token
     */
    public static LockToken noop() {
        return new LockToken("NOOP");
    }

    /**
     * Creates a lock token for the specified auction item.
     *
     * @param auctionItem the item to create a lock token for
     * @return a lock token identifying the item
     */
    public static LockToken of(Item auctionItem) {
        return new LockToken("item:" + auctionItem.getId());
    }
}
