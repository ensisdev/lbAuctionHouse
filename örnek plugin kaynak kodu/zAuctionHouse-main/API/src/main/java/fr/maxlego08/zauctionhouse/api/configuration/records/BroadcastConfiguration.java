package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public record BroadcastConfiguration(
        boolean sellEnabled,
        boolean purchaseEnabled,
        boolean excludeSeller,
        boolean excludeBuyer,
        Map<String, String> categoryMessagesSell,
        Map<String, String> categoryMessagesPurchase
) {

    public static BroadcastConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {
        boolean sellEnabled = configuration.getBoolean("broadcast.sell.enable", false);
        boolean purchaseEnabled = configuration.getBoolean("broadcast.purchase.enable", false);
        boolean excludeSeller = configuration.getBoolean("broadcast.sell.exclude-seller", true);
        boolean excludeBuyer = configuration.getBoolean("broadcast.purchase.exclude-buyer", true);

        Map<String, String> categoryMessagesSell = new HashMap<>();
        ConfigurationSection sellSection = configuration.getConfigurationSection("broadcast.category-messages.sell");
        if (sellSection != null) {
            for (String key : sellSection.getKeys(false)) {
                categoryMessagesSell.put(key, sellSection.getString(key));
            }
        }

        Map<String, String> categoryMessagesPurchase = new HashMap<>();
        ConfigurationSection purchaseSection = configuration.getConfigurationSection("broadcast.category-messages.purchase");
        if (purchaseSection != null) {
            for (String key : purchaseSection.getKeys(false)) {
                categoryMessagesPurchase.put(key, purchaseSection.getString(key));
            }
        }

        return new BroadcastConfiguration(sellEnabled, purchaseEnabled, excludeSeller, excludeBuyer, categoryMessagesSell, categoryMessagesPurchase);
    }
}
