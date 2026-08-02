package dev.ensisdev.lbauctionhouse;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BroadcastService {

    private final LbAuctionHouse plugin;
    private final AuctionConfig config;
    private final AuctionData data;

    private int lastListingIndex = -1;
    private int broadcastTaskId = -1;

    public BroadcastService(LbAuctionHouse plugin, AuctionConfig config, AuctionData data) {
        this.plugin = plugin;
        this.config = config;
        this.data = data;
    }

    public void broadcastAdvertisedListing(AuctionListing listing) {
        if (!config.isAdvertiseEnabled() || listing == null) return;

        String itemName = listing.item().getItemMeta().hasDisplayName()
                ? listing.item().getItemMeta().getDisplayName()
                : listing.item().getType().name();

        String msg = config.getAdvertiseActionbarTitle()
                .replace("{item}", itemName)
                .replace("{seller}", listing.sellerName())
                .replace("{price}", String.valueOf(listing.price()));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!config.getAdvertisePermission().isEmpty()
                    && !player.hasPermission(config.getAdvertisePermission())) continue;
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', msg));
        }
    }

    /**
     * Periyodik duyuru görevi — aktif reklamlı ilanlardan birini seçip
     * actionbar ile tüm uygun oyunculara gösterir.
     */
    public void broadcastPeriodicAdvertisements() {
        if (!config.isAdvertiseEnabled()) return;

        List<AuctionListing> advertised = data.getActiveAdvertisedListings();
        if (advertised.isEmpty()) return;

        // Aynı ilanı arka arkaya göstermemek için döngüsel seçim
        int index = ThreadLocalRandom.current().nextInt(advertised.size());
        if (advertised.size() > 1 && index == lastListingIndex) {
            index = (index + 1) % advertised.size();
        }
        lastListingIndex = index;

        broadcastAdvertisedListing(advertised.get(index));
    }

    /**
     * Periyodik reklam duyuru görevini başlatır.
     * @param intervalSeconds kaç saniyede bir duyuru yapılacağı
     */
    public void startBroadcastTask(int intervalSeconds) {
        stopBroadcastTask();
        broadcastTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin, this::broadcastPeriodicAdvertisements,
                intervalSeconds * 20L, intervalSeconds * 20L);
    }

    /**
     * Periyodik reklam duyuru görevini durdurur.
     */
    public void stopBroadcastTask() {
        if (broadcastTaskId != -1) {
            Bukkit.getScheduler().cancelTask(broadcastTaskId);
            broadcastTaskId = -1;
        }
    }
}
