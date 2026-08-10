package dev.ensisdev.lbauctionhouse;

import dev.ensisdev.lbauctionhouse.cluster.ClusterBridge;
import dev.ensisdev.lbauctionhouse.cluster.LocalClusterBridge;
import dev.ensisdev.lbauctionhouse.cluster.RedisClusterBridge;
import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmdManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.AuctionMessages;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;
import dev.ensisdev.lbauctionhouse.core.addon.AuctionAPI;
import dev.ensisdev.lbauctionhouse.core.config.ConfigManager;
import dev.ensisdev.lbauctionhouse.core.config.LanguageManager;
import dev.ensisdev.lbauctionhouse.core.data.DataManager;
import dev.ensisdev.lbauctionhouse.core.economy.EconomyManager;
import dev.ensisdev.lbauctionhouse.core.gui.MenuManager;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.gui.GUILayoutLoader;
import dev.ensisdev.lbauctionhouse.listener.PlayerListener;
import dev.ensisdev.lbauctionhouse.placeholder.AuctionPlaceholders;
import dev.ensisdev.lbauctionhouse.scheduler.SchedulerAdapter;
import dev.ensisdev.lbauctionhouse.scheduler.SchedulerAdapters;
import dev.ensisdev.lbauctionhouse.service.AntiDupeService;
import dev.ensisdev.lbauctionhouse.service.ListingCacheService;
import dev.ensisdev.lbauctionhouse.service.TradeService;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * lbAuctionHouse — bağımsız ihale (auction house) eklentisi.
 * <p>
 * lbAuctionHouse'dan bağımsızlaştırılmıştır: tüm altyapı (config, dil, veri, ekonomi,
 * GUI) plugin'in kendi {@code core} paketi içinde barındırılır. Addon değil,
 * normal bir Bukkit/Paper pluginidir.
 */
public class LbAuctionHouse extends JavaPlugin {

    private static LbAuctionHouse instance;

    private SchedulerAdapter schedulerAdapter;

    // Vendored core servisleri (lbAuctionHouse'dan bağımsız)
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private DataManager dataManager;
    private EconomyManager economyManager;
    private MenuManager menuManager;

    private AuctionAPI coreAPI;
    private AddonLogger addonLogger;
    private AuctionConfig auctionConfig;
    private AuctionMessages auctionMessages;
    private CollectionEntry auctionData;
    private AuctionEconomy auctionEconomy;
    private AuctionManager auctionManager;
    private ClusterBridge clusterBridge;
    private AuctionCmdManager auctionCmdManager;
    private GUILayoutLoader guiLayoutLoader;
    private PlayerListener playerListener;
    private BroadcastService broadcastService;
    private TradeService tradeService;
    private AntiDupeService antiDupeService;
    private ListingCacheService listingCacheService;
    private SchedulerAdapter.RepeatingTask cacheCleanupTask;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("=== lbAuctionHouse başlatılıyor ===");

        // 0) Scheduler adaptörü — Folia tespit edilir, uygun implementasyon seçilir
        this.schedulerAdapter = SchedulerAdapters.create(this);
        getLogger().info("Scheduler: " + (schedulerAdapter.isFolia() ? "Folia (region-based)" : "Bukkit/Paper (sync)"));

        // 0) İç servisler — bağımsız altyapı
        this.configManager = new ConfigManager(this);
        configManager.loadConfig();
        this.languageManager = new LanguageManager(this);
        languageManager.load();
        this.dataManager = new DataManager(this);
        dataManager.initialize();
        this.economyManager = new EconomyManager(this);
        this.menuManager = new MenuManager(this);
        menuManager.register();
        this.coreAPI = new AuctionAPI(this);
        this.addonLogger = new AddonLogger("Auction", getLogger());
        addonLogger.info("İç servisler hazır (config/lang/data/economy/menu).");

