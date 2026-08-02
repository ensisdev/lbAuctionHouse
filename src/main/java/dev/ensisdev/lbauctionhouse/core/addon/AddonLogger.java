package dev.ensisdev.lbauctionhouse.core.addon;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * lbAuctionHouse için logger sarmalayıcı.
 * <p>
 * Plugin kendi logger'ı zaten {@code [lbAuctionHouse]} prefix'ini ekler; bu
 * sarmalayıcı fazladan prefix eklemez (eski lbAuctionHouse-* addon prefix'i kaldırıldı).
 */
public class AddonLogger {

    private final Logger delegate;
    private final String prefix;

    /**
     * @param addonId  (geriye uyumluluk için; prefix'e artık dahil edilmez)
     * @param delegate JavaPlugin.getLogger()
     */
    public AddonLogger(String addonId, Logger delegate) {
        this.delegate = delegate;
        this.prefix = "";
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
