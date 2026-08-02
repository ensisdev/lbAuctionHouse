package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;

public class CommandAuctionClaim extends VCommand {

    public CommandAuctionClaim(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_USE);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_CLAIM);
        this.addSubCommand(plugin.getConfiguration().loadCommandAliases("commands.claim."));
        this.setConsoleCanUse(false);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        plugin.getAuctionManager().getClaimService().claimMoney(this.player);
        return CommandType.SUCCESS;
    }
}
