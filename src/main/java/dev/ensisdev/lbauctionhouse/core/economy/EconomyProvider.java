package dev.ensisdev.lbauctionhouse.core.economy;

import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Ekonomi servisi için soyut arayüz.
 * <p>
 * Bu interface, Vault'a doğrudan bağımlılığı ortadan kaldırarak
 * Core ve addon'ların ekonomi işlemlerini tek bir API üzerinden
 * yapmasını sağlar. Vault varsa kullanılır, yoksa opsiyonel fallback
 * davranışı gösterir.
 * <p>

 */
public interface EconomyProvider {

    /**
     * Ekonomi servisi kullanılabilir durumda mı?
     * (Vault bağlantısı başarılı mı?)
     */
    boolean isEnabled();

    /**
     * Hesap bakiyesini sorgula.
     * @param playerUUID oyuncunun UUID'si
     * @return mevcut bakiye, hata durumunda -1
     */
    double getBalance(UUID playerUUID);

    /**
     * Hesap bakiyesini sorgula (OfflinePlayer üzerinden).
     */
    default double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    /**
     * Hesaba para ekle.
     * @param playerUUID oyuncu UUID'si
     * @param amount eklenecel miktar (pozitif)
     * @return işlem başarılı mı?
     */
    boolean deposit(UUID playerUUID, double amount);

    /**
     * Hesaptan para çek.
     * @param playerUUID oyuncu UUID'si
     * @param amount çekilecek miktar (pozitif)
     * @return işlem başarılı mı?
     */
    boolean withdraw(UUID playerUUID, double amount);

    /**
     * Oyuncunun belirli bir miktarı karşılayıp karşılayamadığını kontrol eder.
     */
    default boolean has(UUID playerUUID, double amount) {
        return getBalance(playerUUID) >= amount;
    }

    /**
     * Para birimi adı (örn: "Altın", "Dolar").
     */
    String currencyName();

    /**
     * Para birimi sembolü (örn: "$", "⛁").
     */
    String currencySymbol();

    /**
     * Kesirli kısım hassasiyeti (genelde 2 veya 0).
     */
    int fractionalDigits();

    /**
     * Miktarı formatla (örn: "$ 1,234.50")
     */
    default String format(double amount) {
        return String.format("%s %,.2f", currencySymbol(), amount);
    }
}
