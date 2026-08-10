package dev.ensisdev.lbauctionhouse.listener;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.AuctionMessages;
import dev.ensisdev.lbauctionhouse.service.TradeService;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {

    private final LbAuctionHouse plugin;
    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionMessages messages;
    private final AddonLogger logger;

    public PlayerListener(LbAuctionHouse plugin, AuctionManager manager, AuctionConfig config,
                          AuctionMessages messages, AddonLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.messages = messages;
        this.logger = logger;
    }

    public void register(org.bukkit.plugin.java.JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("PlayerListener registered.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.checkExpiredListings();
        if (!config.isNotifyOnJoin()) return;

        Player player = event.getPlayer();
        int unclaimed = manager.getUnclaimedCount(player.getUniqueId());
        if (unclaimed > 0) {
            player.sendMessage(messages.getPrefixed("collection.notify-join",
                    "count", String.valueOf(unclaimed),
                    "command", config.getLangMainCommand()));
        }

        // Bekleyen pazarlık teklifleri — çevrimdışı satıcı hatırlatması
        int pendingOffers = manager.getNegotiation().getPendingOfferCount(player.getUniqueId());
        if (pendingOffers > 0) {
            player.sendMessage(messages.getPrefixed("pazarlik.pending-offers",
                    "count", String.valueOf(pendingOffers),
                    "command", config.getLangMainCommand()));
        }

        // Yenilenebilir (süresi dolmuş) ilanlar — onaylı yenileme hatırlatması
        int expiredCount = manager.getData().getExpiredListingsByPlayer(player.getUniqueId()).size();
        if (expiredCount > 0) {
            player.sendMessage(messages.getPrefixed("auction.listing.expired-notify",
                    "count", String.valueOf(expiredCount),
                    "command", config.getLangMainCommand()));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        TradeService tradeService = plugin.getTradeService();
        if (tradeService != null) {
            tradeService.onPlayerQuit(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        TradeService tradeService = plugin.getTradeService();
        if (tradeService == null) return;

        TradeService.TradeSession session = tradeService.getSession(player);
        if (session == null) {
            // Takas GUI'si açık değilse diğer dinleyicilere bırak
            return;
        }

        Inventory inv = event.getInventory();
        if (inv == null || !inv.equals(player.getOpenInventory().getTopInventory())) return;

        String title = event.getView().getTitle();
        if (title == null || !title.startsWith("§8§lTakas")) return;

        int slot = event.getRawSlot();

        // Takas GUI'si dışındaki slotlar (alt envanter) — kendi envanterinden eşya koyabilmeli
        if (slot >= inv.getSize()) {
            // Alt envantere tıklama serbest — eşya koymak için
            return;
        }

        event.setCancelled(true);

        // Onay düğmesi (49)
        if (slot == 49) {
            session.confirm(player);
            refreshPlayerView(player);
            return;
        }

        // İptal düğmesi (50)
        if (slot == 50) {
            tradeService.closeSession(player);
            return;
        }

        // Kendi tarafındaki slotlar (0..maxSlots-1) — eşya koyabilir
        int maxSlots = session.getMaxSlots();
        if (slot >= 0 && slot < maxSlots) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                // İmleçteki eşyayı koy (imleçten 1 adet aktarılır; slot doluysa eski eşya iade edilir)
                ItemStack placed = cursor.clone();
                placed.setAmount(1);
                ItemStack displaced = session.updateSlot(player, slot, placed);
                if (displaced != null) {
                    player.getInventory().addItem(displaced);
                }
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor);
                } else {
                    event.setCursor(null);
                }
                refreshPlayerView(player);
            } else if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                // Slot'taki eşyayı geri al (tek iade — dupe önlenir)
                ItemStack taken = session.updateSlot(player, slot, null);
                if (taken != null) {
                    event.setCurrentItem(null);
                    player.getInventory().addItem(taken);
                }
                refreshPlayerView(player);
            }
            return;
        }

        // Diğer slotlar (9..9+maxSlots-1 karşı tarafın eşyaları) — sadece görüntüleme
        if (slot >= 9 && slot < 9 + maxSlots) {
            player.sendMessage("§7Bu taraf karşı oyuncunun eşyaları. Senin eşyaların sol tarafta.");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        TradeService tradeService = plugin.getTradeService();
        if (tradeService == null) return;

        TradeService.TradeSession session = tradeService.getSession(player);
        if (session == null) return;

        // Takas GUI'si kapatıldıysa ve hala oturum açıksa, eşyalar kaybolmasın — oturumu kapat
        String title = event.getView().getTitle();
        if (title == null || !title.startsWith("§8§lTakas")) return;

        // Oturumu her zaman temizle (swap sonrası da map'ten düşer — sızıntı önlenir)
        // closeSession idempotent'tir: closed bayrağı çift iadeyi engeller.
        boolean wasClosed = session.isClosed();
        tradeService.closeSession(player);
        if (!wasClosed) {
            player.sendMessage("§cTakas GUI'sini kapattın — takas iptal edildi.");
        }
    }

    private void refreshPlayerView(Player player) {
        player.updateInventory();
        Player other = null;
        TradeService tradeService = plugin.getTradeService();
        if (tradeService != null) {
            TradeService.TradeSession session = tradeService.getSession(player);
            if (session != null) {
                other = session.other(player);
            }
        }
        if (other != null && other.isOnline()) {
            other.updateInventory();
        }
    }
}