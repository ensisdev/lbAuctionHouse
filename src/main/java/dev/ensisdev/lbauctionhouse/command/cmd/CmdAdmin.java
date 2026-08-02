package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionLog;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * /auction admin — admin paneli.
 * Alt komutlar: logs, clear, stats, remove &lt;uuid&gt;
 * <p>
 * Tüm mesajlar {@code messages.yml} üzerinden okunur.
 * Eğer dil anahtarı bulunamazsa varsayılan değerler gösterilir.
 */
public class CmdAdmin extends AuctionCmd {

    public CmdAdmin() {
        super("admin", "lbsmpcore.auction.admin", true);
        setAliases("adm", "yönet");
        setDescription("Admin paneli");
    }

    @Override
    protected void execute() {
        if (!hasArg(0)) {
            msg("§6=== Yönetim Paneli ===");
            msg("§7/" + label + " stats — İstatistikler");
            msg("§7/" + label + " logs — Son işlemleri göster");
            msg("§7/" + label + " clear — Tüm ilanları temizle");
            msg("§7/" + label + " remove <uuid> — İlan kaldır");
            msg("§7/" + label + " ban <oyuncu> [sebep] — Oyuncu yasakla");
            msg("§7/" + label + " unban <oyuncu> — Yasağı kaldır");
            msg("§7/" + label + " banlist — Yasaklı oyuncular");
            return;
        }

        // Admin alt-komut eşleşmeleri commands.yml / lang dosyasından çözülür.
        // Böylece sunucu sahibi admin alt komutlarını da değiştirebilir.
        String sub = arg(0).toLowerCase();
        if (config.isAdminSub("stats", sub)) {
            showStats();
        } else if (config.isAdminSub("logs", sub)) {
            showLogs();
        } else if (config.isAdminSub("clear", sub)) {
            clearAll();
        } else if (config.isAdminSub("remove", sub)) {
            removeListing();
        } else if (config.isAdminSub("ban", sub)) {
            if (!hasArg(1)) {
                msg("§cKullanım: /" + label + " ban <oyuncu> [sebep]");
                return;
            }
            banPlayer(arg(1), arg(2, "Sebep belirtilmedi"));
        } else if (config.isAdminSub("unban", sub)) {
            if (!hasArg(1)) {
                msg("§cKullanım: /" + label + " unban <oyuncu>");
                return;
            }
            unbanPlayer(arg(1));
        } else if (config.isAdminSub("banlist", sub)) {
            listBannedPlayers();
        } else if (config.isAdminSub("inspect", sub)) {
            inspectPlayer(hasArg(1) ? arg(1) : null);
        } else {
            msg("admin.unknown", "arg", arg(0));
        }
    }

    private void showStats() {
        var auctionData = manager.getData();
        AuctionData.AuctionStats stats = auctionData.getStats();
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        msg("§6=== İhale İstatistikleri ===");
        msg("admin.stats-header");
        msg("admin.stats-line",
                "label", messages.getRaw("admin.stats-total-sales"),
                "value", nf.format(stats.totalSales()));
        msg("admin.stats-line",
                "label", messages.getRaw("admin.stats-total-revenue"),
                "value", nf.format(stats.totalRevenue()));
        msg("admin.stats-line",
                "label", messages.getRaw("admin.stats-total-tax"),
                "value", nf.format(stats.totalTax()));
        msg("admin.stats-line",
                "label", messages.getRaw("admin.stats-total-listings"),
                "value", nf.format(stats.totalListings()));
        msg("admin.stats-line",
                "label", messages.getRaw("admin.stats-active-listings"),
                "value", nf.format(stats.activeListings()));
        msg("admin.stats-line",
                "label", messages.getRaw("admin.stats-total-bids"),
                "value", nf.format(stats.totalBids()));
    }

