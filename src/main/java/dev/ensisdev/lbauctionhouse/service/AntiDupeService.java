package dev.ensisdev.lbauctionhouse.service;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Anti-dupe ve eşzamanlılık koruma servisi.
 * <p>
 * Tüm listing bazlı kritik işlemler (satın alma, teklif, iptal, admin kaldırma)
 * bu servis üzerinden geçer. Servis katmanına taşınan koruma mekanizmaları:
 * <ul>
 *   <li><b>Per-listing kilit:</b> Aynı ilan üzerinde eşzamanlı işlem engellenir</li>
 *   <li><b>İşlem cooldown:</b> Aynı oyuncunun aynı ilana çok hızlı art arda işlem yapması engellenir</li>
 *   <li><b>Envanter kapasite doğrulaması:</b> Eşya transferinden önce envanter kontrolü</li>
 *   <li><b>Replay koruması:</b> Çift tık / tekrar istek ile dupe engellenir</li>
 * </ul>
 */
public class AntiDupeService {

    /** Per-listing kilit haritası */
    private final Map<UUID, Object> listingLocks = new ConcurrentHashMap<>();

    /** Devam eden işlem seti: listingId -> (transaction marker) */
    private final Map<UUID, Transaction> inProgress = new ConcurrentHashMap<>();

    /** Oyuncu bazlı son işlem zamanları (cooldown) */
    private final Map<UUID, Long> lastTransactionTime = new ConcurrentHashMap<>();

    private final AuctionConfig config;
    private final AuctionData data;
    private final AddonLogger logger;

    public AntiDupeService(AuctionConfig config, AuctionData data, AddonLogger logger) {
        this.config = config;
        this.data = data;
        this.logger = logger;
    }

    // ----------------------------------------------------------------
    // Kilitli İşlem Yürütme
    // ----------------------------------------------------------------

    /**
     * Verilen listing üzerinde kilitli bir işlem çalıştırır.
     * Aynı listing için eşzamanlı ikinci çağrı, ilk işlem bitene kadar bekler.
     *
     * @param listingId işlem yapılacak ilan
     * @param operation kilit altında çalışacak işlem
     * @param <T> dönüş tipi
     * @return işlemin sonucu
     */
    public <T> T withListingLock(UUID listingId, Supplier<T> operation) {
        Object lock = listingLocks.computeIfAbsent(listingId, k -> new Object());
        synchronized (lock) {
            try {
                return operation.get();
            } finally {
                listingLocks.remove(listingId);
            }
        }
    }

    /**
     * Void işlem varyantı.
     */
    public void withListingLock(UUID listingId, Runnable operation) {
        Object lock = listingLocks.computeIfAbsent(listingId, k -> new Object());
        synchronized (lock) {
            try {
                operation.run();
            } finally {
                listingLocks.remove(listingId);
            }
        }
    }

    // ----------------------------------------------------------------
    // İşlem Güvenliği / Replay Koruması
    // ----------------------------------------------------------------

    /**
     * Bir işlem başlatmayı dener. Eğer aynı listing üzerinde zaten aktif bir
     * işlem varsa veya oyuncu cooldown'daysa false döner.
     *
     * @param listingId ilan
     * @param player    işlemi yapan oyuncu
     * @return işlem başlatılabilir mi?
     */
    public boolean tryBeginTransaction(UUID listingId, Player player) {
        UUID playerId = player.getUniqueId();

        // Replay koruması: aynı listing için devam eden işlem var mı?
        Transaction current = inProgress.get(listingId);
        if (current != null) {
            if (current.playerId().equals(playerId)) {
                logger.warn("Anti-dupe: " + player.getName() + " aynı ilana (" + listingId + ") çift işlem denedi.");
                return false;
            }
            // Farklı oyuncu işlem yapıyor — lock bekleyecek, bu ilk kontrol
        }

        // Cooldown kontrolü — config'de anti-dupe işlem gecikmesi
        int cdMillis = config.getAntiDupeItemOperationMs();
        if (cdMillis > 0) {
            Long last = lastTransactionTime.get(playerId);
            long now = System.currentTimeMillis();
            if (last != null && (now - last) < cdMillis) {
                return false;
            }
            lastTransactionTime.put(playerId, now);
        }

        inProgress.put(listingId, new Transaction(playerId, System.nanoTime()));
        return true;
    }

    /**
     * Başlatılmış işlemi sonlandırır.
     */
    public void endTransaction(UUID listingId) {
        inProgress.remove(listingId);
    }

    // ----------------------------------------------------------------
    // Envanter / Item Doğrulama
    // ----------------------------------------------------------------

    /**
     * Oyuncunun envanterinde verilen eşya için yer var mı?
     */
    public static boolean hasInventorySpace(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) return true;
        // Yığılabilir eşyalar için mevcut stack kontrolü
        if (item == null || item.getType().isAir()) return true;
        return player.getInventory().all(item.getType()).values().stream()
                .anyMatch(stack -> stack.getAmount() + item.getAmount() <= stack.getMaxStackSize());
    }

    /**
     * İlanın hala geçerli olduğunu ve satılmadığını veritabanından taze doğrular.
     * Cache yerine her zaman DB'den okur — dupe riskini önler.
     *
     * @return taze ilan veya null
     */
    public AuctionListing getFreshListing(UUID listingId) {
        return data.getListing(listingId);
    }

    // ----------------------------------------------------------------
    // Temizlik
    // ----------------------------------------------------------------

    /**
     * Zamanaşımına uğramış transaction marker'larını temizler.
     * (örn. sunucu asenkron işlem sırasında çökmüş olabilir)
     */
    public void cleanupStaleTransactions() {
        long now = System.nanoTime();
        // Aşırı uzun süren işlemleri temizle (örn. 30s — sunucu asenkron çökme durumunda)
        long staleNanos = TimeUnit.SECONDS.toNanos(30);
        inProgress.entrySet().removeIf(e -> (now - e.getValue().startedAtNanos()) > staleNanos);
    }

    /**
     * Tüm kilitleri temizler (disable anında).
     */
    public void shutdown() {
        listingLocks.clear();
        inProgress.clear();
        lastTransactionTime.clear();
    }

    // ----------------------------------------------------------------
    // Veri Kayıtları
    // ----------------------------------------------------------------

    private record Transaction(UUID playerId, long startedAtNanos) {}
}