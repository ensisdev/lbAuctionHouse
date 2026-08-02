package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.category.Category;
import fr.maxlego08.zauctionhouse.api.configuration.records.BroadcastConfiguration;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import fr.maxlego08.zauctionhouse.utils.component.ComponentMessageHelper;
import org.bukkit.entity.Player;

import java.util.Set;

public class BroadcastService extends ZUtils {

    private final AuctionPlugin plugin;

    public BroadcastService(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void broadcastSell(Player seller, AuctionItem auctionItem) {
        BroadcastConfiguration config = this.plugin.getConfiguration().getBroadcast();
        if (!config.sellEnabled()) return;

        var optionService = this.plugin.getAuctionManager().getOptionService();
        String categoryName = getFirstCategoryName(auctionItem);
        String customMessage = categoryName != null ? config.categoryMessagesSell().get(categoryName) : null;

        for (Player online : this.plugin.getServer().getOnlinePlayers()) {
            if (config.excludeSeller() && online.equals(seller)) continue;
            if (!optionService.getOption(online, PlayerOption.BROADCAST_SELL)) continue;

            if (customMessage != null) {
                String resolved = resolvePlaceholders(customMessage, seller.getName(), null, auctionItem, categoryName);
                ComponentMessageHelper.componentMessage.sendMessage(online, resolved);
            } else {
                message(this.plugin, online, Message.BROADCAST_SELL,
                        "%seller%", seller.getName(),
                        "%items%", auctionItem.getItemDisplay(),
                        "%price%", auctionItem.getFormattedPrice(),
                        "%category%", categoryName != null ? categoryName : "");
            }
        }
    }

    public void broadcastPurchase(Player buyer, AuctionItem auctionItem) {
        BroadcastConfiguration config = this.plugin.getConfiguration().getBroadcast();
        if (!config.purchaseEnabled()) return;

        var optionService = this.plugin.getAuctionManager().getOptionService();
        String categoryName = getFirstCategoryName(auctionItem);
        String customMessage = categoryName != null ? config.categoryMessagesPurchase().get(categoryName) : null;

        for (Player online : this.plugin.getServer().getOnlinePlayers()) {
            if (config.excludeBuyer() && online.equals(buyer)) continue;
            if (!optionService.getOption(online, PlayerOption.BROADCAST_PURCHASE)) continue;

            if (customMessage != null) {
                String resolved = resolvePlaceholders(customMessage, auctionItem.getSellerName(), buyer.getName(), auctionItem, categoryName);
                ComponentMessageHelper.componentMessage.sendMessage(online, resolved);
            } else {
                message(this.plugin, online, Message.BROADCAST_PURCHASE,
                        "%buyer%", buyer.getName(),
                        "%seller%", auctionItem.getSellerName(),
                        "%items%", auctionItem.getItemDisplay(),
                        "%price%", auctionItem.getFormattedPrice(),
                        "%category%", categoryName != null ? categoryName : "");
            }
        }
    }

    private String getFirstCategoryName(AuctionItem auctionItem) {
        Set<Category> categories = auctionItem.getCategories();
        if (categories != null && !categories.isEmpty()) {
            return categories.iterator().next().getId();
        }
        return null;
    }

    private String resolvePlaceholders(String message, String seller, String buyer, AuctionItem auctionItem, String categoryName) {
        message = message.replace("%seller%", seller != null ? seller : "");
        message = message.replace("%buyer%", buyer != null ? buyer : "");
        message = message.replace("%items%", auctionItem.getItemDisplay());
        message = message.replace("%price%", auctionItem.getFormattedPrice());
        message = message.replace("%category%", categoryName != null ? categoryName : "");
        return message;
    }
}
