package fr.maxlego08.zauctionhouse.services;

import fr.maxlego08.zauctionhouse.api.AuctionManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.cluster.LockToken;
import fr.maxlego08.zauctionhouse.api.event.events.AuctionExpireEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.ItemStatus;
import fr.maxlego08.zauctionhouse.api.item.StorageType;
import fr.maxlego08.zauctionhouse.api.services.AuctionExpireService;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

public class ExpireService implements AuctionExpireService {

    private final AuctionPlugin plugin;
    private final AuctionManager auctionManager;
    // Coalesces concurrent cluster-aware expiration dispatches for the same item id, so a seller
    // spamming their selling tab cannot trigger a stampede of redundant Redis round-trips while
    // one expiration is already in flight for that item.
    private final Set<Integer> expiringItemIds = ConcurrentHashMap.newKeySet();

    public ExpireService(AuctionPlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public void processExpiredItem(Item item, StorageType storageType) {

        // Guard: skip items already processed by another server (e.g., claimed via Redis cluster)
        if (item.getStatus() == ItemStatus.DELETED) {
            this.auctionManager.removeItem(storageType, item);
            return;
        }

        // Multi-server: route LISTED -> EXPIRED through a cluster-aware path that locks the item,
        // re-validates authoritative DB state (skipping items sold on another server), performs
        // the move, then broadcasts it so other nodes converge. Prevents a sold item from being
        // resurrected as EXPIRED for the seller (duplication). Single-server keeps the fast path.
        if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
            expireListedItemClustered(item);
            return;
        }

        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(List.of(item), storageType);
            event.callEvent();
        });

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH); // Suppression du cache global

        var offlineSeller = item.getSeller();
        if (offlineSeller.isOnline()) {
            var sellerPlayer = offlineSeller.getPlayer();
            if (sellerPlayer != null) {
                this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
            }
        }

        if (storageType == StorageType.LISTED) {

            item.setStatus(ItemStatus.REMOVED);
            this.auctionManager.removeItem(StorageType.LISTED, item);

            Consumer<Long> applyExpiration = expiration -> this.plugin.getScheduler().runNextTick(w -> {
                long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                item.setExpiredAt(new Date(expiredAt));

                this.auctionManager.addItem(StorageType.EXPIRED, item);
                storageManager.updateItem(item, StorageType.EXPIRED);
            });

            var onlinePlayer = offlineSeller.isOnline() ? offlineSeller.getPlayer() : null;
            if (onlinePlayer != null) {
                var expiration = configuration.getExpireExpiration().getExpiration(onlinePlayer);
                applyExpiration.accept(expiration);
            } else {
                configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), offlineSeller)
                        .whenComplete((expiration, throwable) -> {
                            long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                            if (throwable != null) {
                                this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + offlineSeller.getName(), throwable);
                            }
                            applyExpiration.accept(safeExpiration);
                        });
            }

        } else {

            item.setStatus(ItemStatus.DELETED);
            storageManager.updateItem(item, StorageType.DELETED);
        }

        // Log expiration for debugging purposes
        if (this.plugin.getConfiguration().isEnableDebug()) {
            this.plugin.getLogger().info("Item " + item.getId() + " expired from " + storageType + " (seller: " + item.getSellerName() + ")");
        }
    }

    @Override
    public void processExpiredItems(List<Item> items, StorageType storageType) {
        if (items.isEmpty()) return;

        // Guard: filter out items already processed by another server (e.g., claimed via Redis cluster)
        List<Item> filtered = new ArrayList<>();
        for (Item item : items) {
            if (item.getStatus() == ItemStatus.DELETED) {
                this.auctionManager.removeItem(storageType, item);
            } else {
                filtered.add(item);
            }
        }
        if (filtered.isEmpty()) return;

        // Multi-server: route LISTED -> EXPIRED through the cluster-aware per-item path.
        if (storageType == StorageType.LISTED && this.plugin.getAuctionClusterBridge().isDistributed()) {
            for (Item item : filtered) {
                expireListedItemClustered(item);
            }
            return;
        }

        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();

        List<Item> validItems = filtered;
        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(validItems, storageType);
            event.callEvent();
        });

        this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);

        // Clear player caches for online sellers
        Set<OfflinePlayer> offlinePlayers = new HashSet<>();
        for (Item item : validItems) {
            var offlineSeller = item.getSeller();
            if (offlineSeller.isOnline() && !offlinePlayers.contains(offlineSeller)) {
                offlinePlayers.add(offlineSeller);
                var sellerPlayer = offlineSeller.getPlayer();
                if (sellerPlayer != null) {
                    this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
                }
            }
        }

        if (storageType == StorageType.LISTED) {
            // Items from LISTED storage go to EXPIRED
            List<Item> onlineSellerItems = new ArrayList<>();
            List<Item> offlineSellerItems = new ArrayList<>();

            // Remove all items from LISTED storage and separate by seller online status
            for (Item item : validItems) {
                item.setStatus(ItemStatus.REMOVED);
                this.auctionManager.removeItem(StorageType.LISTED, item);
                if (item.getSeller().isOnline()) {
                    onlineSellerItems.add(item);
                } else {
                    offlineSellerItems.add(item);
                }
            }

            // Process online sellers synchronously and batch update
            if (!onlineSellerItems.isEmpty()) {
                for (Item item : onlineSellerItems) {
                    var sellerPlayer = item.getSeller().getPlayer();
                    // Player may have disconnected between isOnline() check and now
                    long expiration = sellerPlayer != null
                            ? configuration.getExpireExpiration().getExpiration(sellerPlayer)
                            : configuration.getExpireExpiration().defaultExpiration();
                    long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
                    item.setExpiredAt(new Date(expiredAt));
                    this.auctionManager.addItem(StorageType.EXPIRED, item);
                }

                // Batch update all online seller items
                Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                batchUpdate.put(StorageType.EXPIRED, onlineSellerItems);
                storageManager.updateItems(batchUpdate);
            }

            // Process offline sellers asynchronously then batch update
            if (!offlineSellerItems.isEmpty()) {
                AtomicInteger remaining = new AtomicInteger(offlineSellerItems.size());
                List<Item> processedItems = new CopyOnWriteArrayList<>();

                for (Item item : offlineSellerItems) {
                    configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), item.getSeller())
                            .whenComplete((expiration, throwable) -> {
                                long safeExpiration = expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration();
                                if (throwable != null) {
                                    this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + item.getSeller().getName(), throwable);
                                }

                                long expiredAt = safeExpiration > 0 ? System.currentTimeMillis() + (safeExpiration * 1000) : 0;
                                item.setExpiredAt(new Date(expiredAt));

                                processedItems.add(item);
                                this.auctionManager.addItem(StorageType.EXPIRED, item);

                                // When all items are processed, batch update
                                if (remaining.decrementAndGet() == 0) {
                                    this.plugin.getScheduler().runNextTick(w -> {
                                        Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
                                        batchUpdate.put(StorageType.EXPIRED, new ArrayList<>(processedItems));
                                        storageManager.updateItems(batchUpdate);
                                    });
                                }
                            });
                }
            }

        } else {
            // Items from EXPIRED storage go to DELETED
            for (Item item : validItems) {
                item.setStatus(ItemStatus.DELETED);
            }

            // Batch update all items to DELETED
            Map<StorageType, List<Item>> batchUpdate = new EnumMap<>(StorageType.class);
            batchUpdate.put(StorageType.DELETED, validItems);
            storageManager.updateItems(batchUpdate);
        }
    }

    /**
     * Cluster-aware LISTED -> EXPIRED transition used in multi-server (distributed) setups.
     * <p>
     * Sequence: check availability -> acquire the cluster lock -> re-read authoritative DB state
     * -> if the item was sold/deleted on another server, drop the local ghost and skip; otherwise
     * perform the local move + DB update, then broadcast the removal (source=LISTED,
     * destination=EXPIRED) so other nodes converge -> release the lock.
     * <p>
     * Holding the lock across the whole operation serializes expiration against purchases and
     * against expiration on other nodes, so a sold item can never be resurrected as EXPIRED for
     * the seller. Items being purchased (cluster state LOCKED) or already handled by another node
     * are skipped silently and re-evaluated on a later sweep.
     */
    private void expireListedItemClustered(Item item) {
        // Coalesce concurrent dispatches for the same item id (see expiringItemIds). If an
        // expiration is already in flight, skip; it will be re-evaluated on a later sweep if needed.
        if (!this.expiringItemIds.add(item.getId())) {
            return;
        }
        var clusterBridge = this.plugin.getAuctionClusterBridge();
        var perf = this.plugin.getConfiguration().getPerformance();
        var storageManager = this.plugin.getStorageManager();
        var logger = this.plugin.getLogger();
        var tokenHolder = new AtomicReference<LockToken>();

        clusterBridge.checkAvailability(item)
                .orTimeout(perf.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                .thenCompose(available -> {
                    if (!available) {
                        // Item is locked (being purchased) on the cluster: do not expire now.
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    return clusterBridge.lockItem(item, item.getSellerUniqueId(), StorageType.LISTED)
                            .orTimeout(perf.lockItemTimeoutMs(), TimeUnit.MILLISECONDS)
                            .thenCompose(token -> {
                                tokenHolder.set(token);
                                if (LockToken.noop().value().equals(token.value())) {
                                    // Another server is already processing this item.
                                    return CompletableFuture.<Void>completedFuture(null);
                                }
                                return storageManager.selectItem(item.getId())
                                        .orTimeout(perf.checkAvailabilityTimeoutMs(), TimeUnit.MILLISECONDS)
                                        .thenCompose(dbItem -> {
                                            if (dbItem == null || dbItem.getBuyerUniqueId() != null) {
                                                // Sold/deleted on another server: remove the local ghost, do NOT expire.
                                                this.plugin.getScheduler().runNextTick(w -> {
                                                    this.auctionManager.removeItem(StorageType.LISTED, item.getId());
                                                    this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_SEARCH);
                                                });
                                                return CompletableFuture.<Void>completedFuture(null);
                                            }
                                            // Genuinely still listed: perform the move locally, then broadcast.
                                            return performListedToExpired(item)
                                                    .thenCompose(v -> clusterBridge.removeItem(item, StorageType.LISTED, StorageType.EXPIRED)
                                                            .orTimeout(perf.notifyItemActionTimeoutMs(), TimeUnit.MILLISECONDS));
                                        });
                            });
                })
                .whenComplete((v, throwable) -> {
                    this.expiringItemIds.remove(item.getId());
                    if (throwable != null) {
                        logger.warning("Cluster-aware expiration skipped/failed for item " + item.getId() + ": " + throwable.getMessage());
                    }
                    var token = tokenHolder.get();
                    if (token != null && !LockToken.noop().value().equals(token.value())) {
                        clusterBridge.unlockItem(item, token, StorageType.LISTED).exceptionally(ex -> {
                            logger.severe("Failed to unlock item " + item.getId() + " after expiration: " + ex.getMessage());
                            return null;
                        });
                    }
                });
    }

    /**
     * Performs the local LISTED -> EXPIRED move (fire event, clear caches, compute expiration,
     * mutate the item, update the in-memory stores and the database). Returns a future that
     * completes only after the database row has been updated, so the caller can keep the cluster
     * lock held until the change is durable.
     */
    private CompletableFuture<Void> performListedToExpired(Item item) {
        var configuration = this.plugin.getConfiguration();
        var storageManager = this.plugin.getStorageManager();
        var offlineSeller = item.getSeller();

        this.plugin.getScheduler().runNextTick(w -> {
            var event = new AuctionExpireEvent(List.of(item), StorageType.LISTED);
            event.callEvent();
            this.auctionManager.clearPlayersCache(PlayerCacheKey.ITEMS_LISTED, PlayerCacheKey.ITEMS_SEARCH);
            if (offlineSeller.isOnline()) {
                var sellerPlayer = offlineSeller.getPlayer();
                if (sellerPlayer != null) {
                    this.auctionManager.clearPlayerCache(sellerPlayer, PlayerCacheKey.ITEMS_SELLING, PlayerCacheKey.ITEMS_EXPIRED);
                }
            }
        });

        CompletableFuture<Long> expirationFuture;
        var onlinePlayer = offlineSeller.isOnline() ? offlineSeller.getPlayer() : null;
        if (onlinePlayer != null) {
            expirationFuture = CompletableFuture.completedFuture(configuration.getExpireExpiration().getExpiration(onlinePlayer));
        } else {
            expirationFuture = configuration.getExpireExpiration().getExpiration(this.plugin.getOfflinePermission(), offlineSeller)
                    .thenApply(expiration -> expiration != null ? expiration : configuration.getExpireExpiration().defaultExpiration())
                    .exceptionally(throwable -> {
                        this.plugin.getLogger().log(Level.WARNING, "Cannot compute expiration for offline player " + offlineSeller.getName(), throwable);
                        return configuration.getExpireExpiration().defaultExpiration();
                    });
        }

        return expirationFuture.thenCompose(expiration -> {
            long expiredAt = expiration > 0 ? System.currentTimeMillis() + (expiration * 1000) : 0;
            CompletableFuture<Void> done = new CompletableFuture<>();
            this.plugin.getScheduler().runNextTick(w -> {
                var previousExpiredAt = item.getExpiredAt();
                item.setExpiredAt(new Date(expiredAt));
                storageManager.updateItem(item, StorageType.EXPIRED).whenComplete((u, t) -> {
                    if (t != null) {
                        // DB update failed: revert the expiredAt change and leave the item in the
                        // LISTED store unchanged so a later sweep retries. Never create a local
                        // EXPIRED phantom against a still-LISTED database row.
                        item.setExpiredAt(previousExpiredAt);
                        done.completeExceptionally(t);
                        return;
                    }
                    // DB row is now EXPIRED: apply the in-memory move on the main thread, only once
                    // the change is durable.
                    this.plugin.getScheduler().runNextTick(w2 -> {
                        item.setStatus(ItemStatus.REMOVED);
                        this.auctionManager.removeItem(StorageType.LISTED, item);
                        this.auctionManager.addItem(StorageType.EXPIRED, item);
                        done.complete(null);
                    });
                });
            });
            return done;
        });
    }
}
