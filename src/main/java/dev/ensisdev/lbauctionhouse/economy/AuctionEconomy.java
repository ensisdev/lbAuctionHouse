package dev.ensisdev.lbauctionhouse.economy;

import dev.ensisdev.lbauctionhouse.core.addon.AuctionAPI;
import dev.ensisdev.lbauctionhouse.core.economy.EconomyManager;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Çoklu ekonomi yöneticisi — config'den seçilen ekonomi tipini kullanır.
 */
public class AuctionEconomy {

    private final AuctionEconomyProvider provider;
    private final EconomyManager vaultEconomy;
    private final String type;
    private final Logger logger;

    public AuctionEconomy(AuctionAPI api, JavaPlugin plugin) {
        this.vaultEconomy = api.getEconomyManager();
        this.logger = api.getLogger();
        this.type = plugin.getConfig().getString("economy.type", "vault");

        this.provider = switch (type.toLowerCase()) {
            case "exp", "level", "xp" -> new ExpProvider();
            case "points" -> {
                var pp = new PlayerPointsProvider();
                if (pp.isAvailable()) yield pp;
                logger.warning("PlayerPoints bulunamadı, Vault kullanılıyor.");
                yield new VaultProvider(vaultEconomy);
            }
            default -> new VaultProvider(vaultEconomy);
        };
    }

    public AuctionEconomyProvider getProvider() { return provider; }
    public boolean isEnabled() { return provider.isAvailable(); }
    public String getType() { return type; }
    public String getName() { return provider.getName(); }

    public boolean has(Player player, double amount) { return provider.has(player, amount); }
    public boolean withdraw(Player player, double amount) { return provider.withdraw(player, amount); }
    public boolean deposit(UUID playerUUID, double amount) { return provider.deposit(playerUUID, amount); }

    public double calculateNet(double gross, double taxRate) {
        return gross * (1.0 - taxRate / 100.0);
    }

    public boolean processPurchase(Player buyer, UUID sellerUUID, double price, double taxRate) {
        if (!withdraw(buyer, price)) return false;
        double net = calculateNet(price, taxRate);
        deposit(sellerUUID, net);
        return true;
    }

    public String format(double amount) {
        return provider.format(amount);
    }
}
