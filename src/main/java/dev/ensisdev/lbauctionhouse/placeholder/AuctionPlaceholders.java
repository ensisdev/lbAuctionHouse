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
 *   <li>{@code %auction_listings%} — toplam aktif ilan sayısı</li>
 *   <li>{@code %auction_my_listings%} — oyuncunun aktif ilan sayısı</li>
 *   <li>{@code %auction_unclaimed%} — oyuncunun bekleyen ödül sayısı</li>
 *   <li>{@code %auction_sold%} — oyuncunun toplam satış sayısı</li>
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
        return "auction";
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
            case "my_listings" -> String.valueOf(
                    manager.getPlayerListingCount(player.getUniqueId()));
            case "unclaimed" -> String.valueOf(
                    manager.getUnclaimedCount(player.getUniqueId()));

            default -> null;
        };
    }
}
