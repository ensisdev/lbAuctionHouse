package dev.ensisdev.lbauctionhouse.config;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Özellik aç/kapa (feature toggle) kayıt defteri.
 * <p>
 * Tüm özellikler {@code features.yml} üzerinden okunur. Bir özellik kapatıldığında:
 * <ul>
 *     <li>İlgili komut alt komutu kaydedilmez/çalışmaz.</li>
 *     <li>İlgili tab-complete önerisi gösterilmez.</li>
 *     <li>İlgili GUI butonu/menü öğesi renderlanmaz.</li>
 *     <li>İlgili placeholder'lar boş değer döndürür.</li>
 * </ul>
 * <p>
 * Sıcak yeniden yükleme desteklenir — {@link #reload()} çağrıldığında cache sıfırlanır.
 *
 * <h2>Bilinen özellik anahtarları</h2>
 * <pre>
 *   favorites              → /ihale favorilerim, FavoritesGUI, ♥ toggle, PAPI
 *   search                 → /ihale arama, search GUI button & filter
 *   sort                   → /ihale siralama, sort GUI buttons & filter
 *   bids                   → teklif (bid) modu ve CmdBid
 *   negotiation            → pazarlık (offer) modu ve CmdNegotiate
 *   history                → /ihale gecmis ve HistoryGUI
 *   my-listings            → /ihale ilanlarim ve MyListingsGUI
 *   collection-box         → /ihale kutu ve CollectionBoxGUI
 *   trade                  → ticaret (trade) modu
 *   player-listings        → diğer oyuncu ilanları listesi GUI
 *   bundle-edit            → bundle düzenleme GUI
 *   admin-panel            → /ihaleadmin yönetim paneli
 *   ban-system             → ban/unban/banlist komutları
 *   webhook                → admin discord webhook bildirimi
 *   browse                 → ana menü (kapatılamaz — defaults true; UI için anahtar)
 *   refresh-listings       → ana menüdeki "İhaleleri Yenile" butonu
 *   bidding-feedback       → /ihale teklif (Bid) sistemi içi
 *   confirmation           → satın alma onay GUI
 *   tax                    → vergi sistemi
 *   discord-webhook        → discord webhook genel anahtarı
 *   auto-renew             → otomatik yenileme (auto-renewed mesajı)
 * </pre>
 *
 * <p>Tanımsız anahtarlar için varsayılan olarak {@code true} döner (güvenli).
 */
public final class FeatureRegistry {

    /** Kayıtlı tüm özellik anahtarları — bilinen anahtarlar burada tanımlanır. */
    public static final class Keys {
        public static final String FAVORITES        = "favorites";
        public static final String SEARCH           = "search";
        public static final String SORT             = "sort";
        public static final String BIDS             = "bids";
        public static final String NEGOTIATION      = "negotiation";
        public static final String HISTORY          = "history";
        public static final String MY_LISTINGS      = "my-listings";
        public static final String COLLECTION_BOX   = "collection-box";
        public static final String TRADE            = "trade";
        public static final String PLAYER_LISTINGS  = "player-listings";
        public static final String BUNDLE_EDIT      = "bundle-edit";
        public static final String ADMIN_PANEL      = "admin-panel";
        public static final String BAN_SYSTEM       = "ban-system";
        public static final String WEBHOOK          = "webhook";
        public static final String REFRESH_LISTINGS = "refresh-listings";
        public static final String CONFIRMATION     = "confirmation";
        public static final String TAX              = "tax";
        public static final String AUTO_RENEW       = "auto-renew";
        /** Ana menü (/ihale veya /auction) — kapatılması önerilmez, varsayılan açık. */
        public static final String BROWSE           = "browse";

        private Keys() {}
    }

    private final LbAuctionHouse plugin;
    private final Logger logger;

    /** Diskten son yüklenen ayarlar — null anahtarlar "tanımsız (varsayılan true)" anlamına gelir. */
    private Map<String, Boolean> cache;

    /** Varsa ilk yüklemede kayıt defterine bakılmadan ÜRETİLEN özellik kayıtları. */
    private final Map<String, Boolean> defaults;

    public FeatureRegistry(LbAuctionHouse plugin, Map<String, Boolean> defaults) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        // LinkedHashMap kullanıcının sırası korunur + deterministik.
        this.defaults = defaults == null ? Map.of() : defaults;
        this.cache = new LinkedHashMap<>();
        reload();
    }

    /**
     * features.yml diskten yeniden okunur, eksik anahtarlar {@link #defaults}'tan tamamlanır.
     * <p>
     * Çağrıldığında LbAuctionHouse'taki tüm canlı menüler yeniden yüklenir.
     */
    public synchronized void reload() {
        File file = new File(plugin.getDataFolder(), "features.yml");
        if (!file.exists()) {
            // JAR içinden kopyala
            try {
                plugin.saveResource("features.yml", false);
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING,
                        "features.yml JAR içinde bulunamadı — yalnızca varsayılanlar kullanılacak.", e);
                // Dosya oluşturulamasa bile en azından defaults kullanılabilsin.
                writeEmptyFeatures(file);
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Yorumları desteklemek için JAR içindeki kaynağı okuyup yaml'ye enjekte et
        try (var in = plugin.getResource("features.yml")) {
            if (in != null) {
                YamlConfiguration defaultsYaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                yaml.setDefaults(defaultsYaml);
                yaml.options().copyDefaults(true);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "features.yml kaynak yorumları okunamadı.", e);
        }

        Map<String, Boolean> next = new LinkedHashMap<>();
        // Diskten oku
        for (String key : yaml.getKeys(false)) {
            next.put(key.toLowerCase(Locale.ROOT), yaml.getBoolean(key, true));
        }
        // defaults'tan tamamla
        for (var entry : defaults.entrySet()) {
            next.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        // Bilinen anahtarlar için son kez defaults'tan emin ol
        for (String knownKey : knownKeys()) {
            next.putIfAbsent(knownKey, defaults.getOrDefault(knownKey, true));
        }

        this.cache = next;

        // Cache'i diske yaz — kullanıcı yeni eklenen anahtarları görsün
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "features.yml diske yazılamadı.", e);
        }

        logger.info("FeatureRegistry yeniden yüklendi: " + cache.size() + " özellik (" +
                String.join(", ", disabledKeys()) + " kapalı).");
    }

    private void writeEmptyFeatures(File file) {
        try {
            if (!file.getParentFile().exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
            }
            if (file.createNewFile()) {
                YamlConfiguration seed = new YamlConfiguration();
                for (var entry : defaults.entrySet()) {
                    seed.set(entry.getKey(), entry.getValue());
                }
                seed.save(file);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Boş features.yml oluşturulamadı: " + file, e);
        }
    }

    /**
     * Belirtilen özellik açık mı?
     * <p>
     * Tanımsız anahtarlar için {@code true} döner — yeni eklenen özellikler default olarak açık kalır.
     */
    public boolean isEnabled(String key) {
        if (key == null) return true;
        Boolean v = cache.get(key.toLowerCase(Locale.ROOT));
        if (v == null) {
            // Defaults'tan kontrol et
            v = defaults.get(key.toLowerCase(Locale.ROOT));
        }
        return v == null ? true : v;
    }

    /** Kısaltma. */
    public boolean is(String key) {
        return isEnabled(key);
    }

    /**
     * Belirtilen anahtarın değerini runtime'da değiştirir ve diske yazar.
     * <p>
     * Bu metod tek seferlik in-memory override değildir — kalıcı hale getirir.
     * Admin komutları tarafından kullanılır.
     *
     * @return yeni değer
     */
    public synchronized boolean set(String key, boolean enabled) {
        if (key == null || key.isBlank()) return enabled;
        String normalized = key.toLowerCase(Locale.ROOT);
        cache.put(normalized, enabled);

        File file = new File(plugin.getDataFolder(), "features.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(key, enabled);
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "FeatureRegistry değişikliği diske yazılamadı: " + key, e);
        }
        logger.info("Feature '" + key + "' → " + (enabled ? "açık" : "kapalı") +
                " (yeniden yüklemede menüler & komutlar güncellenecek).");
        return enabled;
    }

    /** Şu an kapalı olan tüm özellik anahtarları (log için). */
    public java.util.List<String> disabledKeys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var e : cache.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) out.add(e.getKey());
        }
        return out;
    }

    /** Tüm bilinen (defaults'a kayıtlı) anahtarların deterministik listesi. */
    public java.util.List<String> knownKeys() {
        java.util.List<String> out = new java.util.ArrayList<>(defaults.keySet());
        java.util.Collections.sort(out);
        return out;
    }

    /** Cache'in salt-okunur kopyası — admin GUI / PAPI için. */
    public Map<String, Boolean> snapshot() {
        return Map.copyOf(cache);
    }

    /** Toplam özellik sayısı. */
    public int size() {
        return cache.size();
    }
}
