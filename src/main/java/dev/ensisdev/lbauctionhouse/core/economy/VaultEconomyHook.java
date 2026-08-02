package dev.ensisdev.lbauctionhouse.core.economy;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Vault API üzerinden ekonomi servisine bağlanan implementasyon.
 * <p>
 * Vault sunucuda yüklü değilse {@link #isEnabled()} {@code false} döner.
 * Vault yüklüyse otomatik olarak sağlayıcıyı (provider) tespit eder.
 */
public class VaultEconomyHook implements EconomyProvider {

    private final LbAuctionHouse plugin;
    private final Logger logger;
    private Economy vaultEconomy;
    private boolean enabled;

    public VaultEconomyHook(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = setupVault();
    }

    /**
     * Vault servis sağlayıcısını bulup kaydeder.
     * @return başarılı mı?
     */
    private boolean setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.warning("Vault bulunamadı — ekonomi servisi devre dışı.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            logger.warning("Vault kurulu ancak ekonomi provider'ı bulunamadı.");
            return false;
        }

        this.vaultEconomy = rsp.getProvider();
        logger.info("Vault ekonomi bağlandı: " + vaultEconomy.getName());
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled && vaultEconomy != null && vaultEconomy.isEnabled();
    }

    @Override
    public double getBalance(UUID playerUUID) {
        if (!isEnabled()) return -1;
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        return vaultEconomy.getBalance(player);
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean withdraw(UUID playerUUID, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean has(UUID playerUUID, double amount) {
        if (!isEnabled()) return false;
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        return vaultEconomy.has(player, amount);
    }

    @Override
    public String currencyName() {
        if (!isEnabled()) return "Unknown";
        return vaultEconomy.currencyNamePlural();
    }

    @Override
    public String currencySymbol() {
        // Vault'ta doğrudan sembol yok — currency name'in ilk harfi + opsiyonel
        String name = currencyName();
        return name.isEmpty() ? "$" : name.substring(0, 1).toUpperCase();
    }

    @Override
    public int fractionalDigits() {
        if (!isEnabled()) return 2;
        return vaultEconomy.fractionalDigits();
    }

    @Override
    public String format(double amount) {
        if (!isEnabled()) return EconomyProvider.super.format(amount);
        return vaultEconomy.format(amount);
    }
}
