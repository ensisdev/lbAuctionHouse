package fr.maxlego08.zauctionhouse.api.history;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;

/**
 * Combines a log entry with its associated auction item.
 * <p>
 * This record is used to display sales history with complete item information.
 *
 * @param log  the log entry containing transaction details
 * @param item the auction item associated with this log
 */
public record ItemLog(LogDTO log, Item item) {
}
