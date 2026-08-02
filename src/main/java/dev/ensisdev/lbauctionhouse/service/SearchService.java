package dev.ensisdev.lbauctionhouse.service;

import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.data.SearchFilter;
import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;

import java.util.List;
import java.util.logging.Logger;

/**
 * İlan arama hizmeti — SearchFilter üzerinden gelişmiş arama yapar.
 * <p>
 * Komut ve GUI katmanlarının ortak arama mantığını tek yerde toplar.
 */
public class SearchService {

    private final LbAuctionHouse plugin;
    private final AuctionManager manager;
    private final AuctionData data;
    private final Logger logger;

    public SearchService(LbAuctionHouse plugin, AuctionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.data = manager.getData();
        this.logger = manager.getApi().getLogger();
    }

    /**
     * Filtreye uyan ilanları döndürür (en fazla 100).
     */
    public List<AuctionListing> search(SearchFilter filter) {
        List<AuctionListing> results = data.searchListingsFiltered(filter);
        logger.fine("Arama: " + filter + " → " + results.size() + " sonuç");
        return results;
    }

    /**
     * Filtreye uyan toplam ilan sayısı.
     */
    public int count(SearchFilter filter) {
        return data.searchListingsFilteredCount(filter);
    }

    /**
     * Basit metin araması — seller: prefix desteği ile.
     */
    public List<AuctionListing> searchText(String query) {
        return data.searchListings(query);
    }

    /**
     * Kullanıcı dostu arama argümanlarını SearchFilter'a çevirir.
     * <p>
     * Desteklenen sözdizimi:
     * <pre>
     *   /auction search elmas min:100 max:500 type:BIN ads:true
     *   /auction search seller:Notch
     * </pre>
     */
    public SearchFilter parseArgs(String[] args) {
        String query = null;
        String seller = null;
        String material = null;
        double minPrice = 0;
        double maxPrice = 0;
        String type = null;
        boolean advertisedOnly = false;

        for (String arg : args) {
            String lower = arg.toLowerCase();
            if (lower.startsWith("min:")) {
                minPrice = parseDouble(arg.substring(4), 0);
            } else if (lower.startsWith("max:")) {
                maxPrice = parseDouble(arg.substring(4), 0);
            } else if (lower.startsWith("type:")) {
                type = arg.substring(5).toUpperCase();
            } else if (lower.startsWith("seller:")) {
                seller = arg.substring(7);
            } else if (lower.startsWith("mat:")) {
                material = arg.substring(4).toUpperCase();
            } else if (lower.equals("ads") || lower.equals("ads:true")) {
                advertisedOnly = true;
            } else {
                query = (query == null) ? arg : query + " " + arg;
            }
        }
        return new SearchFilter(query, seller, material, minPrice, maxPrice, type, advertisedOnly);
    }

    private double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}