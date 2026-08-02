package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.data.SearchFilter;
import dev.ensisdev.lbauctionhouse.service.SearchService;

import java.util.List;

/**
 * /auction search <sorgu> [min:X] [max:X] [type:BIN|BID|RENT] [seller:isim] [mat:MATERIAL] [ads]
 * <p>
 * Gelişmiş filtreli arama yapar ve sonuçları sohbette listeler.
 */
public class CmdSearch extends AuctionCmd {

    private SearchService searchService;

    public CmdSearch() {
        super("search", null, true);
        setAliases("ara", "bul", "filtrele");
        setDescription("İlanlarda gelişmiş arama yapar");
    }

    private SearchService searchService() {
        if (searchService == null) {
            searchService = new SearchService(plugin, manager);
        }
        return searchService;
    }

    @Override
    protected void execute() {
        if (!hasArg(0)) {
            msg("§cKullanım:");
            msg("§7/" + label + " search <eşya adı>");
            msg("§7Örnek: /" + label + " ara elmas");
            msg("§7Gelişmiş: [min:100] [max:500] [type:BIN] [seller:isim] [mat:DIAMOND] [ads]");
            return;
        }

        // Gelişmiş filtre sözdizimi varsa (min:100 gibi) sohbet araması yap;
        // yoksa ANA MENÜDEKİ arama GUI'sini aynı sorguyla aç.
        boolean hasFilters = java.util.Arrays.stream(args)
                .anyMatch(a -> a.matches("^(min|max|type|seller|mat|ads):.*"));

        if (!hasFilters) {
            String query = String.join(" ", args);
            if (player != null) {
                manager.openMainMenuWithSearch(player, query);
            }
            return;
        }

        SearchService service = searchService();
        SearchFilter filter = service.parseArgs(args);
        List<AuctionListing> results = service.search(filter);
        int count = service.count(filter);

        msg("§e§l━━━ İlan Arama Sonuçları §7(" + count + " ilan) §e§l━━━");
        if (results.isEmpty()) {
            msg("§7Arama kriterlerine uygun ilan bulunamadı.");
            return;
        }

        for (AuctionListing listing : results) {
            String typeColor = switch (listing.type()) {
                case "BIN" -> "§a";
                case "BID" -> "§6";
                case "RENT" -> "§b";
                default -> "§7";
            };
            String advertised = listing.isAdvertised() ? " §d★" : "";
            String name = listing.item() != null && listing.item().getItemMeta() != null
                    && listing.item().getItemMeta().hasDisplayName()
                    ? listing.item().getItemMeta().getDisplayName()
                    : (listing.item() != null ? listing.item().getType().name() : "?");
            msg("§7• " + typeColor + name + " §8— §e" + plugin.getAuctionEconomy().format(listing.price())
                    + " §8· " + listing.sellerName() + advertised);
        }
    }
}