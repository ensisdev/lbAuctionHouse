package dev.ensisdev.lbauctionhouse.core.event;

import org.bukkit.event.Event;

/**
 * lbAuctionHouse'un tüm özel event'leri için abstract base sınıf.
 * <p>
 * Bukkit event sistemi üzerine kuruludur — addon'lar normal Bukkit listener'ları
 * ile bu event'leri dinleyebilir. Her event sınıfı kendi {@link org.bukkit.event.HandlerList}'ini
 * ve {@code getHandlerList()} statik metodunu tanımlamalıdır (Bukkit convention).
 * <p>
 * Kullanım:
 * <pre>
 * public class MyEvent extends CoreEvent {
 *     private static final HandlerList HANDLERS = new HandlerList();
 *     public static HandlerList getHandlerList() { return HANDLERS; }
 *     &#064;Override public HandlerList getHandlers() { return HANDLERS; }
 * }
 * </pre>
 */
public abstract class CoreEvent extends Event {

    protected CoreEvent() {
        super();
    }

    protected CoreEvent(boolean isAsync) {
        super(isAsync);
    }

    /**
     * Event'in hangi sistemden geldiğini döndürür.
     */
    public abstract String getSystem();
}
