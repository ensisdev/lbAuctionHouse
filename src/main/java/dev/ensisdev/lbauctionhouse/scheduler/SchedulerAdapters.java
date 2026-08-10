package dev.ensisdev.lbauctionhouse.scheduler;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runtime'da Folia olup olmadığını tespit eder ve uygun {@link SchedulerAdapter}
 * örneğini üretir. Folia'nın kendine özgü {@code RegionizedServerInitEvent} sınıfı
 * yalnızca Folia runtime'ında bulunur — bu güvenilir bir tespit anahtarıdır.
 */
public final class SchedulerAdapters {

    private static final String FOLIA_MARKER_CLASS =
            "io.papermc.paper.threadedregions.RegionizedServerInitEvent";

    private SchedulerAdapters() {
    }

    /** Bu sunucuda Folia çalışıyor mu? */
    public static boolean isFolia() {
        try {
            Class.forName(FOLIA_MARKER_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Mevcut sunucu tipine uygun scheduler adaptörünü üretir. */
    public static SchedulerAdapter create(JavaPlugin plugin) {
        if (isFolia()) {
            return new FoliaSchedulerAdapter(plugin);
        }
        return new BukkitSchedulerAdapter(plugin);
    }
}
