package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import dev.ensisdev.lbauctionhouse.data.AuctionData;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * /auction stats — oyuncunun kendi istatistiklerini ve son 30 günün
 * satış grafiğini (ASCII bar chart) gösterir.
 */
public class CmdStats extends AuctionCmd {

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.systemDefault());

    public CmdStats() {
        super("stats", "lbsmpcore.auction.use", false);
        setAliases("istatistik", "statistics");
        setDescription("İstatistiklerim + satış grafiği");
    }

    @Override
    protected void execute() {
        if (player == null) return;

        AuctionData.PlayerStats stats = manager.getData().getPlayerStats(player.getUniqueId());
        List<AuctionData.DailySales> chart = manager.getData().getPlayerSalesChart(player.getUniqueId(), 30);

        msg("§6§l=== İstatistiklerim ===");
        msg(" §7Satılan Eşya: §e" + stats.totalSold());
        msg(" §7Alınan Eşya: §e" + stats.totalBought());
        msg(" §7Kazanç: §e" + fmt(stats.totalEarned()) + "§6₺");
        msg(" §7Harcama: §e" + fmt(stats.totalSpent()) + "§6₺");
        msg("");
        msg(buildChart(chart));
    }

    /**
     * Son 30 günü 3'er günlük 10 gruba bölerek dikey ASCII bar chart üretir.
     * Bar genişliği grup içindeki maksimum kazanca göre ölçeklenir.
     */
    private String buildChart(List<AuctionData.DailySales> chart) {
        if (chart.isEmpty()) {
            return "§7Satış verisi yok.";
        }

        final int groups = 10;
        final int perGroup = 3;
        final int barWidth = 12;

        long[] groupRevenue = new long[groups]; // kuruş (ondalık hassasiyet)
        int[] groupCount = new int[groups];
        long maxRevenue = 1;

        for (int i = 0; i < chart.size(); i++) {
            AuctionData.DailySales day = chart.get(i);
            int g = Math.min(i / perGroup, groups - 1);
            groupRevenue[g] += Math.round(day.revenue() * 100);
            groupCount[g] += day.count();
            if (groupRevenue[g] > maxRevenue) maxRevenue = groupRevenue[g];
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§8§m┌──────────────────────────────┐");
        sb.append("\n§8│ §6§lSon 30 Gün Satış Grafiği §8│");
        sb.append("\n§8§m├──────────────────────────────┤");

        for (int g = 0; g < groups; g++) {
            int dayIndex = g * perGroup;
            if (dayIndex >= chart.size()) break;

            long start = chart.get(dayIndex).dayStart();
            int filled = (int) Math.ceil((double) groupRevenue[g] / maxRevenue * barWidth);
            if (groupRevenue[g] == 0) filled = 0;

            sb.append("\n§7").append(DAY_FMT.format(Instant.ofEpochMilli(start)))
              .append(" §8│");

            if (filled > 0) sb.append("§a").append("█".repeat(filled));
            if (filled < barWidth) sb.append("§8").append("░".repeat(barWidth - filled));

            sb.append("§e ").append(fmt(groupRevenue[g] / 100.0)).append("₺");
            if (groupCount[g] > 0) {
                sb.append(" §7(").append(groupCount[g]).append(" satış)");
            }
        }

        sb.append("\n§8§m└──────────────────────────────┘");
        return sb.toString();
    }

    private String fmt(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(value);
    }
}