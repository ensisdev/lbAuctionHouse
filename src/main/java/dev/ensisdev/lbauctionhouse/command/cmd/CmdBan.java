package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import java.util.UUID;

/**
 * /auction admin ban <oyuncu> [sebep] — oyuncuyu ihalelerden men eder (admin).
 * /auction admin unban <oyuncu> — yasağı kaldırır.
 * /auction admin banlist — banlı oyuncuları listeler.
 */
public class CmdBan extends AuctionCmd {

    public CmdBan() {
        super("ban", "lbsmpcore.auction.admin", true);
        setAliases("yasak");
        setDescription("Oyuncu ban yönetimi (admin)");
    }

    @Override
    protected void execute() {
        if (!hasArg(0)) {
            msg("§cKullanım:");
            msg("§7/" + label + " admin ban <oyuncu> [sebep]");
            msg("§7/" + label + " admin unban <oyuncu>");
            msg("§7/" + label + " admin banlist");
            return;
        }

        switch (arg(0).toLowerCase()) {
            case "unban", "affet" -> unbanPlayer();
            case "banlist", "banliste", "yasaklılar" -> listBanned();
            default -> banPlayer();
        }
    }

    public void banPlayer() {
        String targetName = arg(0);
        String reason = hasArg(1) ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Sebep belirtilmedi";

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

    public void unbanPlayer() {
        String targetName = arg(1);
        var target = org.bukkit.Bukkit.getPlayerExact(targetName);
        UUID uuid;
        if (target != null) {
            uuid = target.getUniqueId();
        } else {
            // Offline player — try UUID format or offline UUID
            try { uuid = UUID.fromString(targetName); }
            catch (IllegalArgumentException e) {
                msg("admin.ban.player-offline-no-uuid");
                return;
            }
        }

        manager.getData().unbanPlayer(uuid);
        msg("admin.ban.unbanned", "player", targetName);
    }

    public void listBanned() {
        var banned = manager.getData().getBannedPlayers();
        if (banned.isEmpty()) {
            msg("admin.ban.no-banned");
            return;
        }
        msg("admin.ban.banned-list-header", "count", String.valueOf(banned.size()));
        for (var entry : banned) {
            // entry[0]=UUID, entry[1]=playerName, entry[2]=bannedBy, entry[3]=reason
            msg("§7" + entry[1] + " §7(" + entry[2] + ")");
        }
    }
}