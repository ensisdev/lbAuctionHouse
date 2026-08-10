package dev.ensisdev.lbauctionhouse.gui;

import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.core.gui.BaseMenu;
import dev.ensisdev.lbauctionhouse.core.gui.MenuItem;
import dev.ensisdev.lbauctionhouse.data.CollectionEntry;
import dev.ensisdev.lbauctionhouse.util.SmallCaps;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin yasak yönetimi GUI'si — yasaklı oyuncuları listeler.
 * Sol tık → yasağı kaldır. Layout sabittir (admin paneline özel).
 */
public class AdminBansGUI extends BaseMenu {

    private final AuctionManager manager;
    private final CollectionEntry data;

    private Player currentPlayer;
    private List<String[]> banned;
    private int currentPage;

    public AdminBansGUI(AuctionManager manager, CollectionEntry data) {
        super("admin_bans", "&8&l» <gradient:#FF5555:#FF8A80>" + SmallCaps.toSmallCaps("YASAK YÖNETİMİ") + "</gradient> &8&l«", 6);
        this.manager = manager;
        this.data = data;
    }

    public void open(Player player) {
        this.currentPlayer = player;
        this.banned = data.getBannedPlayers();
        this.currentPage = 0;
        super.open(player);
    }

    @Override
    protected void onOpen(Player player) {
        clear();
        drawBorder();

        var slots = contentSlots();
        int start = currentPage * slots.size();
        int end = Math.min(start + slots.size(), banned.size());

        for (int i = start; i < end; i++) {
            String[] entry = banned.get(i); // {uuid, name, reason, banned_at}
            String name = entry.length > 1 ? entry[1] : "?";
            String reason = entry.length > 2 ? entry[2] : "";
            String bannedAt = entry.length > 3 ? entry[3] : "";
            int index = i;

            setItem(slots.get(i - start), MenuItem.builder(Material.PLAYER_HEAD)
                    .name("&#FF5555" + name)
                    .lore("&#8c8c8cSebep: &#F5F5F5" + reason)
                    .lore("&#8c8c8cYasak Tarihi: &#F5F5F5" + bannedAt)
                    .lore("")
                    .lore("&#55FF55✔ Sol Tık — yasağı kaldır")
                    .onClick(e -> handleClick(index))
                    .build());
        }
    }

    private void handleClick(int index) {
        if (index < 0 || index >= banned.size()) return;
        String[] entry = banned.get(index);
        try {
            UUID uuid = UUID.fromString(entry[0]);
            data.unbanPlayer(uuid);
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed(
                    "admin.ban.unbanned", "player", entry[1]));
        } catch (IllegalArgumentException ignored) {
            currentPlayer.sendMessage(manager.getApi().getLanguageManager().getPrefixed("admin.ban.player-offline-no-uuid"));
        }
        open(currentPlayer);
    }

    @Override
    protected void onClick(InventoryClickEvent event, MenuItem item) {
        int slot = event.getSlot();
        if (slot == 45) {
            if (currentPage > 0) { currentPage--; open(currentPlayer); }
        } else if (slot == 53) {
            var slots = contentSlots();
            int maxPage = (int) Math.ceil((double) banned.size() / slots.size()) - 1;
            if (currentPage < maxPage) { currentPage++; open(currentPlayer); }
        } else if (slot == 49) {
            close(currentPlayer);
            manager.openAdminGUI(currentPlayer);
        }
    }

    private List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 9; i <= 44; i++) slots.add(i);
        return slots;
    }

    private void drawBorder() {
        for (int slot : List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53)) {
            setItem(slot, MenuItem.builder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
        setItem(45, MenuItem.builder(Material.ARROW)
                .name("&#FFD54F&l« &#FFB74D&lᴏɴᴄᴇᴋɪ ꜱᴀʏꜰᴀ")
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— önceki sayfayı aç")
                .build());
        setItem(49, MenuItem.builder(Material.BARRIER)
                .name("&#FF5555&lɢᴇʀɪ")
                .lore("&#8c8c8c• &#FF5555Tıkla &#F5F5F5— admin paneline dön")
                .build());
        setItem(53, MenuItem.builder(Material.ARROW)
                .name("&#FFB74D&lꜱᴏɴʀᴀᴋɪ ꜱᴀʏꜰᴀ &#FFD54F&l»")
                .lore("&#8c8c8c• &#FFD54FTıkla &#F5F5F5— sonraki sayfayı aç")
                .build());
    }

    @Override
    protected void onClose(Player player) {}
}
