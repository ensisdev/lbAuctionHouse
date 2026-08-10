package dev.ensisdev.lbauctionhouse;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.scheduler.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BroadcastService {

    private final LbAuctionHouse plugin;
    private final AuctionConfig config;
    private final CollectionEntry data;

    private int lastListingIndex = -1;
    private SchedulerAdapter.RepeatingTask broadcastTask;

    public BroadcastService(LbAuctionHouse plugin, AuctionConfig config, CollectionEntry data) {
        this.plugin = plugin;
        this.config = config;
        this.data = data;
    }

    public void broadcastAdvertisedListing(AuctionListing listing) {
        if (!config.isAdvertiseEnabled() || listing == null) return;

        String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());

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
        broadcastTask = plugin.getScheduler().runRepeatingTask(
                this::broadcastPeriodicAdvertisements,
                intervalSeconds * 20L, intervalSeconds * 20L);
    }

    /**
     * Periyodik reklam duyuru görevini durdurur.
     */
    public void stopBroadcastTask() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
    }
}
