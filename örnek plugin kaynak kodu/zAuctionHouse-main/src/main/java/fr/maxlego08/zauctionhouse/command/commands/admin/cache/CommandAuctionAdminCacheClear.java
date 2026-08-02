package fr.maxlego08.zauctionhouse.command.commands.admin.cache;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.cache.PlayerCacheKey;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.api.utils.Permission;
import fr.maxlego08.zauctionhouse.api.command.CommandType;
import fr.maxlego08.zauctionhouse.api.command.VCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CommandAuctionAdminCacheClear extends VCommand {

    public CommandAuctionAdminCacheClear(AuctionPlugin plugin) {
        super(plugin);

        this.setPermission(Permission.ZAUCTIONHOUSE_ADMIN);
        this.setDescription(Message.COMMAND_DESCRIPTION_AUCTION_ADMIN_CACHE_CLEAR);
        this.setConsoleCanUse(true);

        this.addSubCommand("clear");
        this.addRequireArg("player", (sender, args) -> {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("*");
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
            return suggestions;
        });
        this.addOptionalArg("key", (sender, args) -> {
            List<String> keys = new ArrayList<>();
            keys.add("all");
            Arrays.stream(PlayerCacheKey.values()).map(PlayerCacheKey::name).forEach(keys::add);
            return keys;
        });
    }

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        String targetName = argAsString(0);
        if (targetName == null) {
            return CommandType.SYNTAX_ERROR;
        }

        String keyName = argAsString(1);

        // Handle wildcard for all online players
        if (targetName.equals("*")) {
            Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
            int count = onlinePlayers.size();

            if (keyName == null || keyName.equalsIgnoreCase("all")) {
                for (Player target : onlinePlayers) {
                    this.auctionManager.clearPlayerCache(target, PlayerCacheKey.values());
                }
                message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL_PLAYERS_ALL, "%count%", String.valueOf(count));
            } else {
                PlayerCacheKey key;
                try {
                    key = PlayerCacheKey.valueOf(keyName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_KEY, "%key%", keyName);
                    return CommandType.DEFAULT;
                }
                for (Player target : onlinePlayers) {
                    this.auctionManager.clearPlayerCache(target, key);
                }
                message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL_PLAYERS, "%key%", key.name(), "%count%", String.valueOf(count));
            }
            return CommandType.SUCCESS;
        }

        // Handle single player
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            message(this.plugin, this.sender, Message.ADMIN_CACHE_PLAYER_NOT_ONLINE, "%player%", targetName);
            return CommandType.DEFAULT;
        }

        if (keyName == null || keyName.equalsIgnoreCase("all")) {
            this.auctionManager.clearPlayerCache(target, PlayerCacheKey.values());
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED_ALL, "%player%", target.getName());
        } else {
            PlayerCacheKey key;
            try {
                key = PlayerCacheKey.valueOf(keyName.toUpperCase());
            } catch (IllegalArgumentException e) {
                message(this.plugin, this.sender, Message.ADMIN_CACHE_INVALID_KEY, "%key%", keyName);
                return CommandType.DEFAULT;
            }
            this.auctionManager.clearPlayerCache(target, key);
            message(this.plugin, this.sender, Message.ADMIN_CACHE_CLEARED, "%key%", key.name(), "%player%", target.getName());
        }

        return CommandType.SUCCESS;
    }
}