package dev.ensisdev.lbauctionhouse.command.framework;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.AuctionMessages;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Auction alt komutları için abstract base sınıf.
 * <p>
 * Her alt komut ayrı bir sınıf olarak yazılır:
 * <pre>
 * public class CmdSell extends AuctionCmd {
 *     public CmdSell() {
 *         super("sell", "lbsmpcore.auction.sell", false);
 *         setAliases("sat", "list");
 *         setUsage("<fiyat>");
 *         setDescription("Eşya sat");
 *     }
 *     protected void execute() {
 *         // args[0] = fiyat
 *     }
 * }
 * </pre>
 */
public abstract class AuctionCmd {

    private final String name;
    private final String permission;
    private final boolean consoleCanUse;
    private String displayName;   // yerelleştirilmiş birincil ad (lang/commands.yml'den)
    private String[] aliases = new String[0];
    private String usage = "";
    private String description = "";

    // Context — set before execute()
    protected LbAuctionHouse plugin;
    protected AuctionManager manager;
    protected AuctionConfig config;
    protected AuctionMessages messages;
    protected AddonLogger logger;
    protected CommandSender sender;
    protected Player player;
    protected String[] args;
    protected String label;

    protected AuctionCmd(String name, String permission, boolean consoleCanUse) {
        this.name = name;
        this.permission = permission;
        this.consoleCanUse = consoleCanUse;
        this.displayName = name;
    }

    /**
     * Alt komut çalıştırılır. {@link #sender}, {@link #player}, {@link #args} hazırdır.
     */
    protected abstract void execute();

    /**
     * Alt komutu dışarıdan çalıştırmak için public köprü.
     * {@link AdminCommandExecutor} gibi alt komut sınıfının dışındaki çağırıcılar
     * (protected execute'e erişemeyen) bu metodu kullanır.
     */
    public void runCommand() {
        execute();
    }

    // ---- Chainable config ----

    public AuctionCmd setAliases(String... aliases) {
        this.aliases = aliases;
        return this;
    }

    public AuctionCmd setUsage(String usage) {
        this.usage = usage;
        return this;
    }

    public AuctionCmd setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Yerelleştirilmiş birincil adı ayarlar (lang dosyasındaki ilk öğe).
     * Boş/null verilirse iç isme (name) döner.
     */
    public void setDisplayName(String displayName) {
        this.displayName = (displayName == null || displayName.isEmpty()) ? name : displayName;
    }

    // ---- Getters ----

    public String getName() { return name; }

    /**
     * Yerelleştirilmiş birincil ad (tab tamamlama ve eşleşmede kullanılır).
     * Hiçbir zaman null dönmez — ayarlanmamışsa iç isim.
     */
    public String getDisplayName() { return displayName == null ? name : displayName; }
    /**
     * Permission'ı döndürür — hiçbir zaman {@code null} dönmez.
     * (Bazı alt komutlar yapıcıda {@code null} geçebilir; null kontrolü
     * {@code onTabComplete}/{@code executeSub} içinde NPE'ye yol açar.)
     */
    public String getPermission() { return permission == null ? "" : permission; }
    public boolean isConsoleCanUse() { return consoleCanUse; }
    public String[] getAliases() { return aliases; }
    public String getUsage() { return usage; }
    public String getDescription() { return description; }

    // ---- Yardımcılar ----

    protected boolean hasArg(int index) {
        return args != null && args.length > index;
    }

    protected String arg(int index) {
        return hasArg(index) ? args[index] : null;
    }

    protected String arg(int index, String def) {
        return hasArg(index) ? args[index] : def;
    }

    protected double argDouble(int index) {
        try {
            return Double.parseDouble(arg(index));
        } catch (NumberFormatException | NullPointerException e) {
            return Double.NaN;
        }
    }

    /**
     * Tek argümanlı mesaj gönderimi.
     * <p>
     * String bir renk kodu (§) veya MiniMessage (<) içeriyorsa HAM metin olarak
     * gönderilir; aksi halde messages.yml'deki bir anahtar olarak yorumlanır
     * (örn: "admin.stats-header"). Böylece hem {@code msg("§6Başlık")} hem de
     * {@code msg("admin.stats-header")} doğru davranır.
     */
    protected void msg(String message) {
        if (message == null) return;
        if (message.isEmpty() || message.indexOf('§') >= 0 || message.indexOf('<') >= 0) {
            sender.sendMessage(message);
        } else {
            sender.sendMessage(messages.getPrefixed(message));
        }
    }

    /**
     * Mesaj anahtarı + placeholder'lar ile gönderim. Anahtar her zaman config'den çözülür.
     */
    protected void msg(String key, String... placeholders) {
        sender.sendMessage(messages.getPrefixed(key, placeholders));
    }

    protected void usage() {
        msg("§cKullanım: /" + label + " " + name + " " + usage);
    }

    protected void noPerm() {
        msg("§cYetkin yok!");
    }

    // ---- Context inject ----

    public void inject(LbAuctionHouse plugin, AuctionManager manager, AuctionConfig config,
                       AuctionMessages messages, AddonLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.messages = messages;
        this.logger = logger;
    }
}
