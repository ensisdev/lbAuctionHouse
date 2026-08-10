package dev.ensisdev.lbauctionhouse;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.data.AuctionBid;
import dev.ensisdev.lbauctionhouse.data.AuctionLog;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.gui.AdminGUI;
import dev.ensisdev.lbauctionhouse.gui.CollectionBoxGUI;
import dev.ensisdev.lbauctionhouse.gui.ConfirmBuyGUI;
import dev.ensisdev.lbauctionhouse.gui.FavoritesGUI;
import dev.ensisdev.lbauctionhouse.gui.GUILayoutLoader;
import dev.ensisdev.lbauctionhouse.gui.HistoryGUI;
import dev.ensisdev.lbauctionhouse.gui.MainMenuGUI;
import dev.ensisdev.lbauctionhouse.gui.MyListingsGUI;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;
import dev.ensisdev.lbauctionhouse.core.addon.AuctionAPI;
import dev.ensisdev.lbauctionhouse.cluster.ClusterBridge;
import dev.ensisdev.lbauctionhouse.event.AuctionPrePurchaseEvent;
import dev.ensisdev.lbauctionhouse.event.AuctionPreSellEvent;
import dev.ensisdev.lbauctionhouse.service.AntiDupeService;
import dev.ensisdev.lbauctionhouse.service.ListingCacheService;
import dev.ensisdev.lbauctionhouse.util.BundleItems;
import dev.ensisdev.lbauctionhouse.core.event.EconomyBalanceUpdateEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Auction sisteminin merkezi iş mantığı sınıfı.
 * <p>
 * Listeleme, satın alma, iptal, sure dolumu ve claim işlemlerini yönetir.
 * GUI'leri açar, veritabanı işlemlerini AuctionData'ya devreder.
 */
public class AuctionManager {

    private final LbAuctionHouse plugin;
    private final AuctionAPI api;
    private final CollectionEntry data;
    private final AuctionConfig config;
    private final AuctionEconomy economy;
    private final AddonLogger logger;
    private final GUILayoutLoader layoutLoader;
    private final ClusterBridge cluster;
    private final BroadcastService broadcastService;
    private final AntiDupeService antiDupeService;
    private final ListingCacheService listingCache;

    private MainMenuGUI mainMenuGUI;
    private MyListingsGUI myListingsGUI;
    private ConfirmBuyGUI confirmBuyGUI;
    private CollectionBoxGUI collectionBoxGUI;
    private FavoritesGUI favoritesGUI;
    private HistoryGUI historyGUI;
    private AdminGUI adminGUI;

    private long lastExpiryCheck = 0;

    public CollectionEntry getData() { return data; }
    public AuctionAPI getApi() { return api; }

    /** Tüm yüklenmiş layout'ları admin GUI'si için döndürür. */
    public java.util.Map<String, dev.ensisdev.lbauctionhouse.gui.GUILayoutLoader.GUILayout> getLayouts() {
        return layoutLoader.getLayouts();
    }

    private dev.ensisdev.lbauctionhouse.service.NegotiationService negotiation;
    /** Pazarlık (teklif) servisi — anlık olarak başlatılır. */
    public dev.ensisdev.lbauctionhouse.service.NegotiationService getNegotiation() {
        if (negotiation == null) negotiation = new dev.ensisdev.lbauctionhouse.service.NegotiationService(plugin, this);
        return negotiation;
    }

    public AuctionManager(LbAuctionHouse plugin, AuctionAPI api, CollectionEntry data,
                          AuctionConfig config, AuctionEconomy economy,
                          AddonLogger logger, GUILayoutLoader layoutLoader,
                          ClusterBridge cluster,
                          AntiDupeService antiDupeService,
                          ListingCacheService listingCache) {
        this.plugin = plugin;
        this.api = api;
        this.data = data;
        this.config = config;
        this.economy = economy;
        this.logger = logger;
        this.layoutLoader = layoutLoader;
        this.cluster = cluster;
        this.broadcastService = new BroadcastService(plugin, config, data);
        this.antiDupeService = antiDupeService;
        this.listingCache = listingCache;
    }

    public AntiDupeService getAntiDupeService() { return antiDupeService; }
    public ListingCacheService getListingCache() { return listingCache; }

    // ----------------------------------------------------------------
    // GUI'leri Açma
    // ----------------------------------------------------------------

    public void openMainMenu(Player player) {
        checkExpiredListings();
        if (mainMenuGUI == null)
            mainMenuGUI = new MainMenuGUI(plugin, api.getCore(), this, config, data, economy, layoutLoader);
        mainMenuGUI.open(player);
    }

    /**
     * Ana menüyü belirli bir arama sorgusuyla açar (örn: /ihale ara elmas).
     */
    public void openMainMenuWithSearch(Player player, String query) {
        checkExpiredListings();
        if (mainMenuGUI == null)
            mainMenuGUI = new MainMenuGUI(plugin, api.getCore(), this, config, data, economy, layoutLoader);
        mainMenuGUI.openWithSearch(player, query);
    }

    public void openMyListings(Player player) {
        if (myListingsGUI == null)
            myListingsGUI = new MyListingsGUI(this, config, data, layoutLoader);
        myListingsGUI.open(player);
    }

    public void refreshMyListings(Player player) {
        if (myListingsGUI != null) myListingsGUI.open(player);
    }

    public void openConfirmBuy(Player player, AuctionListing listing) {
        if (confirmBuyGUI == null)
            confirmBuyGUI = new ConfirmBuyGUI(this, layoutLoader);
        confirmBuyGUI.open(player, listing);
    }

