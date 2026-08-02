package dev.ensisdev.lbauctionhouse.core.event;

import dev.ensisdev.lbauctionhouse.core.data.DataManager;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Veritabanı bağlantısı başarıyla kurulup migration'lar çalıştırıldığında tetiklenir.
 * <p>
 * Addon'lar bu event'i dinleyerek veritabanının hazır olduğunu öğrenebilir
 * ve kendi tablolarını oluşturabilir.
 */
public class DataLoadEvent extends CoreEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DataManager dataManager;
    private final boolean success;

    public DataLoadEvent(DataManager dataManager, boolean success) {
        this.dataManager = dataManager;
        this.success = success;
    }

    /**
     * DataManager referansı — addon'lar veritabanı işlemleri için kullanabilir.
     */
    public DataManager getDataManager() {
        return dataManager;
    }

    /**
     * Bağlantı başarılı mı?
     */
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getSystem() {
        return "data";
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
