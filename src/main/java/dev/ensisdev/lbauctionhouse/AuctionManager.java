package dev.ensisdev.lbauctionhouse;

import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.data.AuctionBid;
import dev.ensisdev.lbauctionhouse.data.AuctionLog;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;
import dev.ensisdev.lbauctionhouse.gui.CollectionBoxGUI;
import dev.ensisdev.lbauctionhouse.gui.ConfirmBuyGUI;
import dev.ensisdev.lbauctionhouse.gui.GUILayoutLoader;
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

/**
 * Auction sisteminin merkezi iş mantığı sınıfı.
 * <p>
 * Listeleme, satın alma, iptal, sure dolumu ve claim işlemlerini yönetir.
 * GUI'leri açar, veritabanı işlemlerini AuctionData'ya devreder.
 */
public class AuctionManager {

    private final LbAuctionHouse plugin;
    private final AuctionAPI api;
    private final AuctionData data;
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

    private long lastExpiryCheck = 0;

    public AuctionData getData() { return data; }
    public AuctionAPI getApi() { return api; }

    public AuctionManager(LbAuctionHouse plugin, AuctionAPI api, AuctionData data,
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
            confirmBuyGUI = new ConfirmBuyGUI(this, config, layoutLoader);
        confirmBuyGUI.open(player, listing);
    }

    public void openCollectionBox(Player player) {
        if (collectionBoxGUI == null)
            collectionBoxGUI = new CollectionBoxGUI(this, config, data, economy, layoutLoader);
        collectionBoxGUI.open(player);
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

        long flashSaleEndsAt = 0;
        double originalPrice = 0;
        if (config.isFlashSaleEnabled() && expireHours <= config.getFlashSaleMaxDurationHours()) {
            int currentFlashCount = data.getActiveFlashSaleCount(player.getUniqueId());
            if (currentFlashCount < config.getFlashSaleMaxPerPlayer()) {
                flashSaleEndsAt = now + (config.getFlashSaleDurationHours() * 3600_000L);
                originalPrice = price;
                price = price * (100 - config.getFlashSaleDiscountPercent()) / 100;
            }
        }

        final double finalPrice = price;
        final boolean finalAdvertised = advertised;
        AuctionListing listing = new AuctionListing(
                id, player.getUniqueId(), player.getName(),
                item, finalPrice, 0, "BIN", now, expiresAt, false, null, null,
                flashSaleEndsAt, originalPrice, false, 0, false, advertised
        );

        data.insertListingAsync(listing).thenRun(() -> {
            cluster.onListingCreated(listing);
            if (finalAdvertised) {
                org.bukkit.Bukkit.getScheduler().runTask(
                        (org.bukkit.plugin.java.JavaPlugin) plugin,
                        () -> broadcastService.broadcastAdvertisedListing(listing));
            }

            // Wishlist bildirimi ana thread'de
            String matName = listing.item().getType().name();
            var watchers = data.getWishlistWatchers(matName);
            org.bukkit.Bukkit.getScheduler().runTask((org.bukkit.plugin.java.JavaPlugin) plugin, () -> {
                for (UUID watcherUUID : watchers) {
                    if (watcherUUID.equals(player.getUniqueId())) continue;
                    var watcher = Bukkit.getPlayer(watcherUUID);
                    if (watcher != null && watcher.isOnline()) {
                        watcher.sendMessage(api.getLanguageManager().getPrefixed(
                                "auction.wishlist.notify",
                                "item", listing.item().getType().name(),
                                "price", economy.format(listing.price()),
                                "seller", player.getName()));
                    }
                }
            });

            data.insertLogAsync(AuctionLog.Action.SELL.name(), player.getUniqueId().toString(),
                    player.getName(), null, null, item, finalPrice, 0, id.toString());
        }).exceptionally(ex -> {
            logger.warn("İlan eklenirken hata: " + ex.getMessage());
            return null;
        });

        return true;
    }

