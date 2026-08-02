package fr.maxlego08.zauctionhouse.search;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatSearchListener implements Listener {

    private final AuctionPlugin plugin;
    private final Set<UUID> waitingForInput = ConcurrentHashMap.newKeySet();

    public ChatSearchListener(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void startSearch(Player player) {
        waitingForInput.add(player.getUniqueId());
        player.closeInventory();
        this.plugin.sendMessage(player, Message.SEARCH_START);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!waitingForInput.remove(event.getPlayer().getUniqueId())) return;

        event.setCancelled(true);

        String query = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player player = event.getPlayer();

        this.plugin.getScheduler().runAtEntity(player, w -> {
            if (player.isOnline()) {
                this.plugin.getAuctionManager().startSearch(player, query);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waitingForInput.remove(event.getPlayer().getUniqueId());
    }
}
