package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.log.LogType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * Data Transfer Object for auction log entries.
 * <p>
 * Log entries track all actions performed on auction items, including listings,
 * purchases, and removals. They are used for history tracking and notifications.
 *
 * @param id                the unique identifier for this log entry
 * @param log_type          the type of action that was logged
 * @param item_id           the ID of the item this log relates to
 * @param player_unique_id  the UUID of the player who performed the action
 * @param target_unique_id  the UUID of the other player involved (e.g., buyer/seller)
 * @param itemstack         the Base64-encoded ItemStack data at the time of the action
 * @param price             the price involved in the transaction
 * @param economy_name      the economy used for the transaction
 * @param additional_data   extra serialized data for the log entry
 * @param readed_at         timestamp when the log was read (null if unread)
 * @param created_at        timestamp when the log was created
 * @param updated_at        timestamp when the log was last updated
 */
public record LogDTO(int id, LogType log_type, int item_id, UUID player_unique_id, UUID target_unique_id,
                     String itemstack, BigDecimal price, String economy_name, String additional_data,
                     Date readed_at, Date created_at, Date updated_at) {

    /**
     * Checks if this log has been read by the target player.
     * @return true if the log has been read
     */
    public boolean isRead() {
        return readed_at != null;
    }
}
