package fr.maxlego08.zauctionhouse.api.log;

import fr.maxlego08.zauctionhouse.api.storage.dto.LogDTO;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Represents a log entry with its associated item stacks.
 * Used for admin log display and item retrieval.
 *
 * @param log        the log DTO containing log information
 * @param itemStacks the list of item stacks associated with this log
 */
public record AdminLogItem(LogDTO log, List<ItemStack> itemStacks) {

    /**
     * Checks if this log contains multiple items.
     *
     * @return true if the log contains more than one item stack
     */
    public boolean hasMultipleItems() {
        return itemStacks != null && itemStacks.size() > 1;
    }

    /**
     * Gets the first item stack, or null if no items exist.
     *
     * @return the first item stack or null
     */
    public ItemStack getFirstItem() {
        return itemStacks != null && !itemStacks.isEmpty() ? itemStacks.getFirst() : null;
    }
}