        try {
        // 1) Config — tüm yaml dosyalarını yükle
        this.auctionConfig = new AuctionConfig(this, coreAPI);
        auctionConfig.loadAll();
        addonLogger.info("[1/6] Config yüklendi.");

        // Toplu paket (fıçı) PDC anahtarını başlat (auctionConfig gerektirir)
        dev.ensisdev.lbauctionhouse.util.BundleItems.init(this);

        // Discord webhook aktifse URL'i erken doğrula — yanlış/SSRF adresleri baştan uyarı verir
        if (auctionConfig.isDiscordWebhookEnabled()) {
            String whUrl = auctionConfig.getDiscordWebhookUrl();
            if (!dev.ensisdev.lbauctionhouse.util.DiscordWebhook.isValidWebhookUrl(whUrl)) {
                addonLogger.warn("Discord webhook URL'i geçersiz veya boş! Bildirimler gönderilmeyecek. "
                        + "Beklenen format: https://discord.com/api/webhooks/<id>/<token>");
            }
        }

        // 2) Messages — LanguageManager'a kaydet
        this.auctionMessages = new AuctionMessages(this, coreAPI);
        auctionMessages.register();
        addonLogger.info("[2/6] Messages kaydedildi.");

        // 3) Data — tabloları oluştur
        this.auctionData = new CollectionEntry(this, coreAPI);
        auctionData.initTables();
        addonLogger.info("[3/6] Data katmanı hazır.");

        // 4) Economy — wrapper
        this.auctionEconomy = new AuctionEconomy(coreAPI, this);
        addonLogger.info("[4/6] Ekonomi entegrasyonu hazır.");

        // 5) Cluster — tek sunucu veya Redis
        String clusterMode = getConfig().getString("cluster.mode", "local");
        if ("redis".equalsIgnoreCase(clusterMode)) {
            var redisSection = getConfig().getConfigurationSection("cluster.redis");
            if (redisSection == null) {
                addonLogger.warn("cluster.mode=redis ancak cluster.redis bölümü yok! "
                        + "Tek sunucu moduna geçiliyor. config.yml'i kontrol edin.");
                this.clusterBridge = new LocalClusterBridge(addonLogger);
            } else {
                this.clusterBridge = new RedisClusterBridge(this, auctionData, addonLogger, redisSection);
            }
        } else {
            this.clusterBridge = new LocalClusterBridge(addonLogger);
        }
        clusterBridge.enable();
        addonLogger.info("[5/7] Cluster: " + clusterMode);

        // 6) GUILayoutLoader — gui/*.yml dosyalarını yükle (tümünü diskte oluştur)
        this.guiLayoutLoader = new GUILayoutLoader(this, auctionConfig);
        int guiCount = guiLayoutLoader.preloadAll();
        addonLogger.info("[6/7] GUI layout'ları yüklendi (" + guiCount + " dosya).");

        // 7) Anti-dupe + cache servisleri (cluster modunda çapraz sunucu kilidi için bridge verilir)
        this.antiDupeService = new AntiDupeService(auctionConfig, auctionData, addonLogger, clusterBridge);
        this.listingCacheService = new ListingCacheService(auctionData, addonLogger);

        // Cluster senkronizasyonu → yerel cache bozma: diğer sunucudaki değişiklikler
        // bu sunucunun ListingCacheService'inde stale veri bırakmasın.
        // (Her kanalda tüm cache bozulur — sıralama fiyat/teklif bazlı olduğundan
        // tek ilanın güncellenmesi bile aktif listeyi değiştirebilir.)
        clusterBridge.setCacheInvalidator((channel, listingId) -> listingCacheService.invalidateAll());

        // Periyodik cache temizliği — TTL'si dolan girişleri kalıcı olarak düşür
        // (temizlik yapılmazsa playerCountCache/listingCache vb. zamanla şişer → bellek sızıntısı)
        this.cacheCleanupTask = schedulerAdapter.runRepeatingTask(
                listingCacheService::cleanup,
                1200L, 1200L); // her 60 saniyede bir
        addonLogger.info("[7/8] AntiDupeService + ListingCacheService hazır.");

        // 8) AuctionManager — merkezi iş mantığı
        this.auctionManager = new AuctionManager(this, coreAPI, auctionData,
                auctionConfig, auctionEconomy, addonLogger, guiLayoutLoader, clusterBridge,
                antiDupeService, listingCacheService);
        addonLogger.info("[8/8] AuctionManager hazır.");

        // PlaceholderAPI — soft-depend
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AuctionPlaceholders(this, auctionManager, addonLogger).register();
            addonLogger.info("PlaceholderAPI entegrasyonu aktif.");
        }

        // Süresi geçmiş kiralamaları kontrol et (restart sonrası) — asenkron, ana thread bloklanmaz
        auctionData.getExpiredRentalsAsync().thenAccept(expired -> {
            for (var rental : expired) {
                auctionData.addToCollectionAsync(rental.sellerUUID(), "ITEM", rental.item(), 0, rental.id());
                auctionData.deleteListingAsync(rental.id());
            }
            if (!expired.isEmpty()) addonLogger.info(expired.size() + " süresi dolmuş kiralama iade edildi.");
        });

        // Komutlar — CommandMap + ayrı sınıflar
        this.auctionCmdManager = new AuctionCmdManager(this, auctionManager,
                auctionConfig, auctionMessages, addonLogger);
        auctionCmdManager.register();

        // Trade (oyuncu-oyuncu takas) sistemi
        this.tradeService = new TradeService(this, auctionConfig, auctionEconomy);
        if (auctionConfig.isTradeEnabled()) {
            addonLogger.info("Trade sistemi hazır (anti-snipe aktif).");
        }

