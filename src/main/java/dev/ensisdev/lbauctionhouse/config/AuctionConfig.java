package dev.ensisdev.lbauctionhouse.config;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.addon.AuctionAPI;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auction addon'unun tüm yapılandırma dosyalarını yükleyen ve
 * erişim sağlayan sınıf.
 * <p>
 * Yüklenen dosyalar:
 * <ul>
 *   <li>config.yml — genel ayarlar, vergi, süre, limitler, blacklist</li>
 *   <li>commands.yml — komut adları ve aliaslar</li>
 *   <li>sounds.yml — GUI ses efektleri</li>
 *   <li>gui/categories.yml — kategori tanımları</li>
 * </ul>
 */
public class AuctionConfig {

    private final LbAuctionHouse plugin;
    private final AuctionAPI api;
    private final Logger logger;

    private FileConfiguration config;
    private FileConfiguration commands;
    private FileConfiguration sounds;
    private FileConfiguration categories;
    private FileConfiguration lang;

    public AuctionConfig(LbAuctionHouse plugin, AuctionAPI api) {
        this.plugin = plugin;
        this.api = api;
        this.logger = api.getLogger();
    }

    // ----------------------------------------------------------------
    // Yükleme
    // ----------------------------------------------------------------

    public void loadAll() {
        loadConfig();
        loadMaterialNames();   // config'teki material-names → ItemNames override'ları
        loadCommands();   // komut dili (command-lang) loadLanguage'dan ÖNCE yüklenmeli
        loadLanguage();
        loadSounds();
        loadCategories();
    }

