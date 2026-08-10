package dev.ensisdev.lbauctionhouse.command.framework;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.AuctionMessages;
import dev.ensisdev.lbauctionhouse.config.FeatureRegistry;
import dev.ensisdev.lbauctionhouse.command.cmd.*;
import dev.ensisdev.lbauctionhouse.core.addon.AddonLogger;

import java.lang.reflect.Field;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/**
 * Komut yöneticisi — tüm alt komutları kaydeder ve yönlendirir.
 * <p>
 * CommandMap üzerinden dinamik kayıt yapar, böylece commands.yml'deki
 * komut isimleri ve aliaslar birebir çalışır.
 */
public class AuctionCmdManager implements CommandExecutor, TabCompleter {

    private static CommandMap commandMap;

    static {
        try {
            // Paper 1.20.5+ ve Spigot arasında field adı farklı olabilir
            Class<?> serverClass = Bukkit.getServer().getClass();
            Field field = null;
            try {
                field = serverClass.getDeclaredField("commandMap");
            } catch (NoSuchFieldException e1) {
                try {
                    field = serverClass.getSuperclass().getDeclaredField("commandMap");
                } catch (NoSuchFieldException e2) {
                    // Paper 1.20.5+: CraftServer → SimpleCommandMap
                    for (Field f : serverClass.getDeclaredFields()) {
                        if (CommandMap.class.isAssignableFrom(f.getType())) {
                            field = f;
                            break;
                        }
                    }
                }
            }
            if (field != null) {
                field.setAccessible(true);
                commandMap = (CommandMap) field.get(Bukkit.getServer());
            }
        } catch (Exception e) {
            // System.err yerine logger — stack trace'i yutmayız ama Nag uyarısı çıkmaz.
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                    "[lbAuctionHouse] CommandMap alınamadı!", e);
        }
    }

    private final LbAuctionHouse plugin;
    private final AuctionManager manager;
    private final AuctionConfig config;
    private final AuctionMessages messages;
    private final AddonLogger logger;

    private final List<AuctionCmd> subCommands = new ArrayList<>();
    private AdminCommandExecutor adminExecutor;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private long lastCooldownCleanup = 0;

    public AuctionCmdManager(LbAuctionHouse plugin, AuctionManager manager,
                             AuctionConfig config, AuctionMessages messages,
                             AddonLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        this.messages = messages;
        this.logger = logger;
    }

    /**
     * Tüm alt komutları kaydeder ve ana komutu CommandMap'e ekler.
     * <p>
     * Her alt komut bir {@link FeatureRegistry.Keys feature anahtarına} bağlanır.
     * {@code features.yml}'de ilgili özellik {@code false} ise o komut hiç kaydedilmez.
     */
    public void register() {
        // Tüm alt komutları oluştur — her biri bir özellik anahtarına bağlı
        registerSubCommands();

        // Ana komut: language file'dan (seçili dil)
        registerMainCommand();

        // Yönetim komutu: ana komuttan BAĞIMSIZ ayrı komut (örn. /ihaleadmin)
        registerAdminCommand();
    }

    /**
     * Tüm alt komutları (feature gate'lere göre) yeniden oluşturur.
     * <p>
     * Önce listeyi temizler, ardından features.yml'deki güncel duruma göre
     * komutları yeniden ekler. Hem {@link #register()} hem de
     * {@link #reloadCommands()} buradan geçer — böylece /auction reload ile
     * kapatılan/açılan özelliklerin komutları da anında görünür/gizlenir
     * (sunucu restart'ı gerekmez).
     */
    private void registerSubCommands() {
        subCommands.clear();
        registerSub(new CmdBrowse()
                .setFeatureKey(FeatureRegistry.Keys.BROWSE));
        registerSub(new CmdSell());   // sat — temel fonksiyon, feature gate yok
        registerSub(new CmdMylistings()
                .setFeatureKey(FeatureRegistry.Keys.MY_LISTINGS));
        registerSub(new CmdCollect()
                .setFeatureKey(FeatureRegistry.Keys.COLLECTION_BOX));
        registerSub(new CmdRemove());  // remove — temel fonksiyon (idari işlem)
        registerSub(new CmdReload());  // reload — temel fonksiyon (idari işlem)
        registerSub(new CmdStats());   // stats — temel fonksiyon (idari işlem)
        registerSub(new CmdBan()
                .setFeatureKey(FeatureRegistry.Keys.BAN_SYSTEM));
        registerSub(new CmdSearch()
                .setFeatureKey(FeatureRegistry.Keys.SEARCH));
        registerSub(new CmdTrade()
                .setFeatureKey(FeatureRegistry.Keys.TRADE));
        registerSub(new CmdView()
                .setFeatureKey(FeatureRegistry.Keys.PLAYER_LISTINGS));
        registerSub(new CmdNegotiate()
                .setFeatureKey(FeatureRegistry.Keys.NEGOTIATION));
        registerSub(new CmdFavorites()
                .setFeatureKey(FeatureRegistry.Keys.FAVORITES));
        registerSub(new CmdHistory()
                .setFeatureKey(FeatureRegistry.Keys.HISTORY));
    }

    /**
     * Yönetim komutunu (ör: /ihaleadmin) CommandMap'e kaydeder.
     * Admin alt komutları bu komut üzerinden doğrudan çalışır — tab completer dahil.
     */
    private void registerAdminCommand() {
        String adminCmd = config.getAdminCommand();
        List<String> aliases = new ArrayList<>(config.getAdminAliases());
        if (aliases.isEmpty()) aliases.add("ahadmin");

        this.adminExecutor = new AdminCommandExecutor(
                plugin, manager, config, messages, logger);
        registerCustomPluginCommand(adminCmd, aliases, adminExecutor, adminExecutor);
        logger.info("Yönetim komutu: /" + adminCmd + " (aliases: " + aliases + ").");
    }

    /**
     * Ana komutu seçili dil dosyasından okuyarak CommandMap'e kaydeder.
     * reload sırasında da çağrılabilir.
     */
    private void registerMainCommand() {
        String mainCmd = config.getLangMainCommand();  // lang/tr.yml → "ihale"
        List<String> aliases = new ArrayList<>(config.getLangAliases());  // lang/tr.yml → ["ah", "auction", "mezat"]
        if (!aliases.contains("auction")) {
            aliases.add("auction"); // plugin.yml'deki komut her zaman çalışsın
        }

        // 1) plugin.yml'deki "auction" komutunu executor'a bağla — her durumda çalışsın.
        PluginCommand cmd = plugin.getCommand("auction");
        if (cmd == null) {
            logger.error("plugin.getCommand('auction') NULL döndü! plugin.yml'de commands.auction tanımlı mı?");
        } else {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
            cmd.setAliases(aliases);
            // Statik/İngilizce usage yerine boş kullan — gerçek kullanım mesajları
            // messages.yml üzerinden dinamik olarak yönetilir.
            cmd.setUsage("");
            logger.info("Komut '/auction' executor'a bağlandı (aliases: " + aliases + ").");
        }

        // 2) Dil dosyasındaki özel ana komut (örn. "ihale") farklıysa ayrıca kaydet.
        if (!mainCmd.equalsIgnoreCase("auction")) {
            registerCustomPluginCommand(mainCmd, aliases, this, this);
        } else {
            logger.info("Ana komut: /auction (aliases: " + aliases + ").");
        }
    }

    /**
     * Özel bir komutu (ana komut veya yönetim komutu) reflection ile oluşturup
     * CommandMap'e kaydeder.
     * <p>
     * Kayıt sonrası komutun GERÇEKTE hangi isimle kayıtlı olduğu doğrulanır ve loglanır:
     * eğer isim başka bir komut tarafından kapılmışsa CommandMap fallback prefix'i
     * kullanır (lbauctionhouse:isim) — admin bu durumu konsoldan net görebilir.
     *
     * @param name         kaydedilecek komut adı (ör: "ihale", "ihaleadmin")
     * @param aliases      komut aliasları
     * @param executor     komut executor'ı
     * @param tabCompleter tab completer
     */
    private void registerCustomPluginCommand(String name, List<String> aliases,
                                             CommandExecutor executor, TabCompleter tabCompleter) {
        try {
            java.lang.reflect.Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            // Java 17+ modül sisteminde dahi çalışması için newInstance'ten ÖNCE zorunlu.
            constructor.setAccessible(true);
            PluginCommand custom = constructor.newInstance(name, plugin);
            custom.setExecutor(executor);
            custom.setTabCompleter(tabCompleter);
            custom.setAliases(aliases);
            custom.setUsage(""); // statik/İngilizce usage yok — mesajlar dinamik

            boolean registered = commandMap.register("lbauctionhouse", custom);

            // Kayıt sonrası GERÇEK ismi doğrula ve logla.
            Command actual = commandMap.getCommand(name);
            if (actual != null) {
                logger.info("Komut kaydedildi: /" + actual.getLabel()
                        + " (aliases: " + aliases + ").");
            } else {
                // İsim başka bir komut tarafından kapılmış olabilir → fallback prefix.
                Command fallback = commandMap.getCommand("lbauctionhouse:" + name);
                if (fallback != null) {
                    logger.warn("Komut FALLBACK PREFIX ile kaydedildi: /lbauctionhouse:" + name
                            + " (çünkü /" + name + " adında başka bir komut zaten kayıtlı). "
                            + "Oyuncular komutu /lbauctionhouse:" + name + " şeklinde kullanmak zorunda.");
                } else if (!registered) {
                    logger.error("Komut KAYDEDİLEMEDİ: /" + name
                            + " — CommandMap.register false döndü (isim çakışması).");
                } else {
                    logger.error("Komut register çağrısı sonrası CommandMap'te bulunamadı: /" + name);
                }
            }
        } catch (Exception e) {
            // TAM stack trace — reflection hatası yutulmaz, konsolda görünür.
            logger.error("Komut kaydı başarısız: /" + name, e);
        }
    }

    /**
     * Seçili dil dosyasındaki aliasları alt komuta uygular.
     * <p>
     * Eğer bu alt komut bir {@link AuctionCmd#setFeatureKey(String) feature anahtarına} bağlıysa
     * ve features.yml'de o özellik {@code false} ise komut hiç kaydedilmez —
     * listeye eklenmediği için dispatch ve tab-complete otomatik olarak gizlenir
     * (zombi komut kalmaz).
     */
    private void registerSub(AuctionCmd cmd) {
        // Feature gate — kapaliysa sessizce atla (hiç log yok; her reload spam olur)
        String fk = cmd.getFeatureKey();
        if (fk != null && !config.isFeatureEnabled(fk)) {
            return;
        }
        cmd.inject(plugin, manager, config, messages, logger);
        // Birincil (yerelleştirilmiş) ad — listedeki ilk öğe (örn. "sat")
        cmd.setDisplayName(config.getLangSubCommand(cmd.getName()));
        // Aliaslar — listedeki ilk öğe hariç kalanı (commands.yml veya lang dosyasından)
        List<String> langAliases = config.getLangSubAliases(cmd.getName());
        if (langAliases != null && !langAliases.isEmpty()) {
            cmd.setAliases(langAliases.toArray(new String[0]));
        }
        subCommands.add(cmd);
    }

    /**
     * /auction reload çağrıldığında dil değişmiş olabilir.
     * Ana komut aliaslarını güncelle (tam yeniden kayıt için restart gerekir).
     */
    public void reloadCommands() {
        // Feature gate değişikliklerini uygula — kapatılan özelliklerin komutları
        // listeden çıkarılır, yeni açılanlar eklenir (restart gerekmez).
        registerSubCommands();

        // PluginCommand aliaslarını güncelle
        PluginCommand cmd = plugin.getCommand("auction");
        if (cmd != null) {
            cmd.setAliases(config.getLangAliases());
            logger.info("Komut aliasları yeniden yüklendi: " + config.getLangAliases());
        }
        // Alt komut birincil adlarını + aliaslarını güncelle
        for (AuctionCmd sub : subCommands) {
            sub.setDisplayName(config.getLangSubCommand(sub.getName()));
            List<String> aliases = config.getLangSubAliases(sub.getName());
            if (aliases != null && !aliases.isEmpty()) {
                sub.setAliases(aliases.toArray(new String[0]));
            }
        }
        logger.info("Alt komut adları ve aliasları yeniden yüklendi.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            // Ana komut — browse aç
            if (sender instanceof Player player) {
                manager.openMainMenu(player);
            }
            return true;
        }

        String subName = args[0].toLowerCase();

        // Yönetim: /auction admin <alt-komut> → AdminCommandExecutor'a yönlendir
        if (adminExecutor != null && isAdminCommandLabel(subName)) {
            return adminExecutor.onCommand(sender, command, label, trimArgs(args));
        }

        // Alt komut ara (isim + alias karşılaştır)
        for (AuctionCmd sub : subCommands) {
            if (matches(sub, subName)) {
                return executeSub(sub, sender, label, trimArgs(args));
            }
        }

        // Bulunamadıysa anasayfa
        if (sender instanceof Player player) {
            manager.openMainMenu(player);
        }
        return true;
    }

    private boolean executeSub(AuctionCmd sub, CommandSender sender, String label, String[] args) {
        // Console check
        if (!(sender instanceof Player) && !sub.isConsoleCanUse()) {
            sender.sendMessage("§cBu komut sadece oyuncular içindir.");
            return true;
        }

        // Rate limiting (config: limits.command-cooldown-ms) + periyodik cleanup
        if (sender instanceof Player p) {
            long cdMs = config.getCommandCooldownMs();
            if (cdMs > 0) {
                long now = System.currentTimeMillis();
                if (now - lastCooldownCleanup > 60_000) {
                    lastCooldownCleanup = now;
                    cooldowns.values().forEach(m -> m.values().removeIf(t -> now - t > 60_000));
                    cooldowns.entrySet().removeIf(e -> e.getValue().isEmpty());
                }
                Map<String, Long> pc = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
                Long last = pc.get(sub.getName());
                if (last != null && (now - last) < cdMs) return true;
                pc.put(sub.getName(), now);
            }
        }

        // Permission check
        if (!sub.getPermission().isEmpty() && !sender.hasPermission(sub.getPermission())) {
            sub.noPerm();
            return true;
        }

        // Context set
        sub.sender = sender;
        sub.player = sender instanceof Player p ? p : null;
        sub.args = args;
        sub.label = label;

        // Execute
        sub.execute();
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        try {
        if (args.length == 1) {
            List<String> completions = subCommands.stream()
                    .filter(s -> s.getPermission().isEmpty() || sender.hasPermission(s.getPermission()))
                    .map(AuctionCmd::getDisplayName)  // yerelleştirilmiş birincil adlar
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
            // Yönetim komutu da anasayfadan tamamlansın (yetkisi olanlara)
            if (sender.hasPermission("lbauctionhouse.admin")) {
                String adminName = config.getAdminCommand();
                if (adminName.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(adminName);
                }
            }
            return completions;
        }

        // Yönetim: /auction admin <alt-komut> ... → AdminCommandExecutor'a devret
        if (adminExecutor != null && isAdminCommandLabel(args[0].toLowerCase())) {
            return adminExecutor.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        }

        // remove <uuid> için aktif ilan UUID'lerini öner (limit 20)
        if (args.length == 2 && isSubName("remove", args[0].toLowerCase()) && sender.hasPermission("lbauctionhouse.admin")) {
            return manager.getData().getActiveListings().stream()
                    .map(l -> l.id().toString())
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        return List.of();
        } catch (Throwable t) {
            // Tab-complete asla istemciye hata fırlatmaz — TAM stack trace loglanır,
            // istemci boş liste görür ("internal error" mesajı çıkmaz).
            logger.error("Tab-complete hatası (/" + alias + "):", t);
            return List.of();
        }
    }

    /**
     * Girilen ilk argümanın yönetim komutu (isim + aliaslar) olup olmadığını kontrol eder.
     * "admin" (iç isim) de kabul edilir — eski söz dizimiyle uyumluluk.
     */
    private boolean isAdminCommandLabel(String input) {
        if (input.equalsIgnoreCase("admin")) return true;
        if (config.getAdminCommand().equalsIgnoreCase(input)) return true;
        for (String alias : config.getAdminAliases()) {
            if (alias.equalsIgnoreCase(input)) return true;
        }
        return false;
    }

    private boolean isSubName(String name, String input) {
        if (name.equalsIgnoreCase(input)) return true;
        for (AuctionCmd cmd : subCommands) {
            if (cmd.getName().equalsIgnoreCase(name)) {
                for (String alias : cmd.getAliases()) {
                    if (alias.equalsIgnoreCase(input)) return true;
                }
            }
        }
        return false;
    }

    private boolean isSimilar(String expected, String input) {
        return expected.equals(input) || input.startsWith(expected);
    }

    private boolean matches(AuctionCmd cmd, String name) {
        if (cmd.getName().equalsIgnoreCase(name)) return true;           // iç isim (sell, browse)
        if (cmd.getDisplayName().equalsIgnoreCase(name)) return true;    // yerelleştirilmiş birincil ad
        for (String alias : cmd.getAliases()) {
            if (alias.equalsIgnoreCase(name)) return true;               // yerelleştirilmiş aliaslar
        }
        return false;
    }

    private String[] trimArgs(String[] args) {
        if (args.length <= 1) return new String[0];
        return Arrays.copyOfRange(args, 1, args.length);
    }
}
