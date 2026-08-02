package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction mylistings — oyuncunun kendi ilanlarını açar.
 * Tıklayarak iptal edebilir.
 */
public class CmdMylistings extends AuctionCmd {

    public CmdMylistings() {
        super("mylistings", "lbsmpcore.auction.use", false);
        setAliases("my", "ilanlarim", "ilanlarım");
        setDescription("İlanlarım");
    }

    @Override
    protected void execute() {
        if (player != null) manager.openMyListings(player);
    }
}
