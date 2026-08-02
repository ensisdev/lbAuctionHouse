package dev.ensisdev.lbauctionhouse.economy;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Çoklu ekonomi desteği için soyut interface.
 * Vault, PlayerPoints, Item gibi farklı ekonomi türlerini destekler.
 */
public interface AuctionEconomyProvider {

    /** Ekonomi adı (örn: "Vault", "Points", "Item") */
    String getName();

    /** Oyuncunun yeterli bakiyesi var mı? */
    boolean has(Player player, double amount);

    /** Oyuncudan para çek */
    boolean withdraw(Player player, double amount);

    /** Oyuncuya para yatır */
    boolean deposit(UUID playerUUID, double amount);

    /** Miktarı formatla */
    String format(double amount);

    /** Bu ekonomi kullanılabilir durumda mı? (plugin yüklü mü?) */
    boolean isAvailable();
}
