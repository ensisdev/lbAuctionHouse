package dev.ensisdev.lbauctionhouse.data;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.addon.AuctionAPI;
import dev.ensisdev.lbauctionhouse.core.data.DataManager;
import dev.ensisdev.lbauctionhouse.core.data.MySQLAdapter;
import dev.ensisdev.lbauctionhouse.core.data.StorageAdapter;

import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auction veri katmanı — Core DataManager üzerinden tablo oluşturma
 * ve CRUD işlemleri.
 * <p>
 * {@code auction_listings} ve {@code auction_collection} tablolarını yönetir.
 */
public class AuctionData {

    private final LbAuctionHouse plugin;
    private final DataManager dataManager;
    private final Logger logger;

    public AuctionData(LbAuctionHouse plugin, AuctionAPI api) {
        this.plugin = plugin;
        this.dataManager = api.getDataManager();
        this.logger = api.getLogger();
    }

    // ----------------------------------------------------------------
    // Tablo oluşturma
    // ----------------------------------------------------------------

    public void initTables() {
        if (!dataManager.isReady()) {
            logger.warning("DataManager hazır değil — tablolar oluşturulamadı.");
            return;
        }
        try {
            var adapter = dataManager.getAdapter();
            // MySQL'de TEXT 64KB ile sınırlıdır — NBT/skill içeren büyük item'lar için LONGTEXT (4GB) gerekir.
            // SQLite'ta TEXT sınırsızdır, aynı kalır.
            String itemDataCol = isMySQL() ? "LONGTEXT" : "TEXT";
            adapter.createTableIfNotExists("auction_listings",
                    "id TEXT PRIMARY KEY, " +
                    "seller_uuid TEXT NOT NULL, " +
                    "seller_name TEXT NOT NULL, " +
                    "item_data " + itemDataCol + " NOT NULL, " +
                    "display_name TEXT DEFAULT '', " +
                    "price REAL NOT NULL, " +
                    "starting_bid REAL DEFAULT 0, " +
                    "type TEXT NOT NULL DEFAULT 'BIN', " +
                    "listed_at INTEGER NOT NULL, " +
                    "expires_at INTEGER NOT NULL, " +
                    "sold INTEGER NOT NULL DEFAULT 0, " +
                    "buyer_name TEXT, " +
                    "buyer_uuid TEXT, " +
                    "rental_ends_at INTEGER DEFAULT 0, " +
                    "renew_count INTEGER DEFAULT 0, " +
                    "lore_text TEXT DEFAULT '', " +
                    "enchant_text TEXT DEFAULT ''");

            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN rental_ends_at INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN renew_count INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN lore_text TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN enchant_text TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN display_name TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN starting_bid REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN type TEXT NOT NULL DEFAULT 'BIN'"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN material TEXT DEFAULT ''"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN bin_price REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN sealed INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN flash_sale_ends_at INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN original_price REAL DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN expired INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { adapter.execute("ALTER TABLE auction_listings ADD COLUMN advertised INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            // Mevcut MySQL tablolarını LONGTEXT'e yükselt — eski TEXT sütunu 64KB sınırı veriyi kesebilir.
            if (isMySQL()) {
                try { adapter.execute("ALTER TABLE auction_listings MODIFY COLUMN item_data LONGTEXT NOT NULL"); } catch (Exception ignored) {}
                try { adapter.execute("ALTER TABLE auction_logs MODIFY COLUMN item_data LONGTEXT"); } catch (Exception ignored) {}
                try { adapter.execute("ALTER TABLE auction_collection MODIFY COLUMN item_data LONGTEXT"); } catch (Exception ignored) {}
            }

            adapter.createTableIfNotExists("auction_banned_players",
                    "uuid TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "banned_by TEXT, " +
                    "reason TEXT DEFAULT '', " +
                    "banned_at INTEGER NOT NULL");

            adapter.createTableIfNotExists("auction_logs",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "action TEXT NOT NULL, " +
                    "seller_uuid TEXT, " +
                    "seller_name TEXT, " +
                    "buyer_uuid TEXT, " +
                    "buyer_name TEXT, " +
                    "item_data " + itemDataCol + ", " +
                    "price REAL, " +
                    "tax REAL DEFAULT 0, " +
                    "timestamp INTEGER NOT NULL, " +
                    "listing_id TEXT");

            adapter.createTableIfNotExists("auction_bids",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "listing_id TEXT NOT NULL, " +
                    "bidder_uuid TEXT NOT NULL, " +
                    "bidder_name TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "timestamp INTEGER NOT NULL");

            adapter.createTableIfNotExists("auction_collection",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid TEXT NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "item_data " + itemDataCol + ", " +
                    "amount REAL, " +
                    "listing_id TEXT, " +
                    "claimed INTEGER NOT NULL DEFAULT 0, " +
                    "created_at INTEGER NOT NULL");

            adapter.createTableIfNotExists("auction_wishlists",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid TEXT NOT NULL, " +
                    "material TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "UNIQUE(player_uuid, material)");

            adapter.createTableIfNotExists("auction_autobids",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "listing_id TEXT NOT NULL, " +
                    "player_uuid TEXT NOT NULL, " +
                    "player_name TEXT NOT NULL, " +
                    "max_amount REAL NOT NULL, " +
                    "increment REAL NOT NULL DEFAULT 10, " +
                    "active INTEGER NOT NULL DEFAULT 1, " +
                    "UNIQUE(listing_id, player_uuid)");

            adapter.createTableIfNotExists("auction_player_options",
                    "player_uuid TEXT PRIMARY KEY, " +
                    "notify_on_action INTEGER NOT NULL DEFAULT 1, " +
                    "confirm_on_buy INTEGER NOT NULL DEFAULT 1, " +
                    "show_broadcasts INTEGER NOT NULL DEFAULT 1, " +
                    "updated_at INTEGER NOT NULL");

            // --------------------------------------------------------
            // İndeksler — sık sorgular için performans
            // --------------------------------------------------------
            createIndexIfNotExists(adapter, "auction_listings", "idx_listings_seller", "seller_uuid");
            createIndexIfNotExists(adapter, "auction_listings", "idx_listings_expires", "expires_at");
            createIndexIfNotExists(adapter, "auction_listings", "idx_listings_sold", "sold");
            createIndexIfNotExists(adapter, "auction_listings", "idx_listings_type", "type");
            createIndexIfNotExists(adapter, "auction_bids", "idx_bids_listing", "listing_id");
            createIndexIfNotExists(adapter, "auction_bids", "idx_bids_bidder", "bidder_uuid");
            createIndexIfNotExists(adapter, "auction_collection", "idx_collection_player", "player_uuid");
            createIndexIfNotExists(adapter, "auction_collection", "idx_collection_claimed", "claimed");
            createIndexIfNotExists(adapter, "auction_logs", "idx_logs_timestamp", "timestamp");
            createIndexIfNotExists(adapter, "auction_logs", "idx_logs_action", "action");
            createIndexIfNotExists(adapter, "auction_autobids", "idx_autobids_listing", "listing_id");
            createIndexIfNotExists(adapter, "auction_wishlists", "idx_wishlists_player", "player_uuid");

            logger.info("Veritabanı tabloları oluşturuldu.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Tablo oluşturma hatası", e);
        }
    }

    /**
     * İndeks yoksa oluşturur (mevcut index'e dokunmaz).
     */
    private void createIndexIfNotExists(StorageAdapter adapter, String table, String indexName, String column) {
        try {
            // SQLite destekli "CREATE INDEX IF NOT EXISTS"
            adapter.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + " (" + column + ")");
        } catch (SQLException e) {
            // Bazı veritabanlarında IF NOT EXISTS desteklenmeyebilir — hata varsa yut
            logger.warning("İndeks oluşturulamadı " + indexName + ": " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // İlan CRUD
    // ----------------------------------------------------------------

    public void insertListing(AuctionListing listing) {
        try {
            dataManager.getAdapter().execute(
                "INSERT INTO auction_listings (id, seller_uuid, seller_name, item_data, display_name, price, starting_bid, type, listed_at, expires_at, sold, rental_ends_at, material, flash_sale_ends_at, original_price, bin_price, advertised, lore_text, enchant_text) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?,?,?,?)",
                listing.id().toString(),
                listing.sellerUUID().toString(),
                listing.sellerName(),
                serializeItem(listing.item()),
                listing.item().getItemMeta().hasDisplayName()
                        ? listing.item().getItemMeta().getDisplayName()
                        : listing.item().getType().name(),
                listing.price(),
                listing.startingBid(),
                listing.type(),
                listing.listedAt(),
                listing.expiresAt(),
                0,
                listing.item().getType().name(),
                listing.flashSaleEndsAt(),
                listing.originalPrice(),
                listing.binPrice(),
                listing.isAdvertised() ? 1 : 0,
                extractLoreText(listing.item()),
                extractEnchantText(listing.item())
            );
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "İlan ekleme hatası", e);
        }
    }

    public void markSold(UUID listingId, String buyerName, UUID buyerUUID) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_listings SET sold = 1, buyer_name = ?, buyer_uuid = ? WHERE id = ?",
                buyerName, buyerUUID.toString(), listingId.toString()
            );
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "İlan satılma hatası", e);
        }
    }

    /**
     * Atomik satış claim'i — ilan yalnızca hâlâ satılmamışsa satılır.
     * <p>
     * ÇAPRAZ SUNUCU (MySQL) senaryosunda iki sunucu aynı ilanı aynı anda
     * satın almaya çalışırsa, yalnızca biri başarılı sayılır (UPDATE ... AND sold=0).
     *
     * @return bu sunucu ilanı claim edebildi mi
     */
    public boolean markSoldIfAvailable(UUID listingId) {
        try (Connection conn = dataManager.getAdapter().getConnection();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE auction_listings SET sold = 1 WHERE id = ? AND sold = 0")) {
            st.setString(1, listingId.toString());
            return st.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Atomik satış claim hatası", e);
        }
        return false;
    }

    /**
     * Atomik claim başarılı ama sonrası (para çekme vb.) başarısız olduysa geri alır.
     */
    public void undoSold(UUID listingId) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_listings SET sold = 0 WHERE id = ?", listingId.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Satış claim geri alma hatası", e);
        }
    }

    public void deleteListing(UUID listingId) {
        try {
            dataManager.getAdapter().execute(
                "DELETE FROM auction_listings WHERE id = ?", listingId.toString());
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "İlan silme hatası", e);
        }
    }

    public void markExpired(UUID listingId) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_listings SET expired = 1 WHERE id = ?", listingId.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Expired işareti hatası", e);
        }
    }

    public AuctionListing getListing(UUID listingId) {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT * FROM auction_listings WHERE id = ?", listingId.toString());
            if (!results.isEmpty()) {
                var row = results.get(0);
                return rowToListing(row);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "İlan sorgulama hatası", e);
        }
        return null;
    }

    public List<AuctionListing> getActiveListings() {
        return getListings("SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0");
    }

    /**
     * Reklamlı aktif ilanlar — periyodik duyuru görevi tarafından kullanılır.
     */
    public List<AuctionListing> getActiveAdvertisedListings() {
        return getListings("SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 AND advertised = 1");
    }

    /**
     * Sayfalı aktif ilan listesi — sadece istenen sayfayı döndürür.
     * @param limit  sayfa başına ilan sayısı
     * @param offset başlangıç indeksi (page * limit)
     */
    public List<AuctionListing> getActiveListingsPage(int limit, int offset) {
        return getListings("SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 ORDER BY listed_at DESC LIMIT ? OFFSET ?", limit, offset);
    }

    /**
     * Sayfalı arama — sadece eşleşen ilanları sayfalı döndürür.
     */
    public List<AuctionListing> searchListingsPage(String query, int limit, int offset) {
        String q = "%" + query.toLowerCase() + "%";
        return getListings(
            "SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 AND " +
            "(LOWER(display_name) LIKE ? OR LOWER(lore_text) LIKE ? OR LOWER(enchant_text) LIKE ?) " +
            "ORDER BY listed_at DESC LIMIT ? OFFSET ?",
            q, q, q, limit, offset);
    }

    /**
     * Toplam aktif ilan sayısı (sayfalama için).
     */
    public int getActiveListingsCount() {
        return getCount("SELECT COUNT(*) FROM auction_listings WHERE sold = 0 AND expired = 0");
    }

    /**
     * Arama filtresine uyan toplam ilan sayısı.
     */
    public int getActiveListingsCount(String search) {
        String q = "%" + search.toLowerCase() + "%";
        return getCount("SELECT COUNT(*) FROM auction_listings WHERE sold = 0 AND expired = 0 AND " +
                "(LOWER(display_name) LIKE ? OR LOWER(lore_text) LIKE ? OR LOWER(enchant_text) LIKE ?)",
                q, q, q);
    }

    private int getCount(String sql, Object... params) {
        try {
            var results = dataManager.getAdapter().queryList(sql, params);
            if (!results.isEmpty()) return ((Number) results.get(0).values().iterator().next()).intValue();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Sayım sorgulama hatası", e);
        }
        return 0;
    }

    public List<AuctionListing> getActiveListingsBySeller(UUID sellerUUID) {
        return getListings(
            "SELECT * FROM auction_listings WHERE seller_uuid = ? AND sold = 0 AND expired = 0",
            sellerUUID.toString());
    }

    public List<AuctionListing> getExpiredListings() {
        long now = System.currentTimeMillis();
        return getListings(
            "SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 AND expires_at <= ?",
            now);
    }

    public int getActiveCountBySeller(UUID sellerUUID) {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_listings WHERE seller_uuid = ? AND sold = 0 AND expired = 0",
                sellerUUID.toString());
            if (!results.isEmpty())
                return ((Number) results.get(0).get("c")).intValue();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "İlan sayısı sorgulama hatası", e);
        }
        return 0;
    }

    public int getActiveFlashSaleCount(UUID sellerUUID) {
        long now = System.currentTimeMillis();
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_listings WHERE seller_uuid = ? AND sold = 0 AND expired = 0 AND flash_sale_ends_at > ?",
                sellerUUID.toString(), now);
            if (!results.isEmpty())
                return ((Number) results.get(0).get("c")).intValue();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Flash sale sayısı sorgulama hatası", e);
        }
        return 0;
    }

    public int getActiveAdvertisedCountBySeller(UUID sellerUUID) {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_listings WHERE seller_uuid = ? AND sold = 0 AND expired = 0 AND advertised = 1",
                sellerUUID.toString());
            if (!results.isEmpty())
                return ((Number) results.get(0).get("c")).intValue();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Reklamlı ilan sayısı sorgulama hatası", e);
        }
        return 0;
    }

    public List<AuctionListing> searchListings(String query) {
        // seller: prefix — satıcı adına göre ara
        if (query.toLowerCase().startsWith("seller:")) {
            String sellerName = query.substring(7).trim().toLowerCase();
            return getListings(
                "SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 AND LOWER(seller_name) LIKE ?",
                "%" + sellerName + "%");
        }
        String q = "%" + query.toLowerCase() + "%";
        return getListings(
            "SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 AND " +
            "(LOWER(display_name) LIKE ? OR LOWER(lore_text) LIKE ? OR LOWER(enchant_text) LIKE ?)",
            q, q, q);
    }

    /**
     * Gelişmiş filtreli arama — SearchFilter alanlarına göre dinamik SQL kurar.
     * <p>
     * Sadece aktif (satılmamış ve süresi dolmamış) ilanları döndürür.
     * Filtreler: anahtar kelime, satıcı, materyal, fiyat aralığı, tip, reklamlı.
     */
    public List<AuctionListing> searchListingsFiltered(SearchFilter filter) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0");
        List<Object> params = new ArrayList<>();

        if (filter.hasQuery()) {
            sql.append(" AND (LOWER(display_name) LIKE ? OR LOWER(material) LIKE ? OR LOWER(lore_text) LIKE ? OR LOWER(enchant_text) LIKE ?)");
            String q = "%" + filter.query().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (filter.hasSeller()) {
            sql.append(" AND LOWER(seller_name) LIKE ?");
            params.add("%" + filter.seller().toLowerCase() + "%");
        }
        if (filter.hasMaterial()) {
            sql.append(" AND LOWER(material) = ?");
            params.add(filter.material().toLowerCase());
        }
        if (filter.hasMinPrice()) {
            sql.append(" AND price >= ?");
            params.add(filter.minPrice());
        }
        if (filter.hasMaxPrice()) {
            sql.append(" AND price <= ?");
            params.add(filter.maxPrice());
        }
        if (filter.hasType()) {
            sql.append(" AND type = ?");
            params.add(filter.type().toUpperCase());
        }
        if (filter.advertisedOnly()) {
            sql.append(" AND advertised = 1");
        }

        sql.append(" ORDER BY listed_at DESC LIMIT 100");
        return getListings(sql.toString(), params.toArray());
    }

    /**
     * Filtreye uyan toplam ilan sayısı.
     */
    public int searchListingsFilteredCount(SearchFilter filter) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM auction_listings WHERE sold = 0 AND expired = 0");
        List<Object> params = new ArrayList<>();

        if (filter.hasQuery()) {
            sql.append(" AND (LOWER(display_name) LIKE ? OR LOWER(material) LIKE ? OR LOWER(lore_text) LIKE ? OR LOWER(enchant_text) LIKE ?)");
            String q = "%" + filter.query().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (filter.hasSeller()) {
            sql.append(" AND LOWER(seller_name) LIKE ?");
            params.add("%" + filter.seller().toLowerCase() + "%");
        }
        if (filter.hasMaterial()) {
            sql.append(" AND LOWER(material) = ?");
            params.add(filter.material().toLowerCase());
        }
        if (filter.hasMinPrice()) {
            sql.append(" AND price >= ?");
            params.add(filter.minPrice());
        }
        if (filter.hasMaxPrice()) {
            sql.append(" AND price <= ?");
            params.add(filter.maxPrice());
        }
        if (filter.hasType()) {
            sql.append(" AND type = ?");
            params.add(filter.type().toUpperCase());
        }
        if (filter.advertisedOnly()) {
            sql.append(" AND advertised = 1");
        }

        return getCount(sql.toString(), params.toArray());
    }

    public List<AuctionListing> searchListingsBySeller(String sellerName) {
        return getListings(
            "SELECT * FROM auction_listings WHERE sold = 0 AND expired = 0 AND LOWER(seller_name) LIKE ?",
            "%" + sellerName.toLowerCase() + "%");
    }

    private List<AuctionListing> getListings(String sql, Object... params) {
        List<AuctionListing> listings = new ArrayList<>();
        try {
            var results = dataManager.getAdapter().queryList(sql, params);
            for (var row : results) {
                AuctionListing listing = rowToListing(row);
                if (listing != null) listings.add(listing);
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "İlan listeleme hatası", e);
        }
        return listings;
    }

    // ----------------------------------------------------------------
    // Collection Box
    // ----------------------------------------------------------------

    public void addToCollection(UUID playerUUID, String type, ItemStack item, double amount, UUID listingId) {
        try {
            dataManager.getAdapter().execute(
                "INSERT INTO auction_collection (player_uuid, type, item_data, amount, listing_id, claimed, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 0, ?)",
                playerUUID.toString(), type,
                item != null ? serializeItem(item) : null,
                amount,
                listingId.toString(),
                System.currentTimeMillis()
            );
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Koleksiyon ekleme hatası", e);
        }
    }

    public List<CollectionEntry> getUnclaimedCollection(UUID playerUUID) {
        List<CollectionEntry> entries = new ArrayList<>();
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT * FROM auction_collection WHERE player_uuid = ? AND claimed = 0 ORDER BY created_at",
                playerUUID.toString());
            for (var row : results) {
                String type = (String) row.get("type");
                ItemStack item = row.get("item_data") != null
                        ? deserializeItem((String) row.get("item_data")) : null;
                double amount = row.get("amount") != null ? ((Number) row.get("amount")).doubleValue() : 0;
                int id = ((Number) row.get("id")).intValue();
                entries.add(new CollectionEntry(id, type, item, amount));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Koleksiyon sorgulama hatası", e);
        }
        return entries;
    }

    public void markClaimed(int entryId) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_collection SET claimed = 1 WHERE id = ?", entryId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Claim işareti hatası", e);
        }
    }

    /**
     * Koleksiyon kutusundan belirli bir girdiyi kaldırır.
     * @param entryId silinecek girdinin ID'si
     */
    public void removeFromCollection(int entryId) {
        try {
            dataManager.getAdapter().execute(
                "DELETE FROM auction_collection WHERE id = ? AND claimed = 0", entryId);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Koleksiyon girdisi silme hatası", e);
        }
    }

    public int getUnclaimedCount(UUID playerUUID) {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_collection WHERE player_uuid = ? AND claimed = 0",
                playerUUID.toString());
            if (!results.isEmpty())
                return ((Number) results.get(0).get("c")).intValue();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Claim sayısı hatası", e);
        }
        return 0;
    }

    // ----------------------------------------------------------------
    // Teklif Sistemi (Bidding)
    // ----------------------------------------------------------------

    public void insertBid(UUID listingId, UUID bidderUUID, String bidderName, double amount) {
        try {
            dataManager.getAdapter().execute(
                "INSERT INTO auction_bids (listing_id, bidder_uuid, bidder_name, amount, timestamp) VALUES (?,?,?,?,?)",
                listingId.toString(), bidderUUID.toString(), bidderName, amount, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Teklif ekleme hatası", e);
        }
    }

    public List<AuctionBid> getBids(UUID listingId) {
        List<AuctionBid> bids = new ArrayList<>();
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT * FROM auction_bids WHERE listing_id = ? ORDER BY timestamp ASC",
                listingId.toString());
            for (var row : results) {
                bids.add(new AuctionBid(
                    ((Number) row.get("id")).longValue(),
                    UUID.fromString((String) row.get("listing_id")),
                    UUID.fromString((String) row.get("bidder_uuid")),
                    (String) row.get("bidder_name"),
                    ((Number) row.get("amount")).doubleValue(),
                    ((Number) row.get("timestamp")).longValue()
                ));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Teklif sorgulama hatası", e);
        }
        return bids;
    }

    public AuctionBid getHighestBid(UUID listingId) {
        try {
            var results = dataManager.getAdapter().queryList(
                "SELECT * FROM auction_bids WHERE listing_id = ? ORDER BY amount DESC LIMIT 1",
                listingId.toString());
            if (!results.isEmpty()) {
                var row = results.get(0);
                return new AuctionBid(
                    ((Number) row.get("id")).longValue(),
                    UUID.fromString((String) row.get("listing_id")),
                    UUID.fromString((String) row.get("bidder_uuid")),
                    (String) row.get("bidder_name"),
                    ((Number) row.get("amount")).doubleValue(),
                    ((Number) row.get("timestamp")).longValue()
                );
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "En yüksek teklif sorgulama hatası", e);
        }
        return null;
    }

    public void updateListingPrice(UUID listingId, double newPrice) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_listings SET price = ? WHERE id = ?",
                newPrice, listingId.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Fiyat güncelleme hatası", e);
        }
    }

    public void updateRentalEnd(UUID listingId, long endsAt) {
        try { dataManager.getAdapter().execute("UPDATE auction_listings SET rental_ends_at = ? WHERE id = ?", endsAt, listingId.toString());
        } catch (SQLException e) { logger.log(Level.WARNING, "Kira bitiş güncelleme hatası", e); }
    }

    public List<AuctionListing> getExpiredRentals() {
        long now = System.currentTimeMillis();
        return getListings("SELECT * FROM auction_listings WHERE type = 'RENT' AND rental_ends_at > 0 AND rental_ends_at <= ?", now);
    }

    // ----------------------------------------------------------------
    // Log Sistemi
    // ----------------------------------------------------------------

    public void insertLog(AuctionLog.Action action, String sellerUUID, String sellerName,
                           String buyerUUID, String buyerName, ItemStack item,
                           double price, double tax, String listingId) {
        try {
            dataManager.getAdapter().execute(
                "INSERT INTO auction_logs (action, seller_uuid, seller_name, buyer_uuid, buyer_name, " +
                "item_data, price, tax, timestamp, listing_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
                action.name(), sellerUUID, sellerName, buyerUUID, buyerName,
                item != null ? serializeItem(item) : null, price, tax,
                System.currentTimeMillis(), listingId
            );
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Log kaydı hatası", e);
        }
    }

    /**
     * Toplu log ekleme — Batch INSERT ile tek seferde çoklu log kaydı.
     * Transaction içinde çalışır, ya hep ya hiç.
     */
    public void insertLogsBatch(List<LogBatchEntry> entries) {
        if (entries.isEmpty()) return;
        String sql = "INSERT INTO auction_logs (action, seller_uuid, seller_name, buyer_uuid, buyer_name, " +
                     "item_data, price, tax, timestamp, listing_id) VALUES (?,?,?,?,?,?,?,?,?,?)";
        long now = System.currentTimeMillis();
        try (Connection conn = dataManager.getAdapter().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (LogBatchEntry e : entries) {
                stmt.setString(1, e.action);
                stmt.setString(2, e.sellerUUID);
                stmt.setString(3, e.sellerName);
                stmt.setString(4, e.buyerUUID);
                stmt.setString(5, e.buyerName);
                stmt.setString(6, e.itemData);
                stmt.setDouble(7, e.price);
                stmt.setDouble(8, e.tax);
                stmt.setLong(9, now);
                stmt.setString(10, e.listingId);
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.setAutoCommit(true);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Toplu log ekleme hatası (" + entries.size() + " kayıt)", ex);
        }
    }

    public record LogBatchEntry(String action, String sellerUUID, String sellerName,
                                String buyerUUID, String buyerName, String itemData,
                                double price, double tax, String listingId) {}


    public List<AuctionLog> getRecentLogs(int limit) {
        return queryLogs("SELECT * FROM auction_logs ORDER BY timestamp DESC LIMIT ?", limit);
    }

    // ----------------------------------------------------------------
    // Price History
    // ----------------------------------------------------------------

    /**
     * Belirli bir material tipi için ortalama fiyat bilgisi.
     */
    public PriceInfo getPriceInfo(String materialName) {
        int totalSales = 0;
        double avgPrice = 0, maxPrice = 0, minPrice = Double.MAX_VALUE;
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c, AVG(price) AS avg, MAX(price) AS max, MIN(price) AS min FROM auction_logs WHERE action = 'PURCHASE' AND item_data LIKE ?",
                "%" + materialName + "%");
            if (!rows.isEmpty()) {
                totalSales = ((Number) rows.get(0).get("c")).intValue();
                avgPrice = rows.get(0).get("avg") != null ? ((Number) rows.get(0).get("avg")).doubleValue() : 0;
                maxPrice = rows.get(0).get("max") != null ? ((Number) rows.get(0).get("max")).doubleValue() : 0;
                minPrice = rows.get(0).get("min") != null ? ((Number) rows.get(0).get("min")).doubleValue() : 0;
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Fiyat geçmişi sorgulama hatası", e);
        }
        return new PriceInfo(totalSales, avgPrice, maxPrice, minPrice == Double.MAX_VALUE ? 0 : minPrice);
    }

    public record PriceInfo(int totalSales, double avgPrice, double maxPrice, double minPrice) {
        public boolean hasData() { return totalSales > 0; }
    }

    public List<AuctionLog> queryLogs(String sql, Object... params) {
        List<AuctionLog> logs = new ArrayList<>();
        try {
            var results = dataManager.getAdapter().queryList(sql, params);
            for (var row : results) {
                logs.add(new AuctionLog(
                    ((Number) row.get("id")).longValue(),
                    (String) row.get("action"),
                    (String) row.get("seller_uuid"),
                    (String) row.get("seller_name"),
                    (String) row.get("buyer_uuid"),
                    (String) row.get("buyer_name"),
                    row.get("item_data") != null ? deserializeItem((String) row.get("item_data")) : null,
                    row.get("price") != null ? ((Number) row.get("price")).doubleValue() : 0,
                    row.get("tax") != null ? ((Number) row.get("tax")).doubleValue() : 0,
                    ((Number) row.get("timestamp")).longValue(),
                    (String) row.get("listing_id")
                ));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Log sorgulama hatası", e);
        }
        return logs;
    }

    public void setAutoBid(UUID listingId, UUID playerUUID, String playerName, double maxAmount, double increment) {
        try {
            dataManager.getAdapter().execute(
                "INSERT OR REPLACE INTO auction_autobids (listing_id, player_uuid, player_name, max_amount, increment, active) VALUES (?,?,?,?,?,1)",
                listingId.toString(), playerUUID.toString(), playerName, maxAmount, increment);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Auto-bid ekleme hatası", e);
        }
    }

    public void removeAutoBid(UUID listingId, UUID playerUUID) {
        try {
            dataManager.getAdapter().execute(
                "DELETE FROM auction_autobids WHERE listing_id = ? AND player_uuid = ?",
                listingId.toString(), playerUUID.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Auto-bid silme hatası", e);
        }
    }

    public List<AutoBid> getActiveAutoBids(UUID listingId) {
        List<AutoBid> result = new ArrayList<>();
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT * FROM auction_autobids WHERE listing_id = ? AND active = 1 ORDER BY max_amount DESC",
                listingId.toString());
            for (var row : rows) {
                result.add(new AutoBid(
                    UUID.fromString((String) row.get("listing_id")),
                    UUID.fromString((String) row.get("player_uuid")),
                    (String) row.get("player_name"),
                    ((Number) row.get("max_amount")).doubleValue(),
                    ((Number) row.get("increment")).doubleValue()
                ));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Auto-bid sorgulama hatası", e);
        }
        return result;
    }

    public boolean hasAutoBid(UUID listingId, UUID playerUUID) {
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT 1 FROM auction_autobids WHERE listing_id = ? AND player_uuid = ? AND active = 1",
                listingId.toString(), playerUUID.toString());
            return !rows.isEmpty();
        } catch (SQLException e) {
            return false;
        }
    }

    public record AutoBid(UUID listingId, UUID playerUUID, String playerName, double maxAmount, double increment) {
        public boolean canBid(double currentPrice) {
            return currentPrice + increment <= maxAmount;
        }
    }

    // ----------------------------------------------------------------
    // Wishlist
    // ----------------------------------------------------------------

    public void addWishlist(UUID playerUUID, String materialName) {
        try {
            dataManager.getAdapter().execute(
                "INSERT OR IGNORE INTO auction_wishlists (player_uuid, material, created_at) VALUES (?,?,?)",
                playerUUID.toString(), materialName, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Wishlist ekleme hatası", e);
        }
    }

    public void removeWishlist(UUID playerUUID, String materialName) {
        try {
            dataManager.getAdapter().execute(
                "DELETE FROM auction_wishlists WHERE player_uuid = ? AND material = ?",
                playerUUID.toString(), materialName);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Wishlist silme hatası", e);
        }
    }

    public boolean isWishlisted(UUID playerUUID, String materialName) {
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT 1 FROM auction_wishlists WHERE player_uuid = ? AND material = ?",
                playerUUID.toString(), materialName);
            return !rows.isEmpty();
        } catch (SQLException e) {
            return false;
        }
    }

    public List<String> getWishlistedMaterials(UUID playerUUID) {
        List<String> result = new ArrayList<>();
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT material FROM auction_wishlists WHERE player_uuid = ? ORDER BY created_at",
                playerUUID.toString());
            for (var row : rows) result.add((String) row.get("material"));
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Wishlist sorgulama hatası", e);
        }
        return result;
    }

    /**
     * Wishlist'teki bir materyal için listeleyen satıcı UUID'lerini döndürür.
     */
    public List<UUID> getWishlistWatchers(String materialName) {
        List<UUID> result = new ArrayList<>();
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT player_uuid FROM auction_wishlists WHERE material = ?", materialName);
            for (var row : rows) result.add(UUID.fromString((String) row.get("player_uuid")));
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Wishlist izleyici sorgulama hatası", e);
        }
        return result;
    }

    // ----------------------------------------------------------------
    // Async Wrappers — Core'un AsyncStorageWrapper'ı üzerinden
    // ----------------------------------------------------------------

    public CompletableFuture<Void> insertListingAsync(AuctionListing listing) {
        return dataManager.async().executeAsync(
            "INSERT INTO auction_listings (id, seller_uuid, seller_name, item_data, display_name, price, starting_bid, type, listed_at, expires_at, sold, rental_ends_at, material, flash_sale_ends_at, original_price, bin_price, advertised, lore_text, enchant_text) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,0,0,?,?,?,?,?,?,?)",
            listing.id().toString(), listing.sellerUUID().toString(), listing.sellerName(),
            serializeItem(listing.item()),
            listing.item().getItemMeta().hasDisplayName() ? listing.item().getItemMeta().getDisplayName() : listing.item().getType().name(),
            listing.price(), listing.startingBid(), listing.type(), listing.listedAt(), listing.expiresAt(),
            listing.item().getType().name(), listing.flashSaleEndsAt(), listing.originalPrice(),
            listing.binPrice(), listing.isAdvertised() ? 1 : 0,
            extractLoreText(listing.item()), extractEnchantText(listing.item())
        );
    }

    public CompletableFuture<Void> markSoldAsync(UUID listingId, String buyerName, UUID buyerUUID) {
        return dataManager.async().executeAsync(
            "UPDATE auction_listings SET sold = 1, buyer_name = ?, buyer_uuid = ? WHERE id = ?",
            buyerName, buyerUUID.toString(), listingId.toString());
    }

    public CompletableFuture<Void> deleteListingAsync(UUID listingId) {
        return dataManager.async().executeAsync(
            "DELETE FROM auction_listings WHERE id = ?", listingId.toString());
    }

    public CompletableFuture<Void> markExpiredAsync(UUID listingId) {
        return dataManager.async().executeAsync(
            "UPDATE auction_listings SET expired = 1 WHERE id = ?", listingId.toString());
    }

    /**
     * Belirli günden eski expired ilanları temizler.
     */
    public CompletableFuture<Void> cleanOldExpired(int days) {
        long cutoff = System.currentTimeMillis() - (days * 86400_000L);
        return dataManager.async().executeAsync(
            "DELETE FROM auction_listings WHERE expired = 1 AND expires_at < ?", cutoff);
    }

    /**
     * Oyuncunun expired ilanlarını döndürür (tarihçe için).
     */
    public List<AuctionListing> getExpiredListingsByPlayer(UUID playerUUID) {
        return getListings("SELECT * FROM auction_listings WHERE seller_uuid = ? AND expired = 1 ORDER BY expires_at DESC LIMIT 50",
                playerUUID.toString());
    }

    public CompletableFuture<Void> updateListingPriceAsync(UUID listingId, double newPrice) {
        return dataManager.async().executeAsync(
            "UPDATE auction_listings SET price = ? WHERE id = ?", newPrice, listingId.toString());
    }

    public void updateExpiresAt(UUID listingId, long newExpiry) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_listings SET expires_at = ? WHERE id = ?", newExpiry, listingId.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Süre uzatma hatası", e);
        }
    }

    /**
     * İlanın kaç kez otomatik yenilendiğini döndürür (auto-relist sayacı).
     */
    public int getRenewCount(UUID listingId) {
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT renew_count FROM auction_listings WHERE id = ?", listingId.toString());
            if (!rows.isEmpty()) {
                Object v = rows.get(0).get("renew_count");
                return v == null ? 0 : ((Number) v).intValue();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "renew_count sorgulama hatası", e);
        }
        return 0;
    }

    /**
     * İlanın auto-relist sayacını 1 artırır.
     */
    public void incrementRenewCount(UUID listingId) {
        try {
            dataManager.getAdapter().execute(
                "UPDATE auction_listings SET renew_count = renew_count + 1 WHERE id = ?", listingId.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "renew_count güncelleme hatası", e);
        }
    }

    public CompletableFuture<Void> insertBidAsync(UUID listingId, UUID bidderUUID, String bidderName, double amount) {
        return dataManager.async().executeAsync(
            "INSERT INTO auction_bids (listing_id, bidder_uuid, bidder_name, amount, timestamp) VALUES (?,?,?,?,?)",
            listingId.toString(), bidderUUID.toString(), bidderName, amount, System.currentTimeMillis());
    }

    public CompletableFuture<Void> insertLogAsync(String action, String sellerUUID, String sellerName,
                                                   String buyerUUID, String buyerName, ItemStack item,
                                                   double price, double tax, String listingId) {
        return dataManager.async().executeAsync(
            "INSERT INTO auction_logs (action, seller_uuid, seller_name, buyer_uuid, buyer_name, item_data, price, tax, timestamp, listing_id) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?)",
            action, sellerUUID, sellerName, buyerUUID, buyerName,
            item != null ? serializeItem(item) : null, price, tax, System.currentTimeMillis(), listingId);
    }

    public CompletableFuture<Void> addToCollectionAsync(UUID playerUUID, String type, ItemStack item, double amount, UUID listingId) {
        return dataManager.async().executeAsync(
            "INSERT INTO auction_collection (player_uuid, type, item_data, amount, listing_id, claimed, created_at) VALUES (?,?,?,?,?,0,?)",
            playerUUID.toString(), type, item != null ? serializeItem(item) : null, amount, listingId.toString(), System.currentTimeMillis());
    }

    // ----------------------------------------------------------------
    // Oyuncu Tercihleri (Options)
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPlayerOptionsRow(UUID playerUUID) {
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT * FROM auction_player_options WHERE player_uuid = ?", playerUUID.toString());
            if (!rows.isEmpty()) return (Map<String, Object>) rows.get(0);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu tercih sorgulama hatası", e);
        }
        return null;
    }

    /**
     * Oyuncu tercihlerini döndürür (kayıt yoksa varsayılanlar).
     */
    public PlayerOptions getPlayerOptions(UUID playerUUID) {
        var row = getPlayerOptionsRow(playerUUID);
        if (row == null) return PlayerOptions.DEFAULTS;
        boolean notifyOnAction = row.get("notify_on_action") == null || ((Number) row.get("notify_on_action")).intValue() == 1;
        boolean confirmOnBuy = row.get("confirm_on_buy") == null || ((Number) row.get("confirm_on_buy")).intValue() == 1;
        boolean showBroadcasts = row.get("show_broadcasts") == null || ((Number) row.get("show_broadcasts")).intValue() == 1;
        return new PlayerOptions(notifyOnAction, confirmOnBuy, showBroadcasts);
    }

    /**
     * Oyuncu tercihlerini kaydeder (yoksa oluşturur).
     */
    public void setPlayerOptions(UUID playerUUID, PlayerOptions options) {
        try {
            dataManager.getAdapter().execute(
                "INSERT OR REPLACE INTO auction_player_options (player_uuid, notify_on_action, confirm_on_buy, show_broadcasts, updated_at) VALUES (?,?,?,?,?)",
                playerUUID.toString(),
                options.notifyOnAction() ? 1 : 0,
                options.confirmOnBuy() ? 1 : 0,
                options.showBroadcasts() ? 1 : 0,
                System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu tercih kaydetme hatası", e);
        }
    }

    /**
     * Tek bir tercih anahtarını günceller.
     */
    public void updatePlayerOption(UUID playerUUID, String key, boolean value) {
        String column = switch (key) {
            case "notify_on_action" -> "notify_on_action";
            case "confirm_on_buy" -> "confirm_on_buy";
            case "show_broadcasts" -> "show_broadcasts";
            default -> throw new IllegalArgumentException("Bilinmeyen tercih anahtarı: " + key);
        };
        try {
            dataManager.getAdapter().execute(
                "INSERT INTO auction_player_options (player_uuid, notify_on_action, confirm_on_buy, show_broadcasts, updated_at) VALUES (?,1,1,1,?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET " + column + " = ?, updated_at = ?",
                playerUUID.toString(), System.currentTimeMillis(),
                value ? 1 : 0, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu tercih güncelleme hatası", e);
        }
    }

    public record PlayerOptions(boolean notifyOnAction, boolean confirmOnBuy, boolean showBroadcasts) {
        public static final PlayerOptions DEFAULTS = new PlayerOptions(true, true, true);
    }

    // ----------------------------------------------------------------
    // Oyuncu İstatistikleri
    // ----------------------------------------------------------------

    public PlayerStats getPlayerStats(UUID playerUUID) {
        long totalSold = 0, totalBought = 0;
        double totalEarned = 0, totalSpent = 0;
        try {
            var sold = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c, COALESCE(SUM(price - (price * tax / 100.0)), 0) AS total FROM auction_logs WHERE seller_uuid = ? AND action = 'PURCHASE'",
                playerUUID.toString());
            if (!sold.isEmpty()) {
                totalSold = ((Number) sold.get(0).get("c")).longValue();
                totalEarned = ((Number) sold.get(0).get("total")).doubleValue();
            }
            var bought = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c, COALESCE(SUM(price), 0) AS total FROM auction_logs WHERE buyer_uuid = ? AND action = 'PURCHASE'",
                playerUUID.toString());
            if (!bought.isEmpty()) {
                totalBought = ((Number) bought.get(0).get("c")).longValue();
                totalSpent = ((Number) bought.get(0).get("total")).doubleValue();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu istatistik sorgulama hatası", e);
        }
        return new PlayerStats(totalSold, totalBought, totalEarned, totalSpent);
    }

    public record PlayerStats(long totalSold, long totalBought, double totalEarned, double totalSpent) {}

    /**
     * Oyuncunun son N gündeki günlük satış verilerini döndürür (grafik için).
     * <p>
     * Sonuç eski günden yeni güne sıralıdır ve her gün için satış adedi
     * ile net kazanç (vergiler düşülmüş) bilgisini içerir.
     *
     * @param playerUUID oyuncu UUID'si
     * @param days       kaç gün geriye gidileceği
     * @return günlük satış verileri (eski → yeni, boş günler dahil)
     */
    public List<DailySales> getPlayerSalesChart(UUID playerUUID, int days) {
        long dayMillis = 86_400_000L;
        long cutoff = System.currentTimeMillis() - (long) days * dayMillis;
        List<DailySales> result = new ArrayList<>();
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT timestamp, price, tax FROM auction_logs WHERE seller_uuid = ? AND action = 'PURCHASE' AND timestamp >= ?",
                playerUUID.toString(), cutoff);

            // Gün başlangıcı (epoch) → [satış adedi, kazanç(kuruş)]
            Map<Long, long[]> byDay = new TreeMap<>();
            for (var row : rows) {
                long ts = ((Number) row.get("timestamp")).longValue();
                long dayStart = ts - (ts % dayMillis);
                double price = row.get("price") != null ? ((Number) row.get("price")).doubleValue() : 0;
                double tax = row.get("tax") != null ? ((Number) row.get("tax")).doubleValue() : 0;
                double revenue = price - (price * tax / 100.0);
                long[] agg = byDay.computeIfAbsent(dayStart, k -> new long[2]);
                agg[0]++;
                agg[1] += Math.round(revenue * 100);
            }

            // Boş günler dahil tüm günleri üret (eski → yeni)
            long todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % dayMillis);
            for (int i = days - 1; i >= 0; i--) {
                long dayStart = todayStart - (long) i * dayMillis;
                long[] agg = byDay.getOrDefault(dayStart, new long[2]);
                result.add(new DailySales(dayStart, (int) agg[0], agg[1] / 100.0));
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Satış grafiği sorgulama hatası", e);
        }
        return result;
    }

    /** Belirli bir günün satış özeti (grafik çizimi için). */
    public record DailySales(long dayStart, int count, double revenue) {}

    // ----------------------------------------------------------------
    // Oyuncu Ban / Kara Liste
    // ----------------------------------------------------------------

    public boolean isPlayerBanned(UUID playerUUID) {
        try {
            return !dataManager.getAdapter().queryList(
                "SELECT 1 FROM auction_banned_players WHERE uuid = ?", playerUUID.toString()).isEmpty();
        } catch (SQLException e) { return false; }
    }

    public void banPlayer(UUID uuid, String name, String bannedBy, String reason) {
        try {
            dataManager.getAdapter().execute(
                "INSERT OR REPLACE INTO auction_banned_players (uuid, name, banned_by, reason, banned_at) VALUES (?,?,?,?,?)",
                uuid.toString(), name, bannedBy, reason, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu ban hatası", e);
        }
    }

    public void unbanPlayer(UUID uuid) {
        try {
            dataManager.getAdapter().execute(
                "DELETE FROM auction_banned_players WHERE uuid = ?", uuid.toString());
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Oyuncu ban kaldırma hatası", e);
        }
    }

    public List<String[]> getBannedPlayers() {
        List<String[]> result = new ArrayList<>();
        try {
            var rows = dataManager.getAdapter().queryList(
                "SELECT uuid, name, reason, banned_at FROM auction_banned_players ORDER BY banned_at DESC");
            for (var row : rows) {
                result.add(new String[]{
                    (String) row.get("uuid"),
                    (String) row.get("name"),
                    (String) row.get("reason"),
                    String.valueOf(row.get("banned_at"))
                });
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Ban listesi sorgulama hatası", e);
        }
        return result;
    }

    // ----------------------------------------------------------------
    // İstatistikler (Admin)
    // ----------------------------------------------------------------

    public AuctionStats getStats() {
        long totalSales = 0;
        double totalRevenue = 0;
        double totalTax = 0;
        int totalListings = 0;
        int activeListings = 0;
        int totalBids = 0;

        try {
            var saleCount = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_logs WHERE action = 'PURCHASE'");
            if (!saleCount.isEmpty()) totalSales = ((Number) saleCount.get(0).get("c")).longValue();

            var revenue = dataManager.getAdapter().queryList(
                "SELECT COALESCE(SUM(price * (1.0 - tax / 100.0)), 0) AS total FROM auction_logs WHERE action = 'PURCHASE'");
            if (!revenue.isEmpty()) totalRevenue = ((Number) revenue.get(0).get("total")).doubleValue();

            var tax = dataManager.getAdapter().queryList(
                "SELECT COALESCE(SUM(price * (tax / 100.0)), 0) AS total FROM auction_logs WHERE action = 'PURCHASE'");
            if (!tax.isEmpty()) totalTax = ((Number) tax.get(0).get("total")).doubleValue();

            var listingCount = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_listings");
            if (!listingCount.isEmpty()) totalListings = ((Number) listingCount.get(0).get("c")).intValue();

            var activeCount = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_listings WHERE sold = 0 AND expired = 0");
            if (!activeCount.isEmpty()) activeListings = ((Number) activeCount.get(0).get("c")).intValue();

            var bidCount = dataManager.getAdapter().queryList(
                "SELECT COUNT(*) AS c FROM auction_bids");
            if (!bidCount.isEmpty()) totalBids = ((Number) bidCount.get(0).get("c")).intValue();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "İstatistik sorgulama hatası", e);
        }

        return new AuctionStats(totalSales, totalRevenue, totalTax, totalListings, activeListings, totalBids);
    }

    public record AuctionStats(
            long totalSales,
            double totalRevenue,
            double totalTax,
            int totalListings,
            int activeListings,
            int totalBids
    ) {}

    public List<AuctionLog> getLogsByPlayer(String uuid, int limit) {
        return queryLogs(
            "SELECT * FROM auction_logs WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY timestamp DESC LIMIT ?",
            uuid, uuid, limit);
    }

    // ----------------------------------------------------------------
    // ItemStack Serialization (Paper serializeAsBytes / deserializeBytes)
    // ----------------------------------------------------------------

    /**
     * Eşyanın lore satırlarını tek metinde birleştirir (arama için).
     */
    private String extractLoreText(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return "";
        return String.join(" ", meta.getLore());
    }

    /**
     * Eşyanın büyülerini (enchant isimleri) tek metinde birleştirir (arama için).
     */
    private String extractEnchantText(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta == null || !meta.hasEnchants()) return "";
        return meta.getEnchants().keySet().stream()
                .map(e -> e.getKey().getKey())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String serializeItem(ItemStack item) {
        try {
            return Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Item serialize hatası", e);
            return "";
        }
    }

    private ItemStack deserializeItem(String data) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Item deserialize hatası", e);
            return null;
        }
    }

    private boolean isMySQL() {
        return dataManager.getAdapter() instanceof MySQLAdapter;
    }

    private AuctionListing rowToListing(Map<String, Object> row) {
        try {
            String type = row.get("type") != null ? (String) row.get("type") : "BIN";
            double startingBid = row.get("starting_bid") != null ? ((Number) row.get("starting_bid")).doubleValue() : 0;
            long flashSaleEndsAt = row.get("flash_sale_ends_at") != null ? ((Number) row.get("flash_sale_ends_at")).longValue() : 0;
            double originalPrice = row.get("original_price") != null ? ((Number) row.get("original_price")).doubleValue() : 0;
            boolean expired = row.get("expired") != null && ((Number) row.get("expired")).intValue() == 1;
            double binPrice = row.get("bin_price") != null ? ((Number) row.get("bin_price")).doubleValue() : 0;
            boolean sealed = row.get("sealed") != null && ((Number) row.get("sealed")).intValue() == 1;
            boolean advertised = row.get("advertised") != null && ((Number) row.get("advertised")).intValue() == 1;
            return new AuctionListing(
                UUID.fromString((String) row.get("id")),
                UUID.fromString((String) row.get("seller_uuid")),
                (String) row.get("seller_name"),
                deserializeItem((String) row.get("item_data")),
                ((Number) row.get("price")).doubleValue(),
                startingBid,
                type,
                ((Number) row.get("listed_at")).longValue(),
                ((Number) row.get("expires_at")).longValue(),
                ((Number) row.get("sold")).intValue() == 1,
                (String) row.get("buyer_name"),
                row.get("buyer_uuid") != null ? UUID.fromString((String) row.get("buyer_uuid")) : null,
                flashSaleEndsAt,
                originalPrice,
                expired,
                binPrice,
                sealed,
                advertised
            );
        } catch (Exception e) {
            logger.log(Level.WARNING, "Satır→Listing dönüşüm hatası", e);
            return null;
        }
    }

    public record CollectionEntry(int id, String type, ItemStack item, double amount) {}
}