    public void openCollectionBox(Player player) {
        if (collectionBoxGUI == null)
            collectionBoxGUI = new CollectionBoxGUI(this, config, data, economy, layoutLoader);
        collectionBoxGUI.open(player);
    }

    public void openFavorites(Player player) {
        if (favoritesGUI == null)
            favoritesGUI = new FavoritesGUI(this, config, data, economy, layoutLoader);
        favoritesGUI.open(player);
    }

    public void openHistory(Player player) {
        if (historyGUI == null)
            historyGUI = new HistoryGUI(this, config, data, economy, layoutLoader);
        historyGUI.open(player);
    }

    public void openAdminGUI(Player player) {
        if (adminGUI == null)
            adminGUI = new AdminGUI(this, config, data, economy);
        adminGUI.open(player);
    }

    /**
     * Cache'lenmiş tüm GUI örneklerini sıfırlar (reload/sıcak yenileme için).
     * <p>
     * GUI'ler layout'u (gui/*.yml) constructor'da BİR KEZ okur ve burada singleton
     * olarak saklanır. {@code GUILayoutLoader.clearCache()} layout cache'ini temizlese
     * bile oluşturulmuş örnekler eski layout nesnesini tutmaya devam eder; bu nedenle
     * config değişikliğinin yeni açılışlarda uygulanması için örnekler yeniden
     * oluşturulmalıdır. Reload komutu bu metodu çağırır; bir sonraki açılışta
     * ilgili GUI yeni (güncel) layout ile kurulur.
     */
    public void resetCachedGuis() {
        mainMenuGUI = null;
        myListingsGUI = null;
        confirmBuyGUI = null;
        collectionBoxGUI = null;
        favoritesGUI = null;
        historyGUI = null;
        adminGUI = null;
    }

    /**
     * Süresi dolmuş bir ilanı onaylı olarak yeniden listeler.
     * <p>
     * Eşyanın koleksiyona düşen kopyası temizlenir (ilaç/dupe önlenir),
     * ilan aktif edilir ve varsa süre ücreti kesilir.
     *
     * @return yenileme başarılı mı?
     */
    public boolean renewListing(Player player, AuctionListing listing) {
        if (listing == null || listing.sold() || !listing.expired()) return false;

        if (data.isPlayerBanned(player.getUniqueId())) {
            player.sendMessage("§cİhalelerden yasaklandınız!");
            return false;
        }

        // İlan limiti kontrolü (permission bazlı)
        int limit = getMaxLimit(player);
        int current = listingCache.getActiveCountBySeller(player.getUniqueId());
        if (current >= limit) {
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.failed-limit"));
            return false;
        }

        // Süre ücreti (varsa) — yeni listelemeyle aynı kurallar
        double durationFee = config.getDurationOptions().getOrDefault(config.getExpireHours(), 0.0) * config.getExpireHours();
        if (durationFee > 0) {
            if (!economy.has(player, durationFee)) {
                player.sendMessage(api.getLanguageManager().getPrefixed("auction.purchase.insufficient-funds"));
                return false;
            }
            economy.withdraw(player, durationFee);
        }

        long newExpiresAt = System.currentTimeMillis() + (config.getExpireHours() * 3600_000L);
        data.removeCollectionByListing(listing.id());
        data.relistListing(listing.id(), newExpiresAt);
        data.incrementRenewCount(listing.id());
        return true;
    }

    // ----------------------------------------------------------------
    // İş Mantığı
    // ----------------------------------------------------------------

    /**
     * Bir eşyayı ihalea koyar.
     * @return başarılı mı? (false = limit aşıldı, blacklist, ekonomi yok)
     */
    public boolean listItem(Player player, ItemStack item, double price) {
        return listItem(player, item, price, config.getExpireHours(), false);
    }

    public boolean listItem(Player player, ItemStack item, double price, int expireHours) {
        return listItem(player, item, price, expireHours, false);
    }

    public boolean listItem(Player player, ItemStack item, double price, int expireHours, boolean advertised) {
        return listItem(player, item, price, expireHours, advertised, false);
    }

