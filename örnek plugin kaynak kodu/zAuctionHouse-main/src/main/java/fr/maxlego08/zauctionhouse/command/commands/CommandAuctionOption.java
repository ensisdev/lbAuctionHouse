package fr.maxlego08.zauctionhouse.command.commands;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.option.PlayerOption;
import fr.maxlego08.zauctionhouse.api.utils.Permission;

import java.util.Arrays;
import java.util.List;

public class CommandAuctionOption extends VCommand {

    public CommandAuctionOption(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_OPTION);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_OPTION);
        this.setConsoleCanUse(false);

        this.addSubCommand("option");
        this.addSubCommand("options");
        this.addSubCommand("opt");
        this.addOptionalArg("option_name", (sender, args) -> Arrays.stream(PlayerOption.values()).map(PlayerOption::getKey).toList());
        this.addOptionalArg("value", (sender, args) -> List.of("true", "false"));
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String optionName = argAsString(0, null);

        if (optionName == null) {
            plugin.getInventoriesLoader().openInventory(this.player, Inventories.OPTIONS);
            return CommandType.SUCCESS;
        }

        PlayerOption option = PlayerOption.fromKey(optionName);
        if (option == null) {
            return CommandType.DEFAULT;
        }

        String valueStr = argAsString(1, null);
        boolean value;
        if (valueStr != null) {
            value = Boolean.parseBoolean(valueStr);
        } else {
            // Toggle
            value = !this.auctionManager.getOptionService().getOption(this.player, option);
        }

        this.auctionManager.getOptionService().setOption(this.player.getUniqueId(), option, value);
        message(plugin, this.sender, value ? option.getEnabledMessage() : option.getDisabledMessage());

        return CommandType.SUCCESS;
    }
}