    /**
     * config.yml'deki {@code material-names} eşleşmelerini ItemNames'e yükler.
     * Örn: ENCHANTING_TABLE → "Büyü Masası" (ihalede enum adı yerine gösterilir).
     */
    private void loadMaterialNames() {
        dev.ensisdev.lbauctionhouse.util.ItemNames.clearOverrides();
        ConfigurationSection sec = config.getConfigurationSection("material-names");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key.toUpperCase());
                dev.ensisdev.lbauctionhouse.util.ItemNames.setOverride(mat, sec.getString(key, ""));
            } catch (IllegalArgumentException ignored) {
                // bilinmeyen material yok sayılır
            }
        }
    }

    public void reloadAll() {
        blacklistCacheValid = false;
        loadAll();
    }

    private void loadConfig() {
        plugin.saveResource("config.yml", false);
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        migrateIfNeeded();
    }

    /**
     * Config versiyon kontrolü ve otomatik migrasyon.
     * Eski config'de eksik anahtarları varsayılan değerlerle tamamlar.
     */
    private void migrateIfNeeded() {
        int currentVersion = config.getInt("config-version", 1);
        if (currentVersion >= 3) return;

        logger.info("Config migrasyonu başlatılıyor (v" + currentVersion + " → v3)...");

        // v1 → v2: flash-sale + auto-bid + wishlist bölümleri eklendi
        if (!config.contains("flash-sale")) {
            config.set("flash-sale.enabled", false);
            config.set("flash-sale.discount-percent", 20);
            config.set("flash-sale.duration-hours", 4);
            config.set("flash-sale.max-listing-duration-hours", 24);
            config.set("flash-sale.max-per-player", 3);
            logger.info("  + flash-sale bölümü eklendi.");
        }
        if (!config.contains("auto-bid")) {
            config.set("auto-bid.enabled", true);
            config.set("auto-bid.min-increment", 10.0);
            logger.info("  + auto-bid bölümü eklendi.");
        }

        // v2 → v3: advertise (reklam) bölümü eklendi
        if (!config.contains("advertise")) {
            config.set("advertise.enabled", true);
            config.set("advertise.fee", 500.0);
            config.set("advertise.commission-percent", 10.0);
            config.set("advertise.max-per-player", 3);
            config.set("advertise.announce-on-list", true);
            config.set("advertise.broadcast-interval-seconds", 120);
            config.set("advertise.actionbar-title", "&6📢 &e{item} &7- &6{seller} &7| &e{price}₺ &7- &6/{}ihale");
            config.set("advertise.permission", "");
            logger.info("  + advertise bölümü eklendi.");
        }

        config.set("config-version", 3);
        try {
            plugin.saveConfig();
            logger.info("Config migrasyonu tamamlandı (v3).");
        } catch (Exception e) {
            // Başlangıç yolunda — hata yutulmaz, TAM stack trace basılır.
            logger.log(java.util.logging.Level.WARNING, "Config kaydedilemedi:", e);
        }
    }

    private void loadSounds() {
        this.sounds = loadYaml("sounds.yml");
    }

    private void loadCategories() {
        this.categories = loadYaml("gui/categories.yml");
    }

    /**
     * commands.yml dosyasını yükler (yoksa diske kopyalar).
     * Komut adları ve aliaslar bu dosyadan + seçili dil dosyasından gelir.
     */
    private void loadCommands() {
        if (!new File(plugin.getDataFolder(), "commands.yml").exists()) {
            plugin.saveResource("commands.yml", false);
        }
        this.commands = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "commands.yml"));
    }

    private void loadLanguage() {
        // TÜM dil dosyalarını (en, de, fr, ar, tr, ...) diske kopyala — yalnızca seçili dil değil.
        preloadAllLanguages();

        // KOMUT dili — commands.yml içindeki "command-lang" değeri.
        // "none" → dil dosyasından KOMUT çekilmez; tüm komutlar commands.yml'den gelir.
        String langCode = getCommandLangCode();
        if ("none".equalsIgnoreCase(langCode) || langCode.isEmpty()) {
            this.lang = new YamlConfiguration();  // boş — commands.yml değerleri kullanılır
            return;
        }

        File langFile = new File(plugin.getDataFolder(), "lang" + File.separator + langCode + ".yml");
        if (!langFile.exists()) {
            logger.warning("Komut dili dosyası bulunamadı: lang/" + langCode
                    + ".yml — commands.yml değerleri kullanılacak. (commands.yml → command-lang)");
        }
        this.lang = YamlConfiguration.loadConfiguration(langFile);
    }

    /**
     * JAR içindeki {@code lang/*.yml} dosyalarının TÜMÜNÜ data klasörüne kopyalar (dosya yoksa).
     * <p>
     * Böylece sunucu sahibi {@code config.yml} içindeki {@code lang:} değerini değiştirdiğinde
     * ilgili dil dosyası hazır olur — ilk açılışta yalnızca seçili dilin değil, tüm dil
     * dosyalarının diske yazılması garanti edilir.
     * <p>
     * Dosya adları hardcode edilmez — JAR'ın {@code lang/} dizini taranır.
     */
    private void preloadAllLanguages() {
        java.util.jar.JarFile jar = null;
        try {
            jar = new java.util.jar.JarFile(plugin.getPluginJarFile());
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("lang/") && name.endsWith(".yml") && !entry.isDirectory()) {
                    File target = new File(plugin.getDataFolder(), name);
                    if (!target.exists()) {
                        try {
                            plugin.saveResource(name, false);
                            logger.info("Dil dosyası oluşturuldu: " + name);
                        } catch (Exception e) {
                            // TAM stack trace — hata yutulmaz.
                            logger.log(Level.WARNING, "Dil dosyası kopyalanamadı: " + name, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "lang/*.yml dosyaları ön yüklenirken hata:", e);
        } finally {
            try { if (jar != null) jar.close(); } catch (java.io.IOException ignored) {}
        }
    }

    /**
     * Komutların çekileceği dil kodu (commands.yml → command-lang).
     * Varsayılan "none": dil dosyası kullanılmaz, tüm komutlar commands.yml'den gelir.
     * Mesaj dilinden bağımsızdır.
     */
    public String getCommandLangCode() {
        if (commands != null) {
            String code = commands.getString("command-lang", "");
            if (!code.isEmpty()) return code;
        }
        return "none";
    }

    /**
     * Mesaj dilinin çekileceği dil kodu (config.yml → lang).
     * Komut dilinden bağımsızdır.
     */
    public String getMessageLangCode() {
        return config != null ? config.getString("lang", "tr") : "tr";
    }

    /**
     * Ana komut adı: commands.yml'deki "main-command" (boş değilse) veya
     * seçili komut dili dosyasındaki değer, o da yoksa "auction".
     */
    public String getLangMainCommand() {
        if (commands != null) {
            String override = commands.getString("main-command", "");
            if (!override.isEmpty()) return override;
        }
        return lang != null ? lang.getString("main-command", "auction") : "auction";
    }

    /**
     * Ana komut aliasları: commands.yml'deki "aliases" (boş değilse) veya
     * seçili komut dili dosyasındaki değer.
     */
    public List<String> getLangAliases() {
        if (commands != null) {
            List<String> override = commands.getStringList("aliases");
            if (!override.isEmpty()) return override;
        }
        return lang != null ? lang.getStringList("aliases") : List.of("ah");
    }

    /**
     * Yönetim (admin) komutunun adı — örn: "ihaleadmin".
     * Ana komuttan BAĞIMSIZ ayrı bir komuttur. commands.yml → lang → "ihaleadmin".
     */
    public String getAdminCommand() {
        if (commands != null) {
            String override = commands.getString("admin-command", "");
            if (!override.isEmpty()) return override;
        }
        if (lang != null) {
            String fromLang = lang.getString("admin-command", "");
            if (!fromLang.isEmpty()) return fromLang;
        }
        return "ihaleadmin";
    }

    /**
     * Yönetim komutunun aliasları — örn: ["ahadmin", "yonetim"].
     */
    public List<String> getAdminAliases() {
        if (commands != null) {
            List<String> override = commands.getStringList("admin-aliases");
            if (!override.isEmpty()) return override;
        }
        if (lang != null) {
            List<String> fromLang = lang.getStringList("admin-aliases");
            if (!fromLang.isEmpty()) return fromLang;
        }
        return List.of("ahadmin", "yonetim", "yönetim");
    }

    /**
     * Kod seviyesi varsayılan sub-command isimleri — TÜRKÇE.
     * commands.yml ve dil dosyası boş olduğunda kullanılır.
     * Böylece hiçbir yapılandırma olmasa bile komutlar Türkçe çalışır.
     */
    private static final Map<String, List<String>> SUBCOMMAND_DEFAULTS = Map.ofEntries(
            Map.entry("sell",       List.of("sat", "list", "sell")),
            Map.entry("browse",     List.of("aç", "ac", "browse", "listele")),
            Map.entry("mylistings", List.of("ilanlarım", "ilanlarim", "mylistings", "my")),
            Map.entry("collect",    List.of("kutu", "collect", "al", "claim")),
            Map.entry("remove",     List.of("sil", "remove", "delete")),
            Map.entry("reload",     List.of("yenile", "reload")),
            Map.entry("admin",      List.of("yönet", "admin", "adm")),
            Map.entry("stats",      List.of("istatistik", "stats", "stat")),
            Map.entry("ban",        List.of("yasak", "ban")),
            Map.entry("search",     List.of("ara", "search")),
            Map.entry("trade",      List.of("takas", "trade")),
            Map.entry("view",       List.of("gör", "gor", "view"))
    );

    /**
     * Bir sub-command'in ASIL (birincil) adı — ilk öğe.
     * Sırasıyla: commands.yml override → seçili dil dosyası → kod varsayılanı (Türkçe).
     */
    public String getLangSubCommand(String key) {
        if (commands != null) {
            List<String> override = commands.getStringList("subcommands." + key);
            if (!override.isEmpty()) return override.get(0);
        }
        if (lang != null) {
            List<String> list = lang.getStringList("subcommands." + key);
            if (!list.isEmpty()) return list.get(0);
        }
        List<String> def = SUBCOMMAND_DEFAULTS.get(key);
        return (def != null && !def.isEmpty()) ? def.get(0) : key;
    }

    /**
     * Bir sub-command'in aliasları — listedeki ilk öğe hariç geri kalanı.
     * Sırasıyla: commands.yml override → seçili dil dosyası → kod varsayılanı (Türkçe).
     */
    public List<String> getLangSubAliases(String key) {
        List<String> all = null;
        if (commands != null) {
            List<String> override = commands.getStringList("subcommands." + key);
            if (!override.isEmpty()) all = override;
        }
        if (all == null && lang != null) {
            List<String> fromLang = lang.getStringList("subcommands." + key);
            if (!fromLang.isEmpty()) all = fromLang;
        }
        if (all == null) {
            all = SUBCOMMAND_DEFAULTS.getOrDefault(key, List.of());
        }
        return all.size() > 1 ? all.subList(1, all.size()) : List.of();
    }

    // ----------------------------------------------------------------
    // Admin alt-komutları (/<ana> admin <alt>) — commands.yml/lang'den çözülür
    // ----------------------------------------------------------------

    private static final Map<String, List<String>> ADMIN_SUB_DEFAULTS = Map.of(
            "stats",   List.of("istatistik", "stats", "stat"),
            "logs",    List.of("geçmiş", "logs", "log"),
            "clear",   List.of("temizle", "clear"),
            "remove",  List.of("sil", "remove"),
            "ban",     List.of("yasak", "ban"),
            "unban",   List.of("affet", "unban"),
            "banlist", List.of("yasaklılar", "banlist", "banliste"),
            "inspect", List.of("inspect", "incele")
    );

    /**
     * Admin alt-komutunun tüm eşleşme adlarını döndürür (isim + aliaslar).
     * commands.yml → admin.<key> (boş değilse), değilse lang dosyası, o da yoksa varsayılan.
     */
    public List<String> getAdminSubAliases(String adminKey) {
        if (commands != null) {
            List<String> override = commands.getStringList("admin." + adminKey);
            if (!override.isEmpty()) return override.stream().map(String::toLowerCase).toList();
        }
        if (lang != null) {
            List<String> fromLang = lang.getStringList("admin." + adminKey);
            if (!fromLang.isEmpty()) return fromLang.stream().map(String::toLowerCase).toList();
        }
        return ADMIN_SUB_DEFAULTS.getOrDefault(adminKey, List.of());
    }

    /**
     * Girilen girdi, belirtilen admin alt-komutunun isimlerinden biri mi?
     */
    public boolean isAdminSub(String adminKey, String input) {
        if (input == null) return false;
        return getAdminSubAliases(adminKey).contains(input.toLowerCase());
    }

    private FileConfiguration loadYaml(String path) {
        // JAR içinden varsayılanı kaydet
        if (!new File(plugin.getDataFolder(), path).exists()) {
            plugin.saveResource(path, false);
        }
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path));
    }

    // ----------------------------------------------------------------
    // Genel Ayarlar (config.yml)
    // ----------------------------------------------------------------

    public String getAddonDisplayName() {
        return config.getString("addon-display-name", "Auction");
    }

    public double getTaxRate() {
        return config.getDouble("auction.tax-rate", 5.0);
    }

    public double getMinPrice() {
        return config.getDouble("auction.min-price", 1.0);
    }

    public double getMaxPrice() {
        return config.getDouble("auction.max-price", 1_000_000.0);
    }

    public int getExpireHours() {
        return config.getInt("auction.expire-hours", 48);
    }

    public Map<Integer, Double> getDurationOptions() {
        Map<Integer, Double> options = new LinkedHashMap<>();
        ConfigurationSection sec = config.getConfigurationSection("auction.duration-options");
        if (sec == null) return options;
        for (String key : sec.getKeys(false)) {
            try {
                int hours = Integer.parseInt(key);
                options.put(hours, sec.getDouble(key, 0));
            } catch (NumberFormatException ignored) {}
        }
        return options;
    }

    public int getMaxListingsPerPlayer() {
        return config.getInt("auction.max-listings-per-player", 10);
    }

    public boolean isConfirmOnBuy() {
        return config.getBoolean("auction.confirm-on-buy", true);
    }

    public boolean isNotifyOnJoin() {
        return config.getBoolean("auction.notify-on-join", true);
    }

    public boolean isConfirmMoney() {
        return config.getBoolean("auction.confirm-money", true);
    }

    public boolean isRentalEnabled() {
        return config.getBoolean("auction.rental.enabled", false);
    }

    public int getMinRentalDays() {
        return config.getInt("auction.rental.min-duration-days", 1);
    }

    public int getMaxRentalDays() {
        return config.getInt("auction.rental.max-duration-days", 30);
    }

    public boolean isLootboxEnabled() {
        return config.getBoolean("auction.lootbox.enabled", false);
    }

    // ----------------------------------------------------------------
    // Flash Sale
    // ----------------------------------------------------------------

    public boolean isFlashSaleEnabled() {
        return config.getBoolean("flash-sale.enabled", false);
    }

    public int getFlashSaleDiscountPercent() {
        return Math.min(99, Math.max(1, config.getInt("flash-sale.discount-percent", 20)));
    }

    public int getFlashSaleDurationHours() {
        return config.getInt("flash-sale.duration-hours", 4);
    }

    public int getFlashSaleMaxDurationHours() {
        return config.getInt("flash-sale.max-listing-duration-hours", 24);
    }

    public int getFlashSaleMaxPerPlayer() {
        return config.getInt("flash-sale.max-per-player", 3);
    }

    // ----------------------------------------------------------------
    // Auto-Bid
    // ----------------------------------------------------------------

    public boolean isAutoBidEnabled() {
        return config.getBoolean("auto-bid.enabled", true);
    }

    public double getAutoBidMinIncrement() {
        return config.getDouble("auto-bid.min-increment", 10.0);
    }

    // ----------------------------------------------------------------
    // Auto-Extend
    // ----------------------------------------------------------------

    public int getAutoExtendSeconds() {
        return config.getInt("auction.auto-extend.seconds", 300);
    }

    public int getAutoExtendThreshold() {
        return config.getInt("auction.auto-extend.threshold-seconds", 300);
    }

    // ----------------------------------------------------------------
    // Auto-Renew
    // ----------------------------------------------------------------

    public int getAutoRenewMax() {
        return config.getInt("auction.auto-renew.max-times", 0);
    }

    // ----------------------------------------------------------------
    // Reklam (Advertised)
    // ----------------------------------------------------------------

    public boolean isAdvertiseEnabled() {
        return config.getBoolean("advertise.enabled", true);
    }

    public double getAdvertiseFee() {
        return config.getDouble("advertise.fee", 500.0);
    }

    public double getAdvertiseCommissionPercent() {
        return config.getDouble("advertise.commission-percent", 10.0);
    }

    public int getAdvertiseMaxPerPlayer() {
        return config.getInt("advertise.max-per-player", 3);
    }

    public boolean isAdvertiseAnnounceOnList() {
        return config.getBoolean("advertise.announce-on-list", true);
    }

    public int getAdvertiseBroadcastIntervalSeconds() {
        return config.getInt("advertise.broadcast-interval-seconds", 120);
    }

    public String getAdvertiseActionbarTitle() {
        return config.getString("advertise.actionbar-title", "&6📢 &e{item} &7- &6{seller} &7| &e{price}₺");
    }

    public String getAdvertisePermission() {
        return config.getString("advertise.permission", "");
    }

    // ----------------------------------------------------------------
    // Discord Webhook (config.yml)
    // ----------------------------------------------------------------

    public boolean isDiscordWebhookEnabled() {
        return config.getBoolean("discord-webhook.enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return config.getString("discord-webhook.url", "");
    }

    public String getDiscordWebhookUsername() {
        return config.getString("discord-webhook.username", "lbAuctionHouse");
    }

    // Sıralama seçenekleri
    public boolean isSortEnabled() {
        return config.getBoolean("sorting.enabled", true);
    }

    public List<String> getSortOptions() {
        return config.getStringList("sorting.options");
    }

    // Arama
    public boolean isSearchEnabled() {
        return config.getBoolean("search.enabled", true);
    }

    // ----------------------------------------------------------------
    // Blacklist (config.yml) — cache'li
    // ----------------------------------------------------------------

    private Set<Material> blacklistCache;
    private boolean blacklistCacheValid;

    public boolean isBlacklistEnabled() {
        return config.getBoolean("blacklist.enabled", true);
    }

    public List<Material> getBlacklistedMaterials() {
        return new ArrayList<>(getBlacklistSet());
    }

    public boolean isBlacklisted(Material material) {
        return isBlacklistEnabled() && getBlacklistSet().contains(material);
    }

    private Set<Material> getBlacklistSet() {
        if (!blacklistCacheValid) {
            Set<Material> mats = new HashSet<>();
            for (String name : config.getStringList("blacklist.materials")) {
                try { mats.add(Material.valueOf(name.toUpperCase())); }
                catch (IllegalArgumentException e) {
                    logger.warning("Blacklist'te geçersiz materyal: " + name);
                }
            }
            blacklistCache = mats;
            blacklistCacheValid = true;
        }
        return blacklistCache;
    }

    // ----------------------------------------------------------------
    // Sesler (sounds.yml)
    // ----------------------------------------------------------------

    public boolean isSoundsEnabled() {
        return sounds.getBoolean("enabled", true);
    }

    public SoundConfig getSound(String path) {
        ConfigurationSection sec = sounds.getConfigurationSection(path);
        if (sec == null) return null;
        try {
            Sound sound = Sound.valueOf(sec.getString("sound", "UI_BUTTON_CLICK").toUpperCase());
            float volume = (float) sec.getDouble("volume", 1.0);
            float pitch = (float) sec.getDouble("pitch", 1.0);
            return new SoundConfig(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record SoundConfig(Sound sound, float volume, float pitch) {}

    // ----------------------------------------------------------------
    // Kategoriler (gui/categories.yml)
    // ----------------------------------------------------------------

    public List<Category> getCategories() {
        List<Category> result = new ArrayList<>();
        ConfigurationSection sec = categories.getConfigurationSection("categories");
        if (sec == null) return result;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection cat = sec.getConfigurationSection(key);
            if (cat == null) continue;
            try {
                Material icon = Material.valueOf(cat.getString("icon", "GRASS_BLOCK").toUpperCase());
                List<Material> materials = new ArrayList<>();
                for (String m : cat.getStringList("materials")) {
                    try { materials.add(Material.valueOf(m.toUpperCase())); }
                    catch (IllegalArgumentException ignored) {}
                }
                int sortPriority = cat.getInt("sort-priority", 50);
                result.add(new Category(key, cat.getString("name", key), icon, materials, sortPriority));
            } catch (Exception e) {
                logger.warning("Kategori yüklenemedi: " + key + " — " + e.getMessage());
            }
        }

        result.sort((a, b) -> Integer.compare(a.sortPriority, b.sortPriority));
        return result;
    }

    public record Category(String id, String name, Material icon, List<Material> materials, int sortPriority) {}

    // ----------------------------------------------------------------
    // Trade (config.yml)
    // ----------------------------------------------------------------

    public boolean isTradeEnabled() {
        return config.getBoolean("trade.enabled", true);
    }

    public int getTradeRequestTimeoutSeconds() {
        return config.getInt("trade.request-timeout-seconds", 30);
    }

    public int getTradeRequestCooldownSeconds() {
        return config.getInt("trade.request-cooldown-seconds", 10);
    }

    public int getTradeMaxSlots() {
        return Math.min(27, Math.max(1, config.getInt("trade.max-slots", 9)));
    }

    public boolean isTradeRequireConfirm() {
        return config.getBoolean("trade.require-confirm", true);
    }

    // ----------------------------------------------------------------
    // Stats & Charts (config.yml)
    // ----------------------------------------------------------------

    public boolean isStatsEnabled() {
        return config.getBoolean("stats.enabled", true);
    }

    public boolean isStatsChartEnabled() {
        return config.getBoolean("stats.chart.enabled", true);
    }

    public int getStatsChartDays() {
        return Math.min(90, Math.max(1, config.getInt("stats.chart.days", 30)));
    }

    // ----------------------------------------------------------------
    // Cooldown & Anti-Dupe (config.yml)
    // ----------------------------------------------------------------

    public boolean isCooldownEnabled() {
        return config.getBoolean("cooldown.enabled", true);
    }

    public int getCommandCooldown(String command) {
        return config.getInt("cooldown.commands." + command, 1);
    }

    public boolean isAntiDupeEnabled() {
        return config.getBoolean("cooldown.anti-dupe.enabled", true);
    }

    public int getAntiDupeItemOperationMs() {
        return config.getInt("cooldown.anti-dupe.item-operation-ms", 500);
    }

    // ----------------------------------------------------------------
    // Daily Rewards (config.yml)
    // ----------------------------------------------------------------

    public boolean isDailyRewardsEnabled() {
        return config.getBoolean("daily-rewards.enabled", true);
    }

    public boolean isDailyStreakBonus() {
        return config.getBoolean("daily-rewards.streak-bonus", true);
    }

    public int getDailyStreakMaxDays() {
        return config.getInt("daily-rewards.streak-max-days", 7);
    }

    /** Gün bazlı ödül: {gün: {money, items:{MAT:adet}}} */
    public Map<Integer, DailyReward> getDailyRewards() {
        Map<Integer, DailyReward> rewards = new LinkedHashMap<>();
        ConfigurationSection sec = config.getConfigurationSection("daily-rewards.rewards");
        if (sec == null) return rewards;
        for (String key : sec.getKeys(false)) {
            try {
                int day = Integer.parseInt(key);
                ConfigurationSection r = sec.getConfigurationSection(key);
                double money = r != null ? r.getDouble("money", 0) : 0;
                Map<String, Integer> items = new HashMap<>();
                if (r != null) {
                    ConfigurationSection itemsSec = r.getConfigurationSection("items");
                    if (itemsSec != null) {
                        for (String mat : itemsSec.getKeys(false)) {
                            items.put(mat.toUpperCase(), itemsSec.getInt(mat, 1));
                        }
                    }
                }
                rewards.put(day, new DailyReward(day, money, items));
            } catch (NumberFormatException ignored) {}
        }
        return rewards;
    }

    public record DailyReward(int day, double money, Map<String, Integer> items) {}

    // ----------------------------------------------------------------
    // Item Generator (config.yml)
    // ----------------------------------------------------------------

    public boolean isItemGeneratorEnabled() {
        return config.getBoolean("item-generator.enabled", true);
    }

    public int getItemGeneratorCooldownSeconds() {
        return config.getInt("item-generator.cooldown-seconds", 60);
    }

    /** Özel item şablonlarını yükler. */
    public List<ItemTemplate> getItemTemplates() {
        List<ItemTemplate> templates = new ArrayList<>();
        List<Map<?, ?>> list = config.getMapList("item-generator.templates");
        for (Map<?, ?> map : list) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;
                String id = String.valueOf(m.getOrDefault("id", ""));
                if (id.isEmpty()) continue;
                String name = String.valueOf(m.getOrDefault("name", id));
                Material material = Material.valueOf(String.valueOf(m.getOrDefault("material", "STONE")).toUpperCase());
                double cost = Double.parseDouble(String.valueOf(m.getOrDefault("cost", 0)));
                String permission = String.valueOf(m.getOrDefault("permission", ""));
                List<String> lore = new ArrayList<>();
                Object loreObj = m.get("lore");
                if (loreObj instanceof List<?> loreList) {
                    for (Object o : loreList) lore.add(String.valueOf(o));
                }
                Map<String, Integer> enchantments = new HashMap<>();
                Object enchObj = m.get("enchantments");
                if (enchObj instanceof Map<?, ?> enchMap) {
                    for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                        enchantments.put(String.valueOf(e.getKey()).toUpperCase(), Integer.parseInt(String.valueOf(e.getValue())));
                    }
                }
                templates.add(new ItemTemplate(id, name, material, lore, enchantments, cost, permission));
            } catch (Exception e) {
                logger.warning("Item template yüklenemedi: " + e.getMessage());
            }
        }
        return templates;
    }

    public record ItemTemplate(String id, String name, Material material, List<String> lore,
                               Map<String, Integer> enchantments, double cost, String permission) {}
}
