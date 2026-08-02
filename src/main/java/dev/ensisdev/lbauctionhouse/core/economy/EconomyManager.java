package dev.ensisdev.lbauctionhouse.core.economy;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.event.EconomyBalanceUpdateEvent;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Ekonomi yöneticisi — Core ve addon'ların ekonomi işlemleri için
 * kullanacağı ana sınıf.
 * <p>
 * Vault varsa {@link VaultEconomyHook} üzerinden çalışır, yoksa
 * tüm metotlar güvenli bir şekilde false/0 döndürür.
 * <p>
 * Her bakiye değişiminde {@link EconomyBalanceUpdateEvent} fire edilir.
 * Kullanım (addon içinden):
 * <pre>
 * EconomyManager econ = api.getEconomyManager();
 * econ.deposit(playerUUID, 100.0);
 * econ.has(playerUUID, 50.0);
 * </pre>
 */
public class EconomyManager {

    private final LbAuctionHouse plugin;
    private final Logger logger;
    private final EconomyProvider provider;

    public EconomyManager(LbAuctionHouse plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.provider = new VaultEconomyHook(plugin);
    }

    public boolean isEnabled() {
        return provider.isEnabled();
    }

    public EconomyProvider getProvider() {
        return provider;
    }

    public double getBalance(UUID playerUUID) {
        double bal = provider.getBalance(playerUUID);
        return bal < 0 ? 0.0 : bal;
    }

    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    /**
     * Para ekle — başarılı olursa {@link EconomyBalanceUpdateEvent} fire eder.
     */
    public boolean deposit(UUID playerUUID, double amount) {
        if (amount <= 0 || !isEnabled()) return false;
        double oldBalance = getBalance(playerUUID);
        if (provider.deposit(playerUUID, amount)) {
            double newBalance = getBalance(playerUUID);
            Bukkit.getPluginManager().callEvent(
                    new EconomyBalanceUpdateEvent(playerUUID, oldBalance, newBalance, amount,
                            EconomyBalanceUpdateEvent.Type.DEPOSIT));
            return true;
        }
        return false;
    }

    /**
     * Para çek — başarılı olursa {@link EconomyBalanceUpdateEvent} fire eder.
     */
    public boolean withdraw(UUID playerUUID, double amount) {
        if (amount <= 0 || !isEnabled()) return false;
        double oldBalance = getBalance(playerUUID);
        if (provider.withdraw(playerUUID, amount)) {
            double newBalance = getBalance(playerUUID);
            Bukkit.getPluginManager().callEvent(
                    new EconomyBalanceUpdateEvent(playerUUID, oldBalance, newBalance, amount,
                            EconomyBalanceUpdateEvent.Type.WITHDRAW));
            return true;
        }
        return false;
    }

    public boolean has(UUID playerUUID, double amount) {
        if (amount <= 0) return true;
        return provider.has(playerUUID, amount);
    }

    public String currencyName() {
        return provider.currencyName();
    }

    public String currencySymbol() {
        return provider.currencySymbol();
    }

    public String format(double amount) {
        return provider.format(amount);
    }

    /**
     * Transfer yap — her iki taraf için de event fire eder.
     */
    public boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0 || !has(from, amount) || !isEnabled()) return false;

        double fromOld = getBalance(from);
        if (provider.withdraw(from, amount)) {
            double toOld = getBalance(to);
            if (provider.deposit(to, amount)) {
                double fromNew = getBalance(from);
                double toNew = getBalance(to);

                Bukkit.getPluginManager().callEvent(
                        new EconomyBalanceUpdateEvent(from, fromOld, fromNew, amount,
                                EconomyBalanceUpdateEvent.Type.TRANSFER_SENT));
                Bukkit.getPluginManager().callEvent(
                        new EconomyBalanceUpdateEvent(to, toOld, toNew, amount,
                                EconomyBalanceUpdateEvent.Type.TRANSFER_RECEIVED));
                return true;
            } else {
                // Rollback: alıcıya yatıramadık, gönderene geri ver
                provider.deposit(from, amount);
                return false;
            }
        }
        return false;
    }
}
