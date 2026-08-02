package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

import java.util.UUID;

/**
 * /auction remove <uuid> — admin, ilanı kaldırır.
 * Eşya satıcının kolisine düşer.
 */
public class CmdRemove extends AuctionCmd {

    public CmdRemove() {
        super("remove", "lbsmpcore.auction.admin", true);
        setAliases("delete", "sil", "adminremove");
        setUsage("<ilan-uuid>");
        setDescription("İlan kaldır (admin)");
    }

    @Override
    protected void execute() {
        if (!hasArg(0)) {
            usage();
            return;
        }

        try {
            UUID listingId = UUID.fromString(arg(0));
            if (manager.removeListing(listingId)) {
                msg("§aİlan kaldırıldı. Eşya satıcının kolisine düştü.");
            } else {
                msg("§cİlan bulunamadı.");
            }
        } catch (IllegalArgumentException e) {
            msg("§cGeçersiz UUID formatı!");
        }
    }
}
