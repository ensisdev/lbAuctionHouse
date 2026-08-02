package fr.maxlego08.zauctionhouse.command.commands.admin;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CommandAuctionAdminForceOpen extends VCommand {

    public CommandAuctionAdminForceOpen(AuctionPlugin plugin) {
        super(plugin);
        this.addSubCommand("forceopen");
        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_FORCEOPEN);
        this.addRequireArg("player", (sender, args) -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        this.addRequireArg("inventory", (sender, args) -> {
            List<String> completions = new ArrayList<>();
            for (Inventories inv : Inventories.values()) {
                completions.add(inv.getFileName());
            }
            return completions;
        });
        this.addOptionalArg("page");
        this.setConsoleCanUse(true);
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {

        Player target = argAsPlayer(0);
        if (target == null) {
            message(this.plugin, this.sender, Message.ADMIN_TARGET_NOT_FOUND, "%target%", argAsString(0));
            return CommandType.DEFAULT;
        }

        String inventoryName = argAsString(1);
        int page = argAsInteger(2, 1);

        Inventories inventory = null;
        for (Inventories inv : Inventories.values()) {
            if (inv.getFileName().equalsIgnoreCase(inventoryName)) {
                inventory = inv;
                break;
            }
        }

        if (inventory == null) {
            message(this.plugin, this.sender, Message.INVENTORY_NOT_FOUND, "%inventory-name%", inventoryName);
            return CommandType.DEFAULT;
        }

        Inventories targetInventory = inventory;
        this.plugin.getScheduler().runAtEntity(target, w -> this.plugin.getInventoriesLoader().openInventory(target, targetInventory, page));
        message(this.plugin, this.sender, Message.ADMIN_FORCEOPEN_INVENTORY, "%target%", target.getName(), "%inventory%", targetInventory.getFileName(), "%page%", String.valueOf(page));

        return CommandType.SUCCESS;
    }
}
