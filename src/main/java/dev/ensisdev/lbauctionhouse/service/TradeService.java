package dev.ensisdev.lbauctionhouse.service;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trade (Oyuncu-oyuncu takas) sistemi.
 * <p>
 * Oyuncular birbirine takas isteği gönderir, kabul edilirse GUI açılır.
 * Her iki taraf da eşyalarını koyar, onaylar ve takas gerçekleşir.
 * Ayrıca anti-snipe koruması için listing süre uzatma sağlar.
 */
public class TradeService {

    private final LbAuctionHouse plugin;
    private final AuctionConfig config;
    private final AuctionEconomy economy;

    /** Aktif takas istekleri: hedef -> (isteyen, son kullanma zamanı) */
    private final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();
    /** Aktif takas oturumları: her iki oyuncu -> oturum */
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    public TradeService(LbAuctionHouse plugin, AuctionConfig config, AuctionEconomy economy) {
        this.plugin = plugin;
        this.config = config;
        this.economy = economy;
    }

    // ----------------------------------------------------------------
    // Takas İsteği
    // ----------------------------------------------------------------

    /**
     * Bir oyuncuya takas isteği gönderir.
     * @return 0=başarılı, 1=hedef yok, 2=kendine istek, 3=zaten istek var, 4=cooldown
     */
    public int sendRequest(Player sender, Player target) {
        if (target == null || !target.isOnline()) return 1;
        if (sender.getUniqueId().equals(target.getUniqueId())) return 2;

        TradeRequest existing = pendingRequests.get(target.getUniqueId());
        if (existing != null && existing.sender().equals(sender.getUniqueId())) return 3;

        if (sessions.containsKey(sender.getUniqueId()) || sessions.containsKey(target.getUniqueId())) {
            sender.sendMessage("§cZaten aktif bir takas oturumunuz var.");
            return 5;
        }

        if (hasCooldown(sender)) {
            sender.sendMessage("§cTakas isteği göndermek için biraz bekleyin.");
            return 4;
        }

        long timeout = config.getTradeRequestTimeoutSeconds() * 1000L;
        pendingRequests.put(target.getUniqueId(), new TradeRequest(sender.getUniqueId(), target.getUniqueId(), System.currentTimeMillis() + timeout, System.currentTimeMillis()));
        target.sendMessage("§e" + sender.getName() + " §7sana takas isteği gönderdi! §a/ihale trade kabul §7veya §c/ihale trade reddet");
        sender.sendMessage("§aTakas isteği §e" + target.getName() + " §akişisine gönderildi.");
        return 0;
    }

    /**
     * Takas isteğini kabul eder ve GUI oturumunu başlatır.
     * @return 0=başarılı, 1=istek yok, 2=süre dolmuş, 3=hedef çevrimdışı
     */
    public int acceptRequest(Player target) {
        TradeRequest req = pendingRequests.remove(target.getUniqueId());
        if (req == null) return 1;
        if (System.currentTimeMillis() > req.expiresAt()) return 2;

        Player sender = Bukkit.getPlayer(req.sender());
        if (sender == null || !sender.isOnline()) return 3;

        TradeSession session = new TradeSession(sender, target, config.getTradeMaxSlots(), config.isTradeRequireConfirm());
        sessions.put(sender.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        session.open();
        return 0;
    }

    /**
     * Takas isteğini reddeder.
     * @return 0=başarılı, 1=istek yok
     */
    public int declineRequest(Player target) {
        TradeRequest req = pendingRequests.remove(target.getUniqueId());
        if (req == null) return 1;
        Player sender = Bukkit.getPlayer(req.sender());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage("§c" + target.getName() + " takas isteğini reddetti.");
        }
        target.sendMessage("§7Takas isteği reddedildi.");
        return 0;
    }

    // ----------------------------------------------------------------
    // Oturum Yönetimi
    // ----------------------------------------------------------------

    public TradeSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void closeSession(Player player) {
        TradeSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        sessions.remove(session.other(player).getUniqueId());
        session.close();
        player.sendMessage("§7Takas oturumu kapatıldı.");
    }

    /** Oyuncu çıkınca temizlik */
    public void onPlayerQuit(UUID uuid) {
        pendingRequests.values().removeIf(r -> r.sender().equals(uuid) || r.target().equals(uuid));
        TradeSession session = sessions.remove(uuid);
        if (session != null) {
            Player other = session.otherByUUID(uuid);
            sessions.remove(other.getUniqueId());
            session.close();
            if (other.isOnline()) other.sendMessage("§cKarşı taraf çıkış yaptı — takas iptal edildi.");
        }
    }

    // ----------------------------------------------------------------
    // Anti-Snipe: listing süre uzatma
    // ----------------------------------------------------------------

