package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * Data Transfer Object for economy transactions.
 * <p>
 * Transactions record all economy operations performed by the auction house,
 * including pending payments to sellers.
 *
 * @param id               the unique identifier for this transaction
 * @param player_unique_id the UUID of the player involved
 * @param economy_name     the economy used for this transaction
 * @param before           the player's balance before the transaction
 * @param after            the player's balance after the transaction
 * @param value            the amount transferred
 * @param status           the current status of the transaction (PENDING, COMPLETED, etc.)
 * @param created_at       timestamp when the transaction was created
 * @param updated_at       timestamp when the transaction was last updated
 */
public record TransactionDTO(int id, UUID player_unique_id, String economy_name, BigDecimal before,
                             BigDecimal after, BigDecimal value, TransactionStatus status, Date created_at,
                             Date updated_at) {
}