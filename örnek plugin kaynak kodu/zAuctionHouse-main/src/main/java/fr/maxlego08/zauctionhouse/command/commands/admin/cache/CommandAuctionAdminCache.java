package fr.maxlego08.zauctionhouse.command.commands.admin.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;

public class CommandAuctionAdminCache extends VCommand {

    public CommandAuctionAdminCache(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE);
        this.setConsoleCanUse(false);

        this.addSubCommand("cache");
        this.addSubCommand(new CommandAuctionAdminCacheShow(plugin));
        this.addSubCommand(new CommandAuctionAdminCacheClear(plugin));
        this.addSubCommand(new CommandAuctionAdminCacheSet(plugin));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        this.syntaxMessage(this.sender);
        return CommandType.SYNTAX_ERROR;
    }
}