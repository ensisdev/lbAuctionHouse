package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;

/**
 * /auction reload — tüm yapılandırmayı yeniden yükler.
 * Sunucu restart'ı gerekmez.
 */
public class CmdReload extends AuctionCmd {

    public CmdReload() {
        super("reload", "lbsmpcore.auction.admin", true);
        setAliases("yenile", "rld");
        setDescription("Config yeniden yükle (admin)");
    }

    @Override
    protected void execute() {
        config.reloadAll();
        // messages.yml'i disketen YENİDEN oku (JAR varsayılanı ezilmez, kullanıcı düzenlemesi korunur)
        plugin.getAuctionMessages().reload();
        plugin.getGuiLayoutLoader().clearCache();
        // Eksik gui/*.yml dosyalarını diske yaz ve layout cache'ini doldur
        plugin.getGuiLayoutLoader().preloadAll();
        // Dil değiştiyse komut aliaslarını güncelle
        plugin.getAuctionCmdManager().reloadCommands();
        msg("reload");
        msg("reload-restart");
    }
}
