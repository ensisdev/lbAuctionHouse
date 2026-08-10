package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction favori — favori ilanları GUI'sini açar.
 */
public class CmdFavorites extends AuctionCmd {

    public CmdFavorites() {
        super("favorites", "", true);
        setAliases("favori", "fav");
        setDescription("Favori ilanlar");
    }

    @Override
    protected void execute() {
        if (player != null) manager.openFavorites(player);
    }
}
