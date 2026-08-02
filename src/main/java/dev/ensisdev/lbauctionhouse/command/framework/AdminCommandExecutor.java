package dev.ensisdev.lbauctionhouse.command.framework;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.command.cmd.CmdAdmin;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.AuctionMessages;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Yönetim komutu executor'ı — ana komuttan BAĞIMSIZ ayrı bir komuttur
 * (örn. /ihaleadmin). Admin alt komutlarını doğrudan yönetir:
 * <pre>
 * /ihaleadmin stats | logs | clear | remove &lt;uuid&gt; | ban &lt;oyuncu&gt; | unban | banlist
 * </pre>
 * Tab completer dahildir.
 */
public class AdminCommandExecutor implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_KEYS = List.of(
            "stats", "logs", "clear", "remove", "ban", "unban", "banlist");

    private final CmdAdmin cmdAdmin;
    private final AuctionConfig config;
    private final AuctionManager manager;
    private final AddonLogger logger;

    public AdminCommandExecutor(LbAuctionHouse plugin, AuctionManager manager,
                                AuctionConfig config, AuctionMessages messages,
                                AddonLogger logger) {
        this.config = config;
        this.manager = manager;
        this.logger = logger;
        this.cmdAdmin = new CmdAdmin();
        this.cmdAdmin.inject(plugin, manager, config, messages, logger);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lbsmpcore.auction.admin")) {
            sender.sendMessage("§cYetkin yok!");
            return true;
        }

        cmdAdmin.sender = sender;
        cmdAdmin.player = sender instanceof Player p ? p : null;
        cmdAdmin.args = args;
        cmdAdmin.label = label;
        cmdAdmin.runCommand();
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        try {
            if (!sender.hasPermission("lbsmpcore.auction.admin")) return List.of();

            if (args.length == 1) {
                return allAdminSubNames().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length == 2) {
                if (config.isAdminSub("remove", args[0])) {
                    return manager.getData().getActiveListings().stream()
                            .map(l -> l.id().toString())
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .limit(20)
                            .collect(Collectors.toList());
                }
                if (config.isAdminSub("ban", args[0]) || config.isAdminSub("unban", args[0])) {
                    return org.bukkit.Bukkit.getOnlinePlayers().stream()
                            .map(org.bukkit.entity.Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (config.isAdminSub("logs", args[0])) {
                    return List.of("10", "20", "50", "100").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }

            if (args.length == 3 && config.isAdminSub("ban", args[0])) {
                // ban <oyuncu> <sebep> — sebep önerisi yok
                return List.of();
            }
        } catch (Throwable t) {
            // Tab-complete asla istemciye hata fırlatmaz — TAM stack trace loglanır.
            logger.error("Yönetim tab-complete hatası (/" + alias + "):", t);
            return List.of();
        }
        return List.of();
    }

    private List<String> allAdminSubNames() {
        List<String> result = new ArrayList<>();
        for (String key : ADMIN_KEYS) {
            result.addAll(config.getAdminSubAliases(key));
        }
        return result;
    }
}
