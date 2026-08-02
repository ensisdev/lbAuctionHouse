package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import dev.ensisdev.lbauctionhouse.data.AuctionListing;
import dev.ensisdev.lbauctionhouse.gui.PlayerListingsGUI;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /auction gör <oyuncu>
 * <p>
 * Başka bir oyuncunun aktif ilanlarını salt-okunur GUI'de gösterir.
 * Oyuncu adı kısmi yazılabilir — çevrimiçi oyuncularda prefix eşleşmesi yapılır,
 * bulunamazsa offline oynanmış isimlerde tam ad aranır.
 */
public class CmdView extends AuctionCmd {

    public CmdView() {
        super("view", "", true);
        setAliases("gör", "gor", "view");
        setUsage("<oyuncu>");
        setDescription("Başka bir oyuncunun aktif ilanlarını görüntüler");
    }

    @Override
    protected void execute() {
        if (player == null) {
            msg("§cBu komut sadece oyuncular içindir.");
            return;
        }

        if (!hasArg(0)) {
            msg("view.usage", "%cmd%", label);
            return;
        }

        String query = arg(0);

        // 1) Çevrimiçi oyuncuları kısmi isimle eşle
        Player target = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().equalsIgnoreCase(query)) {
                target = online;
                break;
            }
        }
        if (target == null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(query.toLowerCase())) {
                    target = online;
                    break;
                }
            }
        }

        String sellerName;

        if (target != null) {
            sellerName = target.getName();
        } else {
            // 2) Offline oyuncu — tam isim girilmiş mi?
            OfflinePlayer offline = Bukkit.getOfflinePlayer(query);
            if (offline.hasPlayedBefore() && offline.getName() != null) {
                sellerName = offline.getName();
            } else {
                msg("view.not-found");
                return;
            }
        }

        List<AuctionListing> listings = manager.getData().searchListingsBySeller(sellerName);
        if (listings.isEmpty()) {
            msg("view.empty", "%seller%", sellerName);
            return;
        }

        // GUI'yi aç
        PlayerListingsGUI gui = new PlayerListingsGUI(
                manager,
                config,
                manager.getData(),
                plugin.getGuiLayoutLoader()
        );
        gui.open(player, sellerName, listings);
        msg("view.opening", "%seller%", sellerName);
    }
}