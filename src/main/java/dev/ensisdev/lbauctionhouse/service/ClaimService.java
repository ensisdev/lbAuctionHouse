package dev.ensisdev.lbauctionhouse.service;

import dev.ensisdev.lbauctionhouse.data.AuctionData;
import dev.ensisdev.lbauctionhouse.data.AuctionData.CollectionEntry;
import dev.ensisdev.lbauctionhouse.economy.AuctionEconomy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Koleksiyon kutusu (Collection Box) alma hizmeti.
 * <p>
 * Satıcı/süresi dolan ilanlardan gelen eşya ve paraları oyuncuya teslim eder.
 */
public class ClaimService {

    private final AuctionData data;
    private final AuctionEconomy economy;

    public ClaimService(AuctionData data, AuctionEconomy economy) {
        this.data = data;
        this.economy = economy;
    }

    /**
     * Oyuncunun bekleyen tüm koleksiyon girdilerini teslim eder.
     * @return kaç girdi teslim edildi
     */
    public int claimAll(Player player) {
        List<CollectionEntry> entries = data.getUnclaimedCollection(player.getUniqueId());
        int claimed = 0;
        for (CollectionEntry entry : entries) {
            if (claimEntry(player, entry)) claimed++;
        }
        return claimed;
    }

    /**
     * Belirli bir koleksiyon girdisini teslim eder.
     * @return teslim başarılı mı
     */
    public boolean claimEntry(Player player, int entryId) {
        List<CollectionEntry> entries = data.getUnclaimedCollection(player.getUniqueId());
        for (CollectionEntry entry : entries) {
            if (entry.id() == entryId) {
                return claimEntry(player, entry);
            }
        }
        return false;
    }

    private boolean claimEntry(Player player, CollectionEntry entry) {
        UUID playerUUID = player.getUniqueId();
        switch (entry.type()) {
            case "ITEM" -> {
                ItemStack item = entry.item();
                if (item == null) {
                    data.removeFromCollection(entry.id());
                    return false;
                }
                player.getInventory().addItem(item);
                data.markClaimed(entry.id());
                return true;
            }
            case "MONEY" -> {
                if (entry.amount() > 0) {
                    economy.deposit(playerUUID, entry.amount());
                }
                data.markClaimed(entry.id());
                return true;
            }
            default -> {
                data.removeFromCollection(entry.id());
                return false;
            }
        }
    }

    /**
     * Oyuncunun bekleyen girdi sayısı.
     */
    public int getUnclaimedCount(UUID playerUUID) {
        return data.getUnclaimedCount(playerUUID);
    }

    /**
     * Oyuncunun bekleyen tüm girdileri.
     */
    public List<CollectionEntry> getUnclaimed(UUID playerUUID) {
        return data.getUnclaimedCollection(playerUUID);
    }
}