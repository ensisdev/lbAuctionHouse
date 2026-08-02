package dev.ensisdev.lbauctionhouse.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * PlayerPoints desteği — soft-depend.
 * Oyuncuların puanlarını para birimi olarak kullanır.
 */
public class PlayerPointsProvider implements AuctionEconomyProvider {

    private boolean available;
    private Object pointsApi;

    public PlayerPointsProvider() {
        try {
            Class<?> apiClass = Class.forName("org.black_ixx.playerpoints.PlayerPoints");
            Object plugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (plugin != null) {
                pointsApi = apiClass.getMethod("getAPI").invoke(plugin);
                available = true;
            }
        } catch (Exception e) {
            available = false;
        }
    }

    @Override public String getName() { return "Points"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public boolean has(Player player, double amount) {
        try {
            Object result = pointsApi.getClass().getMethod("look", UUID.class).invoke(pointsApi, player.getUniqueId());
            return (int) result >= (int) amount;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        try {
            Object result = pointsApi.getClass().getMethod("take", UUID.class, int.class)
                    .invoke(pointsApi, player.getUniqueId(), (int) amount);
            return (boolean) result;
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        try {
            Object result = pointsApi.getClass().getMethod("give", UUID.class, int.class)
                    .invoke(pointsApi, playerUUID, (int) amount);
            return (boolean) result;
        } catch (Exception e) { return false; }
    }

    @Override
    public String format(double amount) {
        return String.format("%,.0f Puan", amount);
    }
}
