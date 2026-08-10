package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction collect — bekleyen eşya/para kolisini açar.
 */
public class CmdCollect extends AuctionCmd {

    public CmdCollect() {
        super("collect", "lbauctionhouse.use", false);
        setAliases("claim", "al", "kutu", "ödül");
        setDescription("Kolim");
    }

    @Override
    protected void execute() {
        if (player != null) manager.openCollectionBox(player);
    }
}