    public boolean listItem(Player player, ItemStack item, double price, int expireHours, boolean advertised, boolean offersEnabled) {
        // Ekonomi kontrolü
        if (!economy.isEnabled()) {
            logger.warn("Ekonomi servisi kullanılamıyor — listeleme yapılamaz.");
            return false;
        }

        // Fiyat kontrolü
        if (price < config.getMinPrice() || price > config.getMaxPrice()) {
            return false;
        }

        // Blacklist kontrolü
        if (config.isBlacklisted(item.getType())) {
            return false;
        }

        // Ban kontrolü
        if (data.isPlayerBanned(player.getUniqueId())) {
            player.sendMessage("§cİhalelerden yasaklandınız!");
            return false;
        }

        // Limit kontrolü (permission bazlı) — cache'den okunur
        int limit = getMaxLimit(player);
        int current = listingCache.getActiveCountBySeller(player.getUniqueId());
        if (current >= limit) {
            return false;
        }

        // Reklam kontrolü
        if (advertised && config.isAdvertiseEnabled()) {
            int advCount = data.getActiveAdvertisedCountBySeller(player.getUniqueId());
            if (advCount >= config.getAdvertiseMaxPerPlayer()) {
                return false;
            }
            double advFee = config.getAdvertiseFee();
            if (advFee > 0) {
                if (!economy.has(player, advFee)) return false;
                economy.withdraw(player, advFee);
            }
        } else if (advertised) {
            advertised = false; // reklam sistemi kapalıysa normal ilan yap
        }

        // PreSellEvent — iptal edilebilir
        var preSell = new AuctionPreSellEvent(player, item, price);
        Bukkit.getPluginManager().callEvent(preSell);
        if (preSell.isCancelled()) return false;
        price = preSell.getPrice(); // event handler fiyatı değiştirebilir

        // Listing oluştur
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        long expiresAt = now + (expireHours * 3600_000L);

        double durationFee = config.getDurationOptions().getOrDefault(expireHours, 0.0) * expireHours;
        if (durationFee > 0) {
            if (!economy.has(player, durationFee)) return false;
            economy.withdraw(player, durationFee);
        }

        // İlan başına sabit ön ücret (listing fee) — sıfırsa atlanır
        double listingFee = config.getListingFee();
        if (listingFee > 0) {
            if (!economy.has(player, listingFee)) return false;
            economy.withdraw(player, listingFee);
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.fee-charged",
                    "fee", economy.format(listingFee)));
        }

        long flashSaleEndsAt = 0;
        double originalPrice = 0;
        if (config.isFlashSaleEnabled() && expireHours <= config.getFlashSaleMaxDurationHours()) {
            int currentFlashCount = data.getActiveFlashSaleCount(player.getUniqueId());
            if (currentFlashCount < config.getFlashSaleMaxPerPlayer()) {
                flashSaleEndsAt = now + (config.getFlashSaleDurationHours() * 3600_000L);
                originalPrice = price;
                price = dev.ensisdev.lbauctionhouse.util.AuctionMath.flashSalePrice(price, config.getFlashSaleDiscountPercent());
            }
        }

        final double finalPrice = price;
        final boolean finalAdvertised = advertised;
        final boolean finalOffers = offersEnabled;
        AuctionListing listing = new AuctionListing(
                id, player.getUniqueId(), player.getName(),
                item, finalPrice, 0, "BIN", now, expiresAt, false, null, null,
                flashSaleEndsAt, originalPrice, false, 0, advertised, finalOffers
        );

        data.insertListingAsync(listing).thenRun(() -> {
            cluster.onListingCreated(listing);
            if (config.isDiscordWebhookEnabled()) {
                String whUrl = config.getDiscordWebhookUrl();
                String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(item);
                dev.ensisdev.lbauctionhouse.util.DiscordWebhook.notifyListing(whUrl, player.getName(), itemName, finalPrice);
            }
            if (finalAdvertised) {
                plugin.getScheduler().runTask(() -> broadcastService.broadcastAdvertisedListing(listing));
            }

            // Wishlist bildirimi ana thread'de
            String matName = listing.item().getType().name();
            var watchers = data.getWishlistWatchers(matName);
            plugin.getScheduler().runTask(() -> {
                for (UUID watcherUUID : watchers) {
                    if (watcherUUID.equals(player.getUniqueId())) continue;
                    var watcher = Bukkit.getPlayer(watcherUUID);
                    if (watcher != null && watcher.isOnline()) {
                        watcher.sendMessage(api.getLanguageManager().getPrefixed(
                                "auction.wishlist.notify",
                                "item", dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item()),
                                "price", economy.format(listing.price()),
                                "seller", player.getName()));
                    }
                }
            });

            data.insertLogAsync(AuctionLog.Action.SELL.name(), player.getUniqueId().toString(),
                    player.getName(), null, null, item, finalPrice, 0, id.toString());
        }).exceptionally(ex -> {
            logger.warn("İlan eklenirken hata: " + ex.getMessage());
            // DB'ye eklenemedi → eşya ve alınan tüm ücretler iade edilir (eşya kaybı önlenir).
            // NOT: Envanter işlemleri Folia'da oyuncunun kendi region'ında yapılmalıdır —
            // bu yüzden runTaskForPlayer (Bukkit'te runTask ile aynıdır) kullanılır.
            plugin.getScheduler().runTaskForPlayer(player, () -> {
                player.getInventory().addItem(item.clone());
                if (finalAdvertised && config.isAdvertiseEnabled()) {
                    double advFee = config.getAdvertiseFee();
                    if (advFee > 0) economy.deposit(player.getUniqueId(), advFee);
                }
                // durationFee metot scope'unda tanımlı — lambda içinde erişilebilir (effectively final)
                if (durationFee > 0) economy.deposit(player.getUniqueId(), durationFee);
                // listingFee de aynı şekilde iade edilir (önceden iade edilmiyordu — eksikti)
                if (listingFee > 0) economy.deposit(player.getUniqueId(), listingFee);
                player.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.failed-db"));
            });
            return null;
        });

        return true;
    }

    /**
     * Bir ilanı satın alır.
     * @return işlem sonucu kodu
     */
    public PurchaseResult buyItem(Player buyer, AuctionListing listing) {
        return buyItem(buyer, listing, null);
    }

    /**
     * Satın alma — isteğe bağlı anlaşılan (pazarlık) fiyat override'ı.
     * {@code priceOverride != null} ise onun yerine o fiyat kullanılır.
     */
    public PurchaseResult buyItem(Player buyer, AuctionListing listing, Double priceOverride) {
        if (!economy.isEnabled())
            return PurchaseResult.ECONOMY_DISABLED;

        // Ban kontrolü
        if (data.isPlayerBanned(buyer.getUniqueId())) {
            buyer.sendMessage("§cİhalelerden yasaklandınız!");
            return PurchaseResult.CANCELLED;
        }

        UUID lid = listing.id();
        // Anti-dupe: aynı ilana çift işlem / hızlı tekrar koruması
        if (!antiDupeService.tryBeginTransaction(lid, buyer)) {
            return PurchaseResult.TRANSACTION_FAILED;
        }
        try {
            // Kritik işlem öncesi her zaman taze DB okuması (cache okumaz)
            AuctionListing fresh = antiDupeService.getFreshListing(lid);
            if (fresh == null || fresh.sold()) return PurchaseResult.ALREADY_SOLD;
            if (fresh.sellerUUID().equals(buyer.getUniqueId())) return PurchaseResult.CANNOT_BUY_OWN;

                // Envanter kontrolü — paketler açılır; stackable eşyalar dahil tam kapasite kontrolü
                ItemStack itemStack = fresh.item().clone();
                List<ItemStack> toGive = BundleItems.isBundle(itemStack)
                        ? BundleItems.unpack(itemStack)
                        : List.of(itemStack);
                if (!canHoldInventory(buyer, toGive)) {
                    buyer.sendMessage(api.getLanguageManager().getPrefixed("auction.purchase.inventory-full"));
                    return PurchaseResult.CANCELLED;
                }

                var preBuy = new AuctionPrePurchaseEvent(buyer, fresh);
                Bukkit.getPluginManager().callEvent(preBuy);
                if (preBuy.isCancelled()) return PurchaseResult.CANCELLED;

                // "BOTH" tipinde: BIN fiyatı varsa onu kullan, yoksa price kullan
                double buyPrice = priceOverride != null ? priceOverride
                : (fresh.isBoth() && fresh.binPrice() > 0 ? fresh.binPrice() : fresh.price());
                if (!economy.has(buyer, buyPrice)) return PurchaseResult.INSUFFICIENT_FUNDS;
                // Çapraz sunucu (MySQL) güvenliği: ilanı ATOMIK olarak claim et —
                // yalnızca hâlâ satılmamışsa satılır, iki sunucu aynı anda satamaz.
                if (!data.markSoldIfAvailable(lid)) return PurchaseResult.ALREADY_SOLD;
                if (!economy.withdraw(buyer, buyPrice)) {
                    data.undoSold(lid); // para çekilemedi → claim'i geri al
                    return PurchaseResult.TRANSACTION_FAILED;
                }

                // Paket (fıçı) ise eşyaları AÇ ve tek tek ver; shulker kutusu olduğu gibi verilir.
                var leftover = buyer.getInventory().addItem(toGive.toArray(new ItemStack[0]));

                double taxRate = config.getTaxRate();
                double netAmount = economy.calculateNet(buyPrice, taxRate);

                // Reklamlı ilanlarda ek komisyon kesintisi
                if (fresh.advertised()) {
                    double advCommission = netAmount * (config.getAdvertiseCommissionPercent() / 100.0);
                    netAmount -= advCommission;
                }

                final double finalNetAmount = netAmount;
                data.markSoldAsync(lid, buyer.getName(), buyer.getUniqueId()).thenRun(() -> {
                    if (!leftover.isEmpty()) {
                        // Paket alımında birden fazla artık olabilir — hepsi koliye düşer
                        for (ItemStack lo : leftover.values()) {
                            data.addToCollectionAsync(buyer.getUniqueId(), "ITEM", lo, 0, lid);
                        }
                    }

                    if (config.isConfirmMoney()) {
                        data.addToCollectionAsync(fresh.sellerUUID(), "MONEY", null, finalNetAmount, lid);
                        // Main thread'de bildirim
                        plugin.getScheduler().runTask(() -> {
                            var sellerPlayer = Bukkit.getPlayer(fresh.sellerUUID());
                            if (sellerPlayer != null && sellerPlayer.isOnline()) {
                                sellerPlayer.sendMessage(api.getLanguageManager().getPrefixed(
                                        "auction.purchase.sold-notification",
                                        "item", dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(fresh.item()),
                                        "price", economy.format(buyPrice),
                                        "command", config.getLangMainCommand()));
                            }
                        });
                    } else {
                        economy.deposit(fresh.sellerUUID(), finalNetAmount);
                    }

                    cluster.onListingSold(fresh, buyer.getName());
                    data.insertLogAsync(AuctionLog.Action.PURCHASE.name(), fresh.sellerUUID().toString(), fresh.sellerName(),
                            buyer.getUniqueId().toString(), buyer.getName(), fresh.item(), buyPrice, taxRate, lid.toString());
                }).exceptionally(ex -> {
                    logger.warn("Satış işareti hatası: " + ex.getMessage());
                    // DB'de satış işareti tamamlanamadı → işlemi geri al:
                    // ilan yeniden satılabilir (undoSold) + alıcının parası iade edilir.
                    plugin.getScheduler().runTask(() -> {
                        data.undoSold(lid);
                        economy.deposit(buyer.getUniqueId(), buyPrice);
                    });
                    return null;
                });

                // Discord webhook bildirimi (async — ağ isteği main thread'i bloklamaz)
                if (config.isDiscordWebhookEnabled()) {
                    String whUrl = config.getDiscordWebhookUrl();
                    String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(fresh.item());
                    plugin.getScheduler().runTaskAsynchronously(
                            () -> dev.ensisdev.lbauctionhouse.util.DiscordWebhook.notifySale(
                                    whUrl, buyer.getName(), itemName, buyPrice));
                }

                return PurchaseResult.SUCCESS;
        } finally {
            antiDupeService.endTransaction(lid);
        }
    }

    /**
     * Oyuncunun kendi ilanını iptal eder.
     */
    public boolean cancelListing(Player player, AuctionListing listing) {
        if (!listing.sellerUUID().equals(player.getUniqueId()))
            return false;

        if (listing.sold())
            return false;

        // Eşyayı oyuncuya geri ver veya koleksiyona düş
        ItemStack item = listing.item().clone();
        var leftover = player.getInventory().addItem(item);

        data.deleteListingAsync(listing.id()).thenRun(() -> {
            if (!leftover.isEmpty()) {
                data.addToCollectionAsync(player.getUniqueId(), "ITEM", leftover.get(0), 0, listing.id());
            }
            cluster.onListingRemoved(listing);
            data.insertLogAsync(AuctionLog.Action.CANCEL.name(), listing.sellerUUID().toString(), listing.sellerName(),
                    null, null, listing.item(), 0, 0, listing.id().toString());
        });

        return true;
    }

    /**
     * Satıcı kendi aktif ilanının fiyatını günceller.
     */
    public boolean updateListingPrice(Player player, UUID listingId, double newPrice) {
        if (newPrice < config.getMinPrice() || newPrice > config.getMaxPrice()) {
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.failed-price",
                    "min", String.valueOf(config.getMinPrice()), "max", String.valueOf(config.getMaxPrice())));
            return false;
        }
        AuctionListing listing = antiDupeService.getFreshListing(listingId);
        if (listing == null || listing.sold()) {
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.purchase.already-sold"));
            return false;
        }
        if (!listing.sellerUUID().equals(player.getUniqueId())) {
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.no-permission"));
            return false;
        }
        data.updateListingPriceAsync(listingId, newPrice);
        player.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.price-updated",
                "price", economy.format(newPrice)));
        return true;
    }

    /**
     * Admin: herhangi bir ilanı kaldırır.
     */
    public boolean removeListing(UUID listingId) {
        AuctionListing listing = data.getListing(listingId);
        if (listing == null) return false;

        data.deleteListingAsync(listingId).thenRun(() -> {
            data.addToCollectionAsync(listing.sellerUUID(), "ITEM", listing.item(), 0, listingId);
            cluster.onListingRemoved(listing);
            data.insertLogAsync(AuctionLog.Action.ADMIN_REMOVE.name(), listing.sellerUUID().toString(),
                    listing.sellerName(), null, null, listing.item(), 0, 0, listingId.toString());
        });

        return true;
    }

    /**
     * Admin: tüm aktif ilanları temizler. Ana thread'i bloklamaz —
     * ilanlar arka planda expired işaretlenir ve koleksiyona async eklenir.
     */
    public CompletableFuture<Integer> clearAllListingsAsync() {
        return data.getActiveListingsAsync().thenApply(active -> {
            for (AuctionListing listing : active) {
                data.markExpiredAsync(listing.id());
                data.addToCollectionAsync(listing.sellerUUID(), "ITEM", listing.item(), 0, listing.id());
            }
            return active.size();
        });
    }

    /**
     * Oyuncunun envanterinin verilen eşyaları sığdırıp sığdıramayacağını kontrol eder.
     * Stackable eşyalar için mevcut stack'lere ekleme yapılabilir mi diye hesaba katar
     * (firstEmpty() == -1 tek başına 64'lük yığınlar için yanlış sonuç verir).
     */
    private boolean canHoldInventory(Player player, List<ItemStack> incoming) {
        if (incoming == null || incoming.isEmpty()) return true;
        ItemStack[] contents = player.getInventory().getContents();
        int emptySlots = 0;
        for (ItemStack c : contents) {
            if (c == null || c.getType().isAir()) emptySlots++;
        }
        for (ItemStack item : incoming) {
            if (item == null || item.getType().isAir()) continue;
            int amount = item.getAmount();
            // Önce aynı türdeki mevcut stack'lere ekle
            for (ItemStack c : contents) {
                if (amount <= 0) break;
                if (c != null && c.isSimilar(item) && c.getAmount() < c.getMaxStackSize()) {
                    amount -= Math.min(c.getMaxStackSize() - c.getAmount(), amount);
                }
            }
            if (amount <= 0) continue;
            int neededSlots = (amount + item.getMaxStackSize() - 1) / item.getMaxStackSize();
            if (neededSlots > emptySlots) return false;
            emptySlots -= neededSlots;
        }
        return true;
    }

    // ----------------------------------------------------------------
    // Placeholder / Sorgular
    // ----------------------------------------------------------------

    public int getActiveListingCount() {
        return listingCache.getActiveListings().size();
    }

    public int getPlayerListingCount(UUID playerUUID) {
        return listingCache.getActiveCountBySeller(playerUUID);
    }

    // ----------------------------------------------------------------
    // Koleksiyon
    // ----------------------------------------------------------------

    public int getUnclaimedCount(UUID playerUUID) {
        return listingCache.getUnclaimedCount(playerUUID);
    }

    /**
     * Oyuncunun bekleyen (claim edilmemiş) para bakiyesi.
     * Koleksiyondaki MONEY tipindeki girişlerin toplamıdır.
     */
    public double getUnclaimedBalance(UUID playerUUID) {
        return data.getUnclaimedCollection(playerUUID).stream()
                .filter(e -> "MONEY".equals(e.type()))
                .mapToDouble(e -> e.amount())
                .sum();
    }

    /**
     * Oyuncunun toplam satış/satın alma istatistiklerini döndürür.
     */
    public CollectionEntry.PlayerStats getPlayerStats(UUID playerUUID) {
        return listingCache.getPlayerStats(playerUUID);
    }

    public List<CollectionEntry.UnclaimedEntry> getUnclaimedCollection(UUID playerUUID) {
        return data.getUnclaimedCollection(playerUUID);
    }

    public void claimItem(int entryId) {
        data.markClaimed(entryId);
    }

    // ----------------------------------------------------------------
    // İzin Bazlı Limit
    // ----------------------------------------------------------------

    private int getMaxLimit(Player player) {
        // İzin sırası:
        // 1) lbauctionhouse.auctionlimit.<N> (yeni format — en yüksek değer kazanır)
        // 2) lbauctionhouse.limit.<N> (eski format — geriye dönük uyumluluk)
        // 3) config'deki auction.max-listings-per-player (varsayılan)
        int highest = config.getMaxListingsPerPlayer();
        for (int i = 50; i >= 1; i--) {
            if (player.hasPermission("lbauctionhouse.auctionlimit." + i)
                    || player.hasPermission("lbauctionhouse.limit." + i)) {
                return i;
            }
        }
        return highest;
    }

    // ----------------------------------------------------------------
    // Shutdown
    // ----------------------------------------------------------------

    public void shutdown() {
        if (cluster != null) cluster.disable();
    }

    // ----------------------------------------------------------------
    // Teklif Sistemi
    // ----------------------------------------------------------------

    /**
     * Bir ilana teklif verir.
     * @return BidResult
     */
    public BidResult placeBid(Player bidder, AuctionListing listing, double amount) {
        UUID lid = listing.id();
        if (!antiDupeService.tryBeginTransaction(lid, bidder)) {
            return BidResult.TRANSACTION_FAILED;
        }
        try {
            AuctionListing fresh = antiDupeService.getFreshListing(lid);
            if (fresh == null || fresh.sold()) return BidResult.SOLD;
                if (fresh.sellerUUID().equals(bidder.getUniqueId())) return BidResult.OWN_LISTING;
                if (!economy.has(bidder, amount)) return BidResult.INSUFFICIENT_FUNDS;

                double currentPrice = fresh.price();
                if (fresh.isBid() && amount < fresh.startingBid()) return BidResult.BELOW_STARTING;
                if (amount <= currentPrice) return BidResult.TOO_LOW;

                // BID Auto-Extend: eğer süre 5 dakikadan azsa +5dk uzat
                if (fresh.isBid() && config.getAutoExtendSeconds() > 0 && fresh.getTimeLeft() < config.getAutoExtendThreshold() * 1000L) {
                    long extended = fresh.expiresAt() + (config.getAutoExtendSeconds() * 1000L);
                    data.updateExpiresAt(lid, extended);
                }

                // Önce yeni teklifin parasını ÇEK — başarısızsa işlem hiç yapılmaz.
                // (Eski teklif önce iade edilirse ve çekim başarısız olursa para enflasyonu oluşur.)
                if (!economy.withdraw(bidder, amount)) return BidResult.TRANSACTION_FAILED;

                AuctionBid previous = data.getHighestBid(lid);
                if (previous != null) {
                    economy.deposit(previous.bidderUUID(), previous.amount());
                    // Outbid notification
                    if (!previous.bidderUUID().equals(bidder.getUniqueId())) {
                        var outbidPlayer = Bukkit.getPlayer(previous.bidderUUID());
                        if (outbidPlayer != null && outbidPlayer.isOnline()) {
                            outbidPlayer.sendMessage(api.getLanguageManager().getPrefixed(
                                    "auction.bid.outbid",
                                    "player", bidder.getName(),
                                    "amount", economy.format(amount),
                                    "item", dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(fresh.item())));
                        }
                    }
                }

                data.insertBidAsync(lid, bidder.getUniqueId(), bidder.getName(), amount);
                data.updateListingPriceAsync(lid, amount);

                var sellerPlayer = Bukkit.getPlayer(fresh.sellerUUID());
                if (sellerPlayer != null) {
                    sellerPlayer.sendMessage(api.getLanguageManager().getPrefixed(
                            "auction.bid.placed",
                            "player", bidder.getName(),
                            "amount", economy.format(amount)));
                }

                // Auto-bid tetikle
                processAutoBids(lid);

                // Discord webhook bildirimi (async)
                if (config.isDiscordWebhookEnabled()) {
                    String whUrl = config.getDiscordWebhookUrl();
                    String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(fresh.item());
                    plugin.getScheduler().runTaskAsynchronously(
                            () -> dev.ensisdev.lbauctionhouse.util.DiscordWebhook.notifyBid(
                                    whUrl, bidder.getName(), itemName, amount));
                }

                return BidResult.SUCCESS;
        } finally {
            antiDupeService.endTransaction(lid);
        }
    }

    /**
     * Oyuncunun auto-bid'ini ayarlar.
     */
    public boolean setAutoBid(Player player, AuctionListing listing, double maxAmount) {
        if (!listing.isBid()) return false;
        if (listing.sellerUUID().equals(player.getUniqueId())) return false;
        double minIncrement = config.getAutoBidMinIncrement();
        data.setAutoBid(listing.id(), player.getUniqueId(), player.getName(), maxAmount, minIncrement);
        // İlk tetikleme
        processAutoBids(listing.id());
        return true;
    }

    /**
     * Auto-bid'leri kaldırır.
     */
    public void removeAutoBid(Player player, AuctionListing listing) {
        data.removeAutoBid(listing.id(), player.getUniqueId());
    }

    /**
     * Otomatik teklif döngüsü — bir listing'deki tüm auto-bid'leri kontrol eder
     * ve sıradaki en yüksek auto-bid sahibine otomatik teklif verir.
     */
    private void processAutoBids(UUID listingId) {
        AuctionListing fresh = antiDupeService.getFreshListing(listingId);
        if (fresh == null || fresh.sold()) return;

        double currentPrice = fresh.price();
        var autobids = data.getActiveAutoBids(listingId);

        for (var ab : autobids) {
            AuctionBid highest = data.getHighestBid(listingId);
            if (highest != null && highest.bidderUUID().equals(ab.playerUUID())) continue;
            if (!ab.canBid(currentPrice)) continue;

            Player bidder = Bukkit.getPlayer(ab.playerUUID());
            if (bidder == null || !bidder.isOnline()) {
                data.removeAutoBid(listingId, ab.playerUUID());
                continue;
            }

            double nextBid = currentPrice + ab.increment();
            if (nextBid > ab.maxAmount()) nextBid = ab.maxAmount();
            if (nextBid <= currentPrice) continue;

            if (!economy.has(bidder, nextBid)) {
                data.removeAutoBid(listingId, ab.playerUUID());
                continue;
            }

            economy.withdraw(bidder, nextBid);
            data.insertBid(listingId, ab.playerUUID(), ab.playerName(), nextBid);
            data.updateListingPrice(listingId, nextBid);

            // Snipe koruması: son dakika auto-bid teklifinde süreyi uzat
            if (config.getAutoExtendSeconds() > 0 && fresh.getTimeLeft() < config.getAutoExtendThreshold() * 1000L) {
                data.updateExpiresAt(listingId, fresh.expiresAt() + (config.getAutoExtendSeconds() * 1000L));
            }

            bidder.sendMessage(api.getLanguageManager().getPrefixed("auction.bid.autobid-placed",
                    "amount", economy.format(nextBid), "max", economy.format(ab.maxAmount())));

            logger.info("Auto-bid: " + ab.playerName() + " → " + nextBid + " (max: " + ab.maxAmount() + ")");
            currentPrice = nextBid;
            break;
        }
    }


    public void checkExpiredListings() {
        long now = System.currentTimeMillis();
        if (now - lastExpiryCheck < 60_000) return;
        lastExpiryCheck = now;

        plugin.getScheduler().runTaskAsynchronously(() -> {
            List<AuctionListing> expired = data.getExpiredListings();
            if (expired.isEmpty()) return;

            List<AuctionListing> bins = expired.stream().filter(l -> !l.isBid()).toList();
            List<AuctionListing> bids = expired.stream().filter(AuctionListing::isBid).toList();

            // Süresi dolmuş BIN ilanlarını işle — auto-relist, max tekrar sayısına kadar
            for (AuctionListing listing : bins) {
                if (config.getAutoRenewMax() > 0 && data.getRenewCount(listing.id()) < config.getAutoRenewMax()) {
                    data.incrementRenewCount(listing.id());
                    long listedTime = listing.expiresAt() - listing.listedAt();
                    long newExpiresAt = System.currentTimeMillis() + listedTime;
                    data.updateExpiresAt(listing.id(), newExpiresAt);
                    final String itemName = dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item());
                    final UUID sellerId = listing.sellerUUID();
                    plugin.getScheduler().runTask(() -> {
                        var seller = Bukkit.getPlayer(sellerId);
                        if (seller != null && seller.isOnline()) {
                            seller.sendMessage(api.getLanguageManager().getPrefixed("auction.listing.auto-renewed",
                                    "item", itemName));
                        }
                    });
                    continue;
                }
                resolveExpiredBinListing(listing);
            }

            // Süresi dolmuş BID ilanlarını işle (main thread)
            if (!bids.isEmpty()) {
                plugin.getScheduler().runTask(() -> resolveBids(bids));
            }
            if (!expired.isEmpty()) logger.info(expired.size() + " süresi dolmuş ilan işlendi.");
        });
    }

    private void resolveExpiredBinListing(AuctionListing listing) {
        data.markExpiredAsync(listing.id()).thenRun(() -> {
            data.addToCollectionAsync(listing.sellerUUID(), "ITEM", listing.item(), 0, listing.id());
            data.insertLogAsync(AuctionLog.Action.EXPIRED.name(), listing.sellerUUID().toString(),
                    listing.sellerName(), null, null, listing.item(), 0, 0, listing.id().toString());
        });
    }

    private void resolveBids(List<AuctionListing> bids) {
        for (AuctionListing listing : bids) {
            resolveBidListing(listing);
        }
        if (!bids.isEmpty()) logger.info(bids.size() + " BID ilanı çözümlendi.");
    }

    private void resolveBidListing(AuctionListing listing) {
        AuctionBid highest = data.getHighestBid(listing.id());
        if (highest != null) {
            resolveWinningBid(listing, highest);
        } else {
            resolveNoBidListing(listing);
        }
        data.markExpiredAsync(listing.id());
    }

    private void resolveWinningBid(AuctionListing listing, AuctionBid highest) {
        data.markSoldAsync(listing.id(), highest.bidderName(), highest.bidderUUID()).thenRun(() -> {
            data.addToCollectionAsync(highest.bidderUUID(), "ITEM", listing.item(), 0, listing.id());
            double tax = config.getTaxRate();
            double net = economy.calculateNet(highest.amount(), tax);
            if (config.isConfirmMoney()) {
                data.addToCollectionAsync(listing.sellerUUID(), "MONEY", null, net, listing.id());
            } else {
                economy.deposit(listing.sellerUUID(), net);
            }
            data.insertLogAsync(AuctionLog.Action.PURCHASE.name(), listing.sellerUUID().toString(),
                    listing.sellerName(), highest.bidderUUID().toString(),
                    highest.bidderName(), listing.item(), highest.amount(), tax, listing.id().toString());
        });
        var winner = Bukkit.getPlayer(highest.bidderUUID());
        if (winner != null)
            winner.sendMessage(api.getLanguageManager().getPrefixed("auction.bid.won"));
    }

    private void resolveNoBidListing(AuctionListing listing) {
        data.addToCollectionAsync(listing.sellerUUID(), "ITEM", listing.item(), 0, listing.id());
        data.insertLogAsync(AuctionLog.Action.EXPIRED.name(), listing.sellerUUID().toString(),
                listing.sellerName(), null, null, listing.item(), 0, 0, listing.id().toString());
    }

    public enum BidResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        TOO_LOW,
        BELOW_STARTING,
        SOLD,
        OWN_LISTING,
        TRANSACTION_FAILED
    }

    // ----------------------------------------------------------------
    // Kiralama Sistemi
    // ----------------------------------------------------------------

    /**
     * Bir eşyayı kirala — kiralama ücretini al, eşyayı ver, süreli iade planla.
     */
    public boolean rentItem(Player renter, AuctionListing listing, int days) {
        if (!config.isRentalEnabled()) return false;
        if (!listing.type().equals("RENT")) return false;
        if (days < config.getMinRentalDays() || days > config.getMaxRentalDays()) return false;
        double totalPrice = listing.price() * days;

        if (!economy.has(renter, totalPrice)) return false;
        if (!economy.withdraw(renter, totalPrice)) return false;

        // Eşya clone'unu kiracıya ver
        var clone = listing.item().clone();
        var leftover = renter.getInventory().addItem(clone);
        if (!leftover.isEmpty()) {
            data.addToCollection(renter.getUniqueId(), "ITEM", leftover.get(0), 0, listing.id());
        }

        // Satıcıya ödeme (vergi sonrası)
        double tax = config.getTaxRate();
        double net = economy.calculateNet(totalPrice, tax);
        if (config.isConfirmMoney()) {
            data.addToCollection(listing.sellerUUID(), "MONEY", null, net, listing.id());
        } else {
            economy.deposit(listing.sellerUUID(), net);
        }

        // Kiralama sonunda iade planla (DB'ye de yaz)
        long returnAt = System.currentTimeMillis() + (days * 86400_000L);
        data.updateRentalEnd(listing.id(), returnAt);
        plugin.getScheduler().runTaskLater(
            () -> returnRentalItem(listing),
            days * 86400_000L / 50L // tick
        );

        data.insertLogAsync(AuctionLog.Action.PURCHASE.name(), listing.sellerUUID().toString(), listing.sellerName(),
                renter.getUniqueId().toString(), renter.getName(), listing.item(),
                totalPrice, tax, listing.id().toString());

        return true;
    }

    private void returnRentalItem(AuctionListing listing) {
        data.addToCollection(listing.sellerUUID(), "ITEM", listing.item(), 0, listing.id());
        var seller = Bukkit.getPlayer(listing.sellerUUID());
        if (seller != null)
            seller.sendMessage(api.getLanguageManager().getPrefixed("auction.rental.returned",
                    "item", dev.ensisdev.lbauctionhouse.util.ItemNames.displayName(listing.item())));
    }

    // ----------------------------------------------------------------
    // Lootbox Sistemi
    // ----------------------------------------------------------------

    /**
     * Lootbox'tan rastgele eşya al.
     */
    public boolean openLootbox(Player player) {
        if (!config.isLootboxEnabled()) return false;
        double price = plugin.getConfig().getDouble("auction.lootbox.price", 100.0);
        if (!economy.has(player, price)) return false;
        economy.withdraw(player, price);

        // Config'deki loot tablosundan rastgele eşya seç
        var items = plugin.getConfig().getConfigurationSection("lootbox.items");
        if (items == null) {
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.lootbox.not-configured"));
            return false;
        }

        double totalWeight = items.getKeys(false).stream()
                .mapToDouble(k -> items.getDouble(k + ".weight", 1))
                .sum();

        double roll = Math.random() * totalWeight;
        double cumulative = 0;
        String chosen = "";

        for (String key : items.getKeys(false)) {
            cumulative += items.getDouble(key + ".weight", 1);
            if (roll <= cumulative) { chosen = key; break; }
        }

        if (chosen.isEmpty()) return false;

        try {
            org.bukkit.Material mat = org.bukkit.Material.valueOf(chosen.toUpperCase());
            int amount = items.getInt(chosen + ".amount", 1);
            var item = new org.bukkit.inventory.ItemStack(mat, amount);

            var leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                data.addToCollection(player.getUniqueId(), "ITEM", leftover.get(0), 0, UUID.randomUUID());
            }

            player.sendMessage(api.getLanguageManager().getPrefixed("auction.lootbox.result",
                    "item", mat.name()));

            if (config.isDiscordWebhookEnabled()) {
                String whUrl = config.getDiscordWebhookUrl();
                plugin.getScheduler().runTaskAsynchronously(
                        () -> dev.ensisdev.lbauctionhouse.util.DiscordWebhook.notifyLootbox(whUrl, player.getName(), mat.name()));
            }
        } catch (Exception e) {
            player.sendMessage(api.getLanguageManager().getPrefixed("auction.lootbox.error",
                    "item", chosen));
        }

        return true;
    }

    public enum PurchaseResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        ALREADY_SOLD,
        CANNOT_BUY_OWN,
        ECONOMY_DISABLED,
        TRANSACTION_FAILED,
        CANCELLED
    }
}