    /**
     * BID tipi ilanlarda, son dakikada gelen teklif süreyi uzatır (anti-snipe).
     * @param currentExpiresAt  mevcut bitiş zamanı (epoch ms)
     * @param timeLeftMs        kalan süre (ms)
     * @return uzatılmış yeni bitiş zamanı
     */
    public long applyAntiSnipeExtend(long currentExpiresAt, long timeLeftMs) {
        int extendSeconds = config.getAutoExtendSeconds();
        int thresholdSeconds = config.getAutoExtendThreshold();
        if (extendSeconds <= 0 || thresholdSeconds <= 0) return currentExpiresAt;

        if (timeLeftMs < thresholdSeconds * 1000L) {
            return currentExpiresAt + (extendSeconds * 1000L);
        }
        return currentExpiresAt;
    }

    // ----------------------------------------------------------------
    // Cooldown
    // ----------------------------------------------------------------

    private final Map<UUID, Long> requestCooldowns = new HashMap<>();

    private boolean hasCooldown(Player player) {
        if (!config.isCooldownEnabled()) return false;
        int cd = config.getTradeRequestCooldownSeconds();
        if (cd <= 0) return false;
        Long last = requestCooldowns.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cd * 1000L) return true;
        requestCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        return false;
    }

    // ----------------------------------------------------------------
    // Veri Kayıtları
    // ----------------------------------------------------------------

    public record TradeRequest(UUID sender, UUID target, long expiresAt, long sentAt) {}

    // ----------------------------------------------------------------
    // Takas Oturumu (GUI)
    // ----------------------------------------------------------------

    public static class TradeSession {
        private final Player playerA;
        private final Player playerB;
        private final int maxSlots;
        private final boolean requireConfirm;
        private final Map<UUID, ItemStack[]> playerItems = new HashMap<>();
        private final Map<UUID, Boolean> confirmations = new HashMap<>();
        private boolean closed = false;

        public TradeSession(Player a, Player b, int maxSlots, boolean requireConfirm) {
            this.playerA = a;
            this.playerB = b;
            this.maxSlots = Math.min(9, maxSlots);
            this.requireConfirm = requireConfirm;
            this.playerItems.put(a.getUniqueId(), new ItemStack[this.maxSlots]);
            this.playerItems.put(b.getUniqueId(), new ItemStack[this.maxSlots]);
        }

        public Player other(Player self) {
            return self.getUniqueId().equals(playerA.getUniqueId()) ? playerB : playerA;
        }

        public Player otherByUUID(UUID uuid) {
            return uuid.equals(playerA.getUniqueId()) ? playerB : playerA;
        }

        /** Takas GUI'sini açar. Her oyuncunun kendi envanteri: sol kendi, sağ karşı taraf. */
        public void open() {
            openFor(playerA);
            openFor(playerB);
            playerA.sendMessage("§aTakas başladı! Eşyalarını sol tarafa koy, onaylamak için son satırdaki §e§lONAYLA§a'ya tıkla.");
            playerB.sendMessage("§aTakas başladı! Eşyalarını sol tarafa koy, onaylamak için son satırdaki §e§lONAYLA§a'ya tıkla.");
        }

        private void openFor(Player player) {
            if (closed) return;
            Inventory inv = Bukkit.createInventory(null, 54, "§8§lTakas — §e" + other(player).getName());
            // Sol: kendi eşyaları (0..maxSlots-1)
            ItemStack[] mine = playerItems.get(player.getUniqueId());
            if (mine != null) {
                for (int i = 0; i < mine.length; i++) {
                    if (mine[i] != null) inv.setItem(i, mine[i].clone());
                }
            }
            // Sağ: karşı tarafın eşyaları (9..9+maxSlots-1)
            ItemStack[] theirs = playerItems.get(other(player).getUniqueId());
            if (theirs != null) {
                for (int i = 0; i < theirs.length; i++) {
                    if (theirs[i] != null) inv.setItem(9 + i, theirs[i].clone());
                }
            }
            // Durum çubuğu (son satır): 49 onayla, 50 iptal
            inv.setItem(49, makeButton(Material.GREEN_STAINED_GLASS_PANE, "§a§lONAYLA",
                    confirmations.getOrDefault(player.getUniqueId(), false)
                            ? "§7Zaten onayladın. Karşı taraf bekleniyor..." : "§7Tıklayınca takası onaylarsın."));
            inv.setItem(50, makeButton(Material.RED_STAINED_GLASS_PANE, "§c§lİPTAL", "§7Tıklayınca takas iptal edilir."));
            player.openInventory(inv);
        }

        private ItemStack makeButton(Material mat, String name, String lore) {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                if (lore != null && !lore.isEmpty()) {
                    meta.setLore(java.util.List.of(lore));
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        /** GUI'deki bir slotu güncellemek için (InventoryClickEvent'ten) */
        public void updateSlot(Player player, int slot, ItemStack item) {
            if (closed) return;
            // Sadece kendi tarafındaki slotlar (0..maxSlots-1)
            if (slot >= 0 && slot < maxSlots) {
                ItemStack[] mine = playerItems.get(player.getUniqueId());
                if (mine != null) {
                    mine[slot] = item == null || item.getType() == Material.AIR ? null : item.clone();
                }
                confirmations.put(player.getUniqueId(), false);
                refreshBoth();
            }
        }

        private void refreshBoth() {
            if (closed) return;
            Player a = Bukkit.getPlayer(playerA.getUniqueId());
            Player b = Bukkit.getPlayer(playerB.getUniqueId());
            if (a != null && a.isOnline()) a.updateInventory();
            if (b != null && b.isOnline()) b.updateInventory();
        }

        /** Oyuncu onay düğmesine bastı. Her iki onaylanınca takas gerçekleşir. */
        public void confirm(Player player) {
            if (closed) return;
            confirmations.put(player.getUniqueId(), true);
            player.sendMessage("§aTakası onayladın! Karşı taraf bekleniyor...");
            Player otherPlayer = other(player);
            otherPlayer.sendMessage("§e" + player.getName() + " §atakası onayladı!");
            if (requireConfirm) {
                if (Boolean.TRUE.equals(confirmations.get(playerA.getUniqueId()))
                        && Boolean.TRUE.equals(confirmations.get(playerB.getUniqueId()))) {
                    executeSwap();
                }
            } else {
                executeSwap();
            }
        }

        private void executeSwap() {
            if (closed) return;
            closed = true;

            Player a = Bukkit.getPlayer(playerA.getUniqueId());
            Player b = Bukkit.getPlayer(playerB.getUniqueId());
            if (a == null || b == null || !a.isOnline() || !b.isOnline()) {
                if (a != null && a.isOnline()) a.sendMessage("§cTakas iptal edildi (karşı taraf çevrimdışı).");
                if (b != null && b.isOnline()) b.sendMessage("§cTakas iptal edildi (karşı taraf çevrimdışı).");
                return;
            }

            ItemStack[] aItems = playerItems.get(playerA.getUniqueId());
            ItemStack[] bItems = playerItems.get(playerB.getUniqueId());

            // Boşsa iptal
            if (isEmpty(aItems) && isEmpty(bItems)) {
                a.sendMessage("§7Takas için eşya koymadınız — iptal edildi.");
                b.sendMessage("§7Takas için eşya koymadınız — iptal edildi.");
                return;
            }

            // Envanter kontrolü — taşma olursa iptal
            for (ItemStack item : bItems) {
                if (item != null && a.getInventory().firstEmpty() == -1) {
                    a.sendMessage("§cEnvanterin dolu! Takas iptal edildi.");
                    b.sendMessage("§cKarşı tarafın envanteri dolu! Takas iptal edildi.");
                    return;
                }
            }
            for (ItemStack item : aItems) {
                if (item != null && b.getInventory().firstEmpty() == -1) {
                    b.sendMessage("§cEnvanterin dolu! Takas iptal edildi.");
                    a.sendMessage("§cKarşı tarafın envanteri dolu! Takas iptal edildi.");
                    return;
                }
            }

            // Swap: A'nın eşyalarını B'ye, B'ninkileri A'ya
            for (ItemStack item : aItems) {
                if (item != null) b.getInventory().addItem(item.clone());
            }
            for (ItemStack item : bItems) {
                if (item != null) a.getInventory().addItem(item.clone());
            }

            clearItems(a);
            clearItems(b);

            a.sendMessage("§a§lTakas tamamlandı! " + b.getName() + " ile eşya değiştiniz.");
            b.sendMessage("§a§lTakas tamamlandı! " + a.getName() + " ile eşya değiştiniz.");
        }

        private boolean isEmpty(ItemStack[] items) {
            if (items == null) return true;
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) return false;
            }
            return true;
        }

        private void clearItems(Player player) {
            player.getInventory().remove(Material.AIR);
            player.updateInventory();
        }

        public int getMaxSlots() {
            return maxSlots;
        }

        public void close() {
            if (closed) return;
            closed = true;
            Player a = Bukkit.getPlayer(playerA.getUniqueId());
            Player b = Bukkit.getPlayer(playerB.getUniqueId());
            if (a != null && a.isOnline()) a.closeInventory();
            if (b != null && b.isOnline()) b.closeInventory();
        }

        public boolean isClosed() {
            return closed;
        }
    }
}