package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * /ihale trade <oyuncu> — oyuncu-oyuncu takas sistemi.
 * <pre>
 * /ihale trade <oyuncu>   → takas isteği gönder
 * /ihale trade kabul      → gelen isteği kabul et
 * /ihale trade reddet     → gelen isteği reddet
 * /ihale trade iptal      → aktif takas oturumunu kapat
 * </pre>
 */
public class CmdTrade extends AuctionCmd {

    public CmdTrade() {
        super("trade", "lbauctionhouse.trade", false);
        setAliases("takas", "t");
        setUsage("<oyuncu|kabul|reddet|iptal>");
        setDescription("Oyuncular arası takas");
    }

    @Override
    protected void execute() {
        if (!config.isTradeEnabled()) {
            player.sendMessage("§cTakas sistemi kapalı.");
            return;
        }

        var tradeService = plugin.getTradeService();
        if (tradeService == null) {
            player.sendMessage("§cTakas sistemi henüz hazır değil.");
            return;
        }

        if (args.length == 0) {
            player.sendMessage("§7Kullanım: §e/" + label + " trade §7<§eoyuncu§7|§ekabul§7|§ereddet§7|§eiptal§7>");
            return;
        }

        String action = args[0].toLowerCase();

        // /ihale trade iptal — aktif oturumu kapat
        if (isAction(action, "iptal", "cancel")) {
            var session = tradeService.getSession(player);
            if (session == null) {
                player.sendMessage("§7Aktif bir takas oturumunuz yok.");
                return;
            }
            tradeService.closeSession(player);
            return;
        }

        // /ihale trade kabul — gelen isteği kabul et
        if (isAction(action, "kabul", "accept", "evet")) {
            int result = tradeService.acceptRequest(player);
            switch (result) {
                case 0 -> {}
                case 1 -> player.sendMessage("§cSana gönderilmiş bir takas isteği yok.");
                case 2 -> player.sendMessage("§cTakas isteğinin süresi doldu.");
                case 3 -> player.sendMessage("§cİstek gönderen oyuncu çevrimdışı.");
                default -> player.sendMessage("§cTakas başlatılamadı.");
            }
            return;
        }

        // /ihale trade reddet — isteği reddet
        if (isAction(action, "reddet", "decline", "hayır")) {
            int result = tradeService.declineRequest(player);
            if (result == 1) {
                player.sendMessage("§cSana gönderilmiş bir takas isteği yok.");
            }
            return;
        }

        // /ihale trade <oyuncu> — istek gönder
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cOyuncu bulunamadı veya çevrimdışı.");
            return;
        }

        int result = tradeService.sendRequest(player, target);
        switch (result) {
            case 1 -> player.sendMessage("§cOyuncu bulunamadı veya çevrimdışı.");
            case 2 -> player.sendMessage("§cKendine takas isteği gönderemezsin.");
            case 3 -> player.sendMessage("§cZaten bu oyuncuya bekleyen bir takas isteğin var.");
            case 4 -> player.sendMessage("§cTakas isteği göndermek için biraz bekleyin.");
            case 5 -> player.sendMessage("§cZaten aktif bir takas oturumunuz var.");
            default -> {}
        }
    }

    private boolean isAction(String input, String... actions) {
        for (String a : actions) {
            if (a.equals(input)) return true;
        }
        return false;
    }
}