        // Reklam duyuru görevi — periyodik actionbar duyurusu
        this.broadcastService = new BroadcastService(this, auctionConfig, auctionData);
        if (auctionConfig.isAdvertiseEnabled()) {
            int interval = Math.max(5, auctionConfig.getAdvertiseBroadcastIntervalSeconds());
            broadcastService.startBroadcastTask(interval);
            addonLogger.info("Reklam duyuru görevi başlatıldı (" + interval + "s aralık).");
        }

        this.playerListener = new PlayerListener(this, auctionManager, auctionConfig,
                auctionMessages, addonLogger);
        playerListener.register(this);

        addonLogger.info("=== lbAuctionHouse başarıyla başlatıldı ===");
        } catch (Throwable t) {
            // HİÇBİR exception sessizce yutulmaz — TAM stack trace konsola basılır.
            addonLogger.error("lbAuctionHouse başlatılırken beklenmeyen hata oluştu:", t);
            cleanupAfterFailedStart();
            Bukkit.getPluginManager().disablePlugin(this);
            throw t instanceof RuntimeException rte ? rte : new RuntimeException(t);
        }
    }

    @Override
    public void onDisable() {
        if (addonLogger != null) {
            addonLogger.info("=== lbAuctionHouse kapatılıyor ===");
        }
        if (broadcastService != null) broadcastService.stopBroadcastTask();
        if (cacheCleanupTask != null) cacheCleanupTask.cancel();
        if (antiDupeService != null) antiDupeService.shutdown();
        if (listingCacheService != null) listingCacheService.shutdown();
        if (auctionManager != null) auctionManager.shutdown();
        if (menuManager != null) menuManager.closeAll();
        if (clusterBridge != null) clusterBridge.disable(); // Redis pool + subscriber kapatılır
        if (dataManager != null) dataManager.shutdown();
        if (addonLogger != null) {
            addonLogger.info("=== lbAuctionHouse kapatıldı ===");
        }
        instance = null;
    }

    private void cleanupAfterFailedStart() {
        try { if (broadcastService != null) broadcastService.stopBroadcastTask(); } catch (Throwable ignored) {}
        try { if (cacheCleanupTask != null) cacheCleanupTask.cancel(); } catch (Throwable ignored) {}
        try { if (antiDupeService != null) antiDupeService.shutdown(); } catch (Throwable ignored) {}
        try { if (listingCacheService != null) listingCacheService.shutdown(); } catch (Throwable ignored) {}
        try { if (auctionManager != null) auctionManager.shutdown(); } catch (Throwable ignored) {}
        try { if (menuManager != null) menuManager.closeAll(); } catch (Throwable ignored) {}
        try { if (clusterBridge != null) clusterBridge.disable(); } catch (Throwable ignored) {}
        try { if (dataManager != null) dataManager.shutdown(); } catch (Throwable ignored) {}
        try {
            if (playerListener != null) {
                org.bukkit.event.HandlerList.getHandlerLists().forEach(hl -> hl.unregister(playerListener));
            }
        } catch (Throwable ignored) {}
    }

    // ---- Static ----

    public static LbAuctionHouse getInstance() {
        return instance;
    }

    /** Scheduler soyutlaması — tüm zamanlanmış görevler buradan geçer (Bukkit + Folia uyumlu). */
    public SchedulerAdapter getScheduler() {
        return schedulerAdapter;
    }

    // ---- Vendored core accessors (AuctionAPI / BaseMenu kullanır) ----

    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public DataManager getDataManager() { return dataManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public MenuManager getMenuManager() { return menuManager; }

    // ---- Domain accessors ----

    public AuctionAPI getCoreAPI() { return coreAPI; }

    /**
     * Plugin JAR dosyasının yolunu döndürür (GUILayoutLoader'ın jar içindeki
     * gui/*.yml dosyalarını tarayabilmesi için). JavaPlugin.getFile() protected
     * olduğundan public bir köprü sağlanır.
     */
    public java.io.File getPluginJarFile() {
        return getFile();
    }

    public AuctionConfig getAuctionConfig() { return auctionConfig; }
    public AuctionMessages getAuctionMessages() { return auctionMessages; }
    public CollectionEntry getAuctionData() { return auctionData; }
    public AuctionEconomy getAuctionEconomy() { return auctionEconomy; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public GUILayoutLoader getGuiLayoutLoader() { return guiLayoutLoader; }
    public AddonLogger getAddonLogger() { return addonLogger; }
    public AuctionCmdManager getAuctionCmdManager() { return auctionCmdManager; }
    public BroadcastService getBroadcastService() { return broadcastService; }
    public TradeService getTradeService() { return tradeService; }
}
