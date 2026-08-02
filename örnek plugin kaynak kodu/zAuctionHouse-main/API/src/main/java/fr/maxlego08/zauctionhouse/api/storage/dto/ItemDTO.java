package fr.maxlego08.zauctionhouse.api.storage.dto;

import fr.maxlego08.zauctionhouse.api.item.ItemType;
import fr.maxlego08.zauctionhouse.api.item.StorageType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

/**
 * Data Transfer Object for auction item metadata.
 * <p>
 * This DTO contains all the metadata for an auction listing, including seller/buyer
 * information, pricing, and storage state.
 *
 * @param id               the unique identifier for this item
 * @param item_type        the type of auction (AUCTION, BID, RENT)
 * @param seller_unique_id the UUID of the player who listed the item
 * @param buyer_unique_id  the UUID of the player who purchased the item (null if not purchased)
 * @param price            the listing price
 * @param economy_name     the name of the economy used for this listing
 * @param storage_type     the current storage bucket (LISTED, PURCHASED, EXPIRED, DELETED)
 * @param server_name      the name of the server where the item was listed
 * @param created_at       timestamp when the item was listed
 * @param updated_at       timestamp when the item was last updated
 * @param expired_at       timestamp when the item expires
 */
public record ItemDTO(int id, ItemType item_type, UUID seller_unique_id, UUID buyer_unique_id, BigDecimal price,
                      String economy_name, StorageType storage_type, String server_name, Date created_at, Date updated_at,
                      Date expired_at) {
}
