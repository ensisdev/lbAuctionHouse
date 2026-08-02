package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction browse — ana ihale GUI'sini açar.
 * Config'de browse alt komut adı değiştirilebilir.
 */
public class CmdBrowse extends AuctionCmd {

    public CmdBrowse() {
        super("browse", "", true);
        setAliases("ac", "aç", "listele", "ara");
        setDescription("İhaleı açar");
    }

    @Override
    protected void execute() {
        if (player != null) manager.openMainMenu(player);
    }
}
