package dev.ensisdev.lbauctionhouse.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Exp/Level tabanlı ekonomi sağlayıcısı.
 * Oyuncular deneyim seviyeleriyle alışveriş yapar.
 */
public class ExpProvider implements AuctionEconomyProvider {

    @Override
    public String getName() { return "Exp"; }

    @Override
    public boolean isAvailable() { return true; }

    @Override
    public boolean has(Player player, double amount) {
        return player.getLevel() >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (player.getLevel() < amount) return false;
        player.setLevel(player.getLevel() - (int) amount);
        return true;
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) return false;
        player.setLevel(player.getLevel() + (int) amount);
        return true;
    }

    @Override
    public String format(double amount) {
        return String.format("%,.0f Seviye", amount);
    }
}
