package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction geçmiş — kişisel satış/satın alma geçmişi GUI'sini açar.
 */
public class CmdHistory extends AuctionCmd {

    public CmdHistory() {
        super("history", "", true);
        setAliases("geçmiş", "gecmis", "geridön");
        setDescription("İşlem geçmişi");
    }

    @Override
    protected void execute() {
        if (player != null) manager.openHistory(player);
    }
}