    private void showLogs() {
        int limit = 20;
        if (hasArg(1)) {
            try {
                limit = Integer.parseInt(arg(1));
            } catch (NumberFormatException e) {
                msg("admin.invalid-number");
                return;
            }
        }
        var auctionData = manager.getData();
        List<AuctionLog> logs = auctionData.queryLogs(
                "SELECT * FROM auction_logs ORDER BY timestamp DESC LIMIT ?", limit);

        if (logs.isEmpty()) {
            msg("admin.no-logs");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        msg("admin.logs-header", "count", String.valueOf(logs.size()));
        for (AuctionLog log : logs) {
            String date = sdf.format(new Date(log.timestamp()));
            String action = switch (log.action()) {
                case "SELL" -> "§aSATIŞ";
                case "PURCHASE" -> "§bALIŞ";
                case "CANCEL" -> "§eİPTAL";
                case "EXPIRED" -> "§7SÜRE DOLDU";
                case "ADMIN_REMOVE" -> "§cADMİN SİLDİ";
                default -> log.action();
            };
            String itemName = log.item() != null ? log.item().getType().name() : "?";
            msg(" §7#" + log.id() + " " + action + " §f" + itemName +
                    " §7- §6" + String.format("%,.0f", log.price()) + "₺" +
                    " §7(" + date + ")");
        }
    }

    private void clearAll() {
        int count = manager.clearAllListings();
        msg("admin.cleared", "count", String.valueOf(count));
    }

    private void removeListing() {
        if (!hasArg(1)) {
            msg("§cKullanım: /" + label + " remove <uuid>");
            return;
        }
        try {
            java.util.UUID uuid = java.util.UUID.fromString(arg(1));
            if (manager.removeListing(uuid)) {
                msg("admin.removed");
            } else {
                msg("admin.not-found");
            }
        } catch (IllegalArgumentException e) {
            msg("admin.invalid-uuid");
        }
    }

    // ---- Ban yardımcıları ----

    private void banPlayer(String targetName, String reason) {
        var target = org.bukkit.Bukkit.getPlayerExact(targetName);
        if (target == null) {
            msg("admin.ban.player-not-found");
            return;
        }
        manager.getData().banPlayer(target.getUniqueId(), target.getName(),
                sender.getName(), reason);
        msg("admin.ban.banned", "player", target.getName(), "reason", reason);
        if (target.isOnline()) {
            target.sendMessage(
                    messages.getPrefixed("admin.ban.you-were-banned", "reason", reason));
        }
    }

    private void unbanPlayer(String targetName) {
        var target = org.bukkit.Bukkit.getPlayerExact(targetName);
        java.util.UUID uuid;
        if (target != null) {
            uuid = target.getUniqueId();
        } else {
            try {
                uuid = java.util.UUID.fromString(targetName);
            } catch (IllegalArgumentException e) {
                msg("admin.ban.player-offline-no-uuid");
                return;
            }
        }
        manager.getData().unbanPlayer(uuid);
        msg("admin.ban.unbanned", "player", targetName);
    }

    private void listBannedPlayers() {
        var banned = manager.getData().getBannedPlayers();
        if (banned.isEmpty()) {
            msg("admin.ban.no-banned");
            return;
        }
        msg("admin.ban.banned-list-header", "count", String.valueOf(banned.size()));
        for (var entry : banned) {
            msg("§7" + entry[1] + " §7(" + entry[2] + ")");
        }
    }

    /**
     * Bir oyuncunun aktif ilanlarını listeler (admin inceleme).
     */
    private void inspectPlayer(String targetName) {
        if (targetName == null || targetName.isEmpty()) {
            msg("§cKullanım: /" + label + " inspect <oyuncu>");
            return;
        }
        java.util.UUID uuid;
        var online = org.bukkit.Bukkit.getPlayerExact(targetName);
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            try {
                uuid = java.util.UUID.fromString(targetName);
            } catch (IllegalArgumentException e) {
                msg("admin.ban.player-offline-no-uuid");
                return;
            }
        }
        var listings = manager.getData().getActiveListingsBySeller(uuid);
        msg("§6=== " + targetName + " — Aktif İlanlar (" + listings.size() + ") ===");
        if (listings.isEmpty()) {
            msg("§7Bu oyuncunun aktif ilanı yok.");
            return;
        }
        for (var listing : listings) {
            String name = listing.item().getItemMeta().hasDisplayName()
                    ? listing.item().getItemMeta().getDisplayName() : listing.item().getType().name();
            msg("§7• " + listing.id() + " §8— §f" + name
                    + " §8· §6" + manager.getApi().getEconomyManager().format(listing.price()));
        }
    }
}
