package dev.ensisdev.lbauctionhouse.scheduler;

import org.bukkit.entity.Player;

/**
 * Sunucu görev zamanlayıcı soyutlaması.
 * <p>
 * Hem klasik Spigot/Paper ({@code Bukkit.getScheduler()}) hem Folia
 * ({@code GlobalRegionScheduler} / {@code AsyncScheduler} / {@code EntityScheduler})
 * üzerinde çalışır. Hangi implementasyonun seçileceği runtime'da Folia tespitiyle
 * belirlenir — plugin tek bir JAR ile her iki ortamda sorunsuz koşar.
 */
public interface SchedulerAdapter {

    /** Plugin bu sunucuda Folia runtime'ında mı çalışıyor? */
    boolean isFolia();

    /**
     * Ana thread / global region üzerinde senkron görev çalıştırır.
     * <p>
     * Folia'da {@code GlobalRegionScheduler.run(...)} kullanılır; Spigot'ta
     * {@code Bukkit.getScheduler().runTask(...)}. Oyuncu envanteri / GUI'ye
     * dokunan işler için {@link #runTaskForPlayer(Player, Runnable)} tercih edilmelidir.
     */
    void runTask(Runnable task);

    /** Gecikmeli senkron görev (tick cinsinden). */
    void runTaskLater(Runnable task, long delayTicks);

    /** Tamamen asenkron görev (DB, webhook vb. — oyuncu/envanter dokunulmaz). */
    void runTaskAsynchronously(Runnable task);

    /**
     * Tekrarlayan senkron görev başlatır.
     * Dönen {@link RepeatingTask} üzerinden iptal edilebilir.
     */
    RepeatingTask runRepeatingTask(Runnable task, long delayTicks, long periodTicks);

    /**
     * Oyuncunun kendi region'ında senkron görev çalıştırır.
     * <p>
     * Folia'da {@code player.getScheduler().run(...)} kullanılır (oyuncu envanteri,
     * GUI açma, item verme gibi oyuncuya ait işler için zorunludur); Spigot'ta
     * {@code Bukkit.getScheduler().runTask(...)} ile birebir aynıdır.
     */
    void runTaskForPlayer(Player player, Runnable task);

    /** Oyuncunun kendi region'ında gecikmeli görev çalıştırır (tick cinsinden). */
    void runTaskLaterForPlayer(Player player, Runnable task, long delayTicks);

    /** İptal edilebilir tekrarlayan görev tanıyıcısı. */
    interface RepeatingTask {
        void cancel();
    }
}
