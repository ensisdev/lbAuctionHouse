package fr.maxlego08.zauctionhouse.placeholder.placeholders;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.placeholders.Placeholder;
import fr.maxlego08.zauctionhouse.api.placeholders.PlaceholderRegister;

public class GlobalPlaceholders implements PlaceholderRegister {

    @Override
    public void register(Placeholder placeholder, AuctionPlugin plugin) {

        var manager = plugin.getAuctionManager();
        var categoryManager = plugin.getCategoryManager();

        // Count only items actually shown in the auction list (available for sale and not expired),
        // so the placeholder stays consistent with the GUI list and the category counts.
        placeholder.register("listed_items", player -> String.valueOf(manager.getItems(StorageType.LISTED).stream()
                .filter(item -> item.isActivelyListed())
                .count()), "Returns the number of listed items");

        placeholder.register("category_count_", (player, args) -> {
            if (args == null || args.isEmpty()) {
                return "0";
            }

            long count = categoryManager.getItemCountForCategory(args);
            return String.valueOf(count);
        }, "Returns the number of items in a category", "<category_id>");
    }
}
