package fr.maxlego08.zauctionhouse.api.storage.dto;

import java.util.Date;

/**
 * Data Transfer Object for auction item stack data.
 * <p>
 * This DTO contains the serialized ItemStack data associated with an auction item.
 *
 * @param id         the unique identifier for this record
 * @param item_id    the ID of the parent item this stack belongs to
 * @param itemstack  the Base64-encoded ItemStack data
 * @param created_at timestamp when the record was created
 * @param updated_at timestamp when the record was last updated
 */
public record AuctionItemDTO(int id, int item_id, String itemstack, Date created_at, Date updated_at) {
}