    /**
     * Bir ilanı satın alır.
     * @return işlem sonucu kodu
     */
    public PurchaseResult buyItem(Player buyer, AuctionListing listing) {
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

                // Envanter kontrolü — envanter doluysa uyar
                if (buyer.getInventory().firstEmpty() == -1) {
                    buyer.sendMessage(api.getLanguageManager().getPrefixed("auction.purchase.inventory-full"));
                    return PurchaseResult.CANCELLED;
                }

                var preBuy = new AuctionPrePurchaseEvent(buyer, fresh);
                Bukkit.getPluginManager().callEvent(preBuy);
                if (preBuy.isCancelled()) return PurchaseResult.CANCELLED;

                // "BOTH" tipinde: BIN fiyatı varsa onu kullan, yoksa price kullan
                double buyPrice = fresh.isBoth() && fresh.binPrice() > 0 ? fresh.binPrice() : fresh.price();
                if (!economy.has(buyer, buyPrice)) return PurchaseResult.INSUFFICIENT_FUNDS;
                if (!economy.withdraw(buyer, buyPrice)) return PurchaseResult.TRANSACTION_FAILED;

                ItemStack itemStack = fresh.item().clone();
                // Paket (fıçı) ise eşyaları AÇ ve tek tek ver; shulker kutusu olduğu gibi verilir.
                List<ItemStack> toGive = BundleItems.isBundle(itemStack)
                        ? BundleItems.unpack(itemStack)
                        : List.of(itemStack);
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
                        org.bukkit.Bukkit.getScheduler().runTask((org.bukkit.plugin.java.JavaPlugin) plugin, () -> {
                            var sellerPlayer = Bukkit.getPlayer(fresh.sellerUUID());
                            if (sellerPlayer != null && sellerPlayer.isOnline()) {
                                sellerPlayer.sendMessage(api.getLanguageManager().getPrefixed(
                                        "auction.purchase.sold-notification",
                                        "item", fresh.item().getType().name(),
                                        "price", economy.format(fresh.price()),
                                        "command", config.getLangMainCommand()));
                            }
                        });
                    } else {
                        economy.deposit(fresh.sellerUUID(), finalNetAmount);
                    }

                    cluster.onListingSold(fresh, buyer.getName());
                    data.insertLogAsync(AuctionLog.Action.PURCHASE.name(), fresh.sellerUUID().toString(), fresh.sellerName(),
                            buyer.getUniqueId().toString(), buyer.getName(), fresh.item(), fresh.price(), taxRate, lid.toString());
                }).exceptionally(ex -> {
                    logger.warn("Satış işareti hatası: " + ex.getMessage());
                    return null;
                });

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
     * Admin: tüm aktif ilanları temizler.
     */
    public int clearAllListings() {
        List<AuctionListing> active = data.getActiveListings();
        for (AuctionListing listing : active) {
            data.markExpiredAsync(listing.id());
            data.addToCollection(listing.sellerUUID(), "ITEM", listing.item(), 0, listing.id());
        }
        return active.size();
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

    public List<AuctionData.CollectionEntry> getUnclaimedCollection(UUID playerUUID) {
        return data.getUnclaimedCollection(playerUUID);
    }

    public void claimItem(int entryId) {
        data.markClaimed(entryId);
    }

    // ----------------------------------------------------------------
    // İzin Bazlı Limit
    // ----------------------------------------------------------------

    private int getMaxLimit(Player player) {
        // lbsmpcore.auction.limit.5 gibi izinleri kontrol et
        int highest = config.getMaxListingsPerPlayer();
        for (int i = 50; i >= 1; i--) {
            if (player.hasPermission("lbsmpcore.auction.limit." + i)) {
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
                                    "item", fresh.item().getType().name()));
                        }
                    }
                }

                economy.withdraw(bidder, amount);
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

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(
                (org.bukkit.plugin.java.JavaPlugin) plugin, () -> {
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
                    final String itemName = listing.item().getType().name();
                    final UUID sellerId = listing.sellerUUID();
                    org.bukkit.Bukkit.getScheduler().runTask((org.bukkit.plugin.java.JavaPlugin) plugin, () -> {
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
                org.bukkit.Bukkit.getScheduler().runTask(
                        (org.bukkit.plugin.java.JavaPlugin) plugin, () -> resolveBids(bids));
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
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            (org.bukkit.plugin.java.JavaPlugin) plugin,
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
                    "item", listing.item().getType().name()));
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
