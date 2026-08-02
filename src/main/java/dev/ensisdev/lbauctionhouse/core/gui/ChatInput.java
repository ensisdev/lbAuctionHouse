package dev.ensisdev.lbauctionhouse.core.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Sohbet tabanlı metin girişi — oyuncuya mesaj gönderir, sohbete yazdığını alır.
 * AnvilGUI gerektirmez, her Paper sürümünde çalışır.
 * <p>
 * Kullanım:
 * <pre>
 * ChatInput.create(plugin, player)
 *     .prompt("&eAranacak eşya adını yaz:")
 *     .onComplete((p, text) -> p.sendMessage("Aradın: " + text))
 *     .onClose(p -> p.sendMessage("İptal edildi"))
 *     .open();
 * </pre>
 */
public class ChatInput {

    private final LbAuctionHouse plugin;
    private final Player player;
    private String promptText;
    private BiConsumer<Player, String> onComplete;
    private Consumer<Player> onClose;

    private ChatInput(LbAuctionHouse plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.promptText = "&eGiriş yap:";
    }

    public static ChatInput create(LbAuctionHouse plugin, Player player) {
        return new ChatInput(plugin, player);
    }

    public ChatInput prompt(String text) {
        this.promptText = text;
        return this;
    }

    public ChatInput onComplete(BiConsumer<Player, String> callback) {
        this.onComplete = callback;
        return this;
    }

    public ChatInput onClose(Consumer<Player> callback) {
        this.onClose = callback;
        return this;
    }

    public void open() {
        player.sendMessage(color(promptText));
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onChat(AsyncPlayerChatEvent e) {
                if (!e.getPlayer().equals(player)) return;
                e.setCancelled(true);
                HandlerList.unregisterAll(this);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (onComplete != null) onComplete.accept(player, e.getMessage());
                });
            }
        }, plugin);
    }

    private String color(String text) {
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
    }
}
