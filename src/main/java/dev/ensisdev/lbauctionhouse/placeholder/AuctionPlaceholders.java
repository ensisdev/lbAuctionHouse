package dev.ensisdev.lbauctionhouse.placeholder;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI entegrasyonu — soft-depend.
 * <p>
 * Kullanılabilir placeholder'lar:
 * <ul>
 *   <li>{@code %lbauctionhouse_listings%} — toplam aktif ilan sayısı</li>
 *   <li>{@code %lbauctionhouse_my_listings%} — oyuncunun aktif ilan sayısı</li>
 *   <li>{@code %lbauctionhouse_unclaimed%} — oyuncunun bekleyen ödül sayısı</li>
 *   <li>{@code %lbauctionhouse_sold%} — oyuncunun toplam satış sayısı</li>
 *   <li>{@code %lbauctionhouse_bought%} — oyuncunun toplam satın alma sayısı</li>
 *   <li>{@code %lbauctionhouse_earned%} — oyuncunun toplam kazancı (vergi sonrası)</li>
 *   <li>{@code %lbauctionhouse_spent%} — oyuncunun toplam harcaması</li>
 *   <li>{@code %lbauctionhouse_balance%} — oyuncunun bekleyen koli bakiyesi</li>
 *   <li>{@code %lbauctionhouse_banned%} — oyuncunun banlı olup olmadığı (true/false)</li>
 *   <li>{@code %lbauctionhouse_active%} — oyuncunun aktif ilan sayısı (my_listings alias)</li>
 * </ul>
 */
public class AuctionPlaceholders extends PlaceholderExpansion {

    private final LbAuctionHouse plugin;
    private final AuctionManager manager;
    private final AddonLogger logger;

    public AuctionPlaceholders(LbAuctionHouse plugin, AuctionManager manager, AddonLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lbauctionhouse";
    }

    @Override
    public @NotNull String getAuthor() {
        return "EnsisDev, LBDev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Sunucu reload'unda yeniden yüklenmez
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        return switch (params.toLowerCase()) {
            case "listings" -> String.valueOf(manager.getActiveListingCount());
            case "my_listings", "active" -> String.valueOf(
                    manager.getPlayerListingCount(player.getUniqueId()));
            case "unclaimed" -> String.valueOf(
                    manager.getUnclaimedCount(player.getUniqueId()));
            case "balance" -> String.valueOf(
                    manager.getUnclaimedBalance(player.getUniqueId()));

            case "sold" -> String.valueOf(
                    manager.getPlayerStats(player.getUniqueId()).totalSold());
            case "bought" -> String.valueOf(
                    manager.getPlayerStats(player.getUniqueId()).totalBought());
            case "earned" -> String.valueOf(
                    manager.getPlayerStats(player.getUniqueId()).totalEarned());
            case "spent" -> String.valueOf(
                    manager.getPlayerStats(player.getUniqueId()).totalSpent());

            case "banned" -> String.valueOf(
                    manager.getData().isPlayerBanned(player.getUniqueId()));

            default -> null;
        };
    }
}