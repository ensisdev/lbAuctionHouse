package dev.ensisdev.lbauctionhouse.core.addon;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Addon'lar için özel logger — mesajları {@code [lbSmpCore-<AddonID>]} prefix'i ile loglar.
 * <p>
 * Core'un standart logger'ını sarar (wrap), böylece addon mesajları
 * konsolda diğer plugin mesajlarıyla tutarlı görünür.
 * <p>
 * Kullanım (addon içinden):
 * <pre>
 * public class OrderAddon extends JavaPlugin implements LbSmpAddon {
 *     private AddonLogger addonLogger;
 *
 *     public void onAddonEnable(AuctionAPI api, JavaPlugin plugin) {
 *         addonLogger = new AddonLogger("Order", api.getLogger());
 *         addonLogger.info("Addon başlatıldı!");
 *     }
 * }
 * </pre>
 * Konsol çıktısı: {@code [lbSmpCore-Order] Addon başlatıldı!}
 */
public class AddonLogger {

    private final Logger delegate;
    private final String prefix;

    /**
     * @param addonId Addon ID'si (örn: "Order")
     * @param delegate Core'un logger'ı (veya JavaPlugin.getLogger())
     */
    public AddonLogger(String addonId, Logger delegate) {
        this.delegate = delegate;
        this.prefix = "[lbSmpCore-" + addonId + "] ";
    }

    public void info(String message) {
        delegate.info(prefix + message);
    }

    public void info(String message, Object... args) {
        delegate.info(prefix + String.format(message, args));
    }

    public void warn(String message) {
        delegate.warning(prefix + message);
    }

    public void warn(String message, Object... args) {
        delegate.warning(prefix + String.format(message, args));
    }

    public void error(String message) {
        delegate.severe(prefix + message);
    }

    public void error(String message, Object... args) {
        delegate.severe(prefix + String.format(message, args));
    }

    public void error(String message, Throwable thrown) {
        LogRecord record = new LogRecord(Level.SEVERE, prefix + message);
        record.setThrown(thrown);
        record.setLoggerName(delegate.getName());
        delegate.log(record);
    }

    /**
     * Alttaki gerçek Logger nesnesini döndürür.
     */
    public Logger getDelegate() {
        return delegate;
    }
}
