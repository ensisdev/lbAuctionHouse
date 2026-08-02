package fr.maxlego08.zauctionhouse.api.transaction;

/**
 * Represents the status of an economy transaction in the auction house.
 * <p>
 * Transactions track pending payments to sellers and their retrieval status.
 */
public enum TransactionStatus {

    /**
     * The transaction is pending and awaiting retrieval by the player.
     * This typically occurs when a seller was offline when their item sold.
     */
    PENDING("Pending"),

    /**
     * The transaction has been retrieved and the funds delivered to the player.
     */
    RETRIEVED("Retrieved");

    private final String defaultDisplayName;

    TransactionStatus(String defaultDisplayName) {
        this.defaultDisplayName = defaultDisplayName;
    }

    /**
     * Gets the default display name for this status.
     *
     * @return the human-readable display name
     */
    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }
}