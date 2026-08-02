package dev.ensisdev.lbauctionhouse.economy;

import dev.ensisdev.lbauctionhouse.core.economy.EconomyManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Vault tabanlı ekonomi — Core'un EconomyManager'ını kullanır.
 */
public class VaultProvider implements AuctionEconomyProvider {

    private final EconomyManager economy;

    public VaultProvider(EconomyManager economy) {
        this.economy = economy;
    }

    @Override public String getName() { return "Vault"; }

    @Override
    public boolean isAvailable() { return economy.isEnabled(); }

    @Override
    public boolean has(Player player, double amount) {
        return economy.has(player.getUniqueId(), amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return economy.withdraw(player.getUniqueId(), amount);
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        return economy.deposit(playerUUID, amount);
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }
}
