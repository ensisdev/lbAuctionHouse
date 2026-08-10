package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.BroadcastService;
import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction reload — tüm yapılandırmayı yeniden yükler.
 * Sunucu restart'ı gerekmez.
 */
public class CmdReload extends AuctionCmd {

    public CmdReload() {
        super("reload", "lbauctionhouse.admin", true);
        // "yenile" alias'ı kaldırıldı; artık yalnızca /ihaleadmin altında çalışır
        setAliases("rld");
        setDescription("Config yeniden yükle (admin)");
    }

    /**
     * Bir başka AuctionCmd'den tetiklenince (CmdAdmin ↦ /ihaleadmin yenile|reload)
     * aynı reload mantığını çalıştırır.
     *
     * @param host çağıran komut (context — sender/player/label messages için)
     */
    public void reloaded(AuctionCmd host) {
        // Host'un tüm protected alanlarını bu objeye kopyala (Context)
        injectFrom(host);
        execute();
    }

    @Override
    protected void execute() {
        config.reloadAll();
        // messages.yml'i disketen YENİDEN oku (JAR varsayılanı ezilmez, kullanıcı düzenlemesi korunur)
        plugin.getAuctionMessages().reload();
        plugin.getGuiLayoutLoader().clearCache();
        // Eksik gui/*.yml dosyalarını diske yaz ve layout cache'ini doldur
        plugin.getGuiLayoutLoader().preloadAll();
        // Dil değiştiyse komut aliaslarını güncelle + feature gate'leri yeniden uygula
        plugin.getAuctionCmdManager().reloadCommands();
        // Cache'lenmiş GUI örneklerini sıfırla → yeni açılışlarda güncel gui/*.yml
        // layout'ları kullanılır (önceki örnekler eski layout nesnesini tutuyordu).
        plugin.getAuctionManager().resetCachedGuis();
        // Reklam duyuru görevini yeni ayarlara göre yeniden başlat
        // (advertise.enabled / broadcast-interval-seconds değişiklikleri restart istemez)
        restartBroadcastTask();
        msg("reload");
        msg("reload-restart");
    }

    /**
     * Periyodik reklam duyuru görevini güncel config'e göre yeniden başlatır.
     * Görev önce durdurulur, sonra ayar açıksa yeni aralıkla başlatılır.
     */
    private void restartBroadcastTask() {
        BroadcastService broadcast = plugin.getBroadcastService();
        if (broadcast == null) return;
        broadcast.stopBroadcastTask();
        if (config.isAdvertiseEnabled()) {
            int interval = Math.max(5, config.getAdvertiseBroadcastIntervalSeconds());
            broadcast.startBroadcastTask(interval);
            logger.info("Reklam duyuru görevi yeniden başlatıldı (" + interval + "s aralık).");
        }
    }
}
