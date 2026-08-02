package dev.ensisdev.lbauctionhouse.core.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tabela (Sign) arayüzü ile metin girişi.
 * <p>
 * Paper 1.20+ API kullanır, herhangi bir harici kütüphane gerektirmez.
 * Oyuncuya tabela açılır, yazdığı metin okunur.
 */
public class SignInputGUI {

    private static final int LINE = 0; // Hangi satır okunsun

    private final LbAuctionHouse plugin;
    private final Player player;
    private BiConsumer<Player, String> onComplete;
    private Consumer<Player> onClose;
    private String[] defaultLines;

    private SignInputGUI(LbAuctionHouse plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.defaultLines = new String[]{"", "~~~~~~~~~~~", "Arama metnini", "yazın"};
    }

    public static SignInputGUI create(LbAuctionHouse plugin, Player player) {
        return new SignInputGUI(plugin, player);
    }

    public SignInputGUI lines(String... lines) {
        this.defaultLines = lines;
        return this;
    }

    public SignInputGUI onComplete(BiConsumer<Player, String> callback) {
        this.onComplete = callback;
        return this;
    }

    public SignInputGUI onClose(Consumer<Player> callback) {
        this.onClose = callback;
        return this;
    }

    public void open() {
        World world = player.getWorld();
        Location loc = player.getLocation().add(0, -5, 0); // Oyuncunun altında, görünmez
        loc.getBlock().setType(org.bukkit.Material.OAK_SIGN, false);

        try {
            org.bukkit.block.Sign sign = (org.bukkit.block.Sign) loc.getBlock().getState();
            for (int i = 0; i < Math.min(defaultLines.length, 4); i++) {
                sign.line(i, Component.text(defaultLines[i] != null ? defaultLines[i] : ""));
            }
            sign.setEditable(true);
            sign.update(true);

            Listener listener = new Listener() {
                @EventHandler
                public void onSignChange(org.bukkit.event.block.SignChangeEvent e) {
                    if (!e.getPlayer().equals(player)) return;
                    // DİKKAT: Location.equals() yaw/pitch'i de karşılaştırır. Oyuncunun
                    // yaw/pitch'i 0 değilse (bir yöne bakıyorsa) eşleşme HER ZAMAN başarısız
                    // olurdu. Bu yüzden yalnızca world + blok koordinatları karşılaştırılır.
                    if (!e.getBlock().getWorld().equals(loc.getWorld())) return;
                    if (e.getBlock().getX() != loc.getBlockX()) return;
                    if (e.getBlock().getY() != loc.getBlockY()) return;
                    if (e.getBlock().getZ() != loc.getBlockZ()) return;
                    cleanup();
                    String text = e.getLine(LINE);
                    if (text == null || text.trim().isEmpty()) {
                        if (onClose != null) onClose.accept(player);
                    } else {
                        if (onComplete != null) onComplete.accept(player, text.trim());
                    }
                }

                @EventHandler
                public void onQuit(PlayerQuitEvent e) {
                    if (e.getPlayer().equals(player)) cleanup();
                }

                void cleanup() {
                    HandlerList.unregisterAll(this);
                    loc.getBlock().setType(org.bukkit.Material.AIR, false);
                }
            };

            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            org.bukkit.block.Sign signBlock = (org.bukkit.block.Sign) loc.getBlock().getState();
            player.openSign(signBlock);

        } catch (Exception e) {
            // Fallback: sohbet girişi
            plugin.getLogger().warning("Sign GUI açılamadı: " + e.getMessage() + " — sohbet kullanılacak");
            loc.getBlock().setType(org.bukkit.Material.AIR, false);
            player.sendMessage("§eAranacak eşya adını sohbete yaz:");
            plugin.getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
                    if (!e.getPlayer().equals(player)) return;
                    e.setCancelled(true);
                    HandlerList.unregisterAll(this);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if ("iptal".equalsIgnoreCase(e.getMessage().trim())) {
                            if (onClose != null) onClose.accept(player);
                        } else {
                            if (onComplete != null) onComplete.accept(player, e.getMessage().trim());
                        }
                    });
                }
            }, plugin);
        }
    }
}
