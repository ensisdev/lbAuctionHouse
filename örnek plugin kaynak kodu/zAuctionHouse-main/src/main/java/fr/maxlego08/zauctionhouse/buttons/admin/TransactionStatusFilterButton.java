package fr.maxlego08.zauctionhouse.buttons.admin;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.transaction.TransactionStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;

public class TransactionStatusFilterButton extends Button {

    private final AuctionPlugin plugin;
    private final String enableText;
    private final String disableText;
    private final List<TransactionStatus> statuses;
    private final Map<TransactionStatus, String> statusNames;
    private final String allStatusesName;

    public TransactionStatusFilterButton(AuctionPlugin plugin, String enableText, String disableText,
                                         List<TransactionStatus> statuses, Map<TransactionStatus, String> statusNames, String allStatusesName) {
        this.plugin = plugin;
        this.enableText = enableText;
        this.disableText = disableText;
        this.statuses = statuses;
        this.statusNames = statusNames;
        this.allStatusesName = allStatusesName;
    }

    @Override
    public boolean isPermanent() {
        return true;
    }

    @Override
    public ItemStack getCustomItemStack(@NotNull Player player, boolean useCache, @NotNull Placeholders placeholders) {
        var cache = this.plugin.getAuctionManager().getCache(player);
        TransactionStatus currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER);

        // Register placeholder for ALL option
        String allStatus = (currentFilter == null ? enableText : disableText).replace("%status%", allStatusesName);
        placeholders.register("ALL", allStatus);

        // Register placeholders for each status
        for (TransactionStatus status : statuses) {
            String displayName = statusNames.getOrDefault(status, status.getDefaultDisplayName());
            String statusText = (status == currentFilter ? enableText : disableText).replace("%status%", displayName);
            placeholders.register(status.name(), statusText);
        }

        return this.getItemStack().build(player, false, placeholders);
    }

    @Override
    public void onClick(@NonNull Player player, @NonNull InventoryClickEvent event, @NonNull InventoryEngine inventory, int slot, @NonNull Placeholders placeholders) {
        super.onClick(player, event, inventory, slot, placeholders);

        var cache = this.plugin.getAuctionManager().getCache(player);
        TransactionStatus currentFilter = cache.get(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER);

        TransactionStatus nextFilter = getNextFilter(currentFilter, event.isRightClick());
        cache.set(PlayerCacheKey.ADMIN_TRANSACTIONS_STATUS_FILTER, nextFilter);
        cache.set(PlayerCacheKey.CURRENT_PAGE, 1);

        this.plugin.getInventoriesLoader().getInventoryManager().updateInventory(player);
    }

    private TransactionStatus getNextFilter(TransactionStatus current, boolean reverse) {
        // Build full list with null (ALL) at the beginning
        int totalSize = statuses.size() + 1; // +1 for ALL option

        int currentIndex;
        if (current == null) {
            currentIndex = 0; // ALL is at index 0
        } else {
            int statusIndex = statuses.indexOf(current);
            currentIndex = statusIndex == -1 ? 0 : statusIndex + 1;
        }

        int direction = reverse ? -1 : 1;
        int nextIndex = (currentIndex + direction + totalSize) % totalSize;

        if (nextIndex == 0) {
            return null; // ALL
        } else {
            return statuses.get(nextIndex - 1);
        }
    }
}
