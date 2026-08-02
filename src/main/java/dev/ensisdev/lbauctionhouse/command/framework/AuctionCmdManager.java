package dev.ensisdev.lbauctionhouse.command.framework;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.AuctionManager;
import dev.ensisdev.lbauctionhouse.config.AuctionConfig;
import dev.ensisdev.lbauctionhouse.config.AuctionMessages;
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
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 500;
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
     */
    public void register() {
        // Tüm alt komutları oluştur — CmdAdmin artık ayrı bir komuttur (/ihaleadmin)
        registerSub(new CmdBrowse());
        registerSub(new CmdSell());
        registerSub(new CmdMylistings());
        registerSub(new CmdCollect());
        registerSub(new CmdRemove());
        registerSub(new CmdReload());
        registerSub(new CmdStats());
        registerSub(new CmdBan());
        registerSub(new CmdSearch());
        registerSub(new CmdTrade());
        registerSub(new CmdView());

        // Ana komut: language file'dan (seçili dil)
        registerMainCommand();

        // Yönetim komutu: ana komuttan BAĞIMSIZ ayrı komut (örn. /ihaleadmin)
        registerAdminCommand();
    }

    /**
     * Yönetim komutunu (ör: /ihaleadmin) CommandMap'e kaydeder.
     * Admin alt komutları bu komut üzerinden doğrudan çalışır — tab completer dahil.
     */
    private void registerAdminCommand() {
        String adminCmd = config.getAdminCommand();
        List<String> aliases = new ArrayList<>(config.getAdminAliases());
        if (aliases.isEmpty()) aliases.add("ahadmin");

        AdminCommandExecutor adminExecutor = new AdminCommandExecutor(
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
     * kullanır (lbsmpcore:isim) — admin bu durumu konsoldan net görebilir.
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

            boolean registered = commandMap.register("lbsmpcore", custom);

            // Kayıt sonrası GERÇEK ismi doğrula ve logla.
            Command actual = commandMap.getCommand(name);
            if (actual != null) {
                logger.info("Komut kaydedildi: /" + actual.getLabel()
                        + " (aliases: " + aliases + ").");
            } else {
                // İsim başka bir komut tarafından kapılmış olabilir → fallback prefix.
                Command fallback = commandMap.getCommand("lbsmpcore:" + name);
                if (fallback != null) {
                    logger.warn("Komut FALLBACK PREFIX ile kaydedildi: /lbsmpcore:" + name
                            + " (çünkü /" + name + " adında başka bir komut zaten kayıtlı). "
                            + "Oyuncular komutu /lbsmpcore:" + name + " şeklinde kullanmak zorunda.");
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
     */
    private void registerSub(AuctionCmd cmd) {
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

        // Rate limiting (500ms cooldown) + periyodik cleanup
        if (sender instanceof Player p) {
            long now = System.currentTimeMillis();
            if (now - lastCooldownCleanup > 60_000) {
                lastCooldownCleanup = now;
                cooldowns.values().forEach(m -> m.values().removeIf(t -> now - t > 60_000));
                cooldowns.entrySet().removeIf(e -> e.getValue().isEmpty());
            }
            Map<String, Long> pc = cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
            Long last = pc.get(sub.getName());
            if (last != null && (now - last) < 500) return true;
            pc.put(sub.getName(), now);
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
            return subCommands.stream()
                    .filter(s -> s.getPermission().isEmpty() || sender.hasPermission(s.getPermission()))
                    .map(AuctionCmd::getDisplayName)  // yerelleştirilmiş birincil adlar
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            // admin <alt-komut> için seçili dildeki aliasları tamamla
            if (isSubName("admin", sub)) {
                return getAdminSubCommands().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            // remove <uuid> için aktif ilan UUID'lerini öner (limit 20)
            if (isSubName("remove", sub) && sender.hasPermission("lbsmpcore.auction.admin")) {
                return manager.getData().getActiveListings().stream()
                        .map(l -> l.id().toString())
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .limit(20)
                        .collect(Collectors.toList());
            }
        }

        // admin logs <limit> — sayı öner
        if (args.length == 3 && isSubName("admin", args[0].toLowerCase()) && matchAdminSub("logs", args[1])) {
            return List.of("10", "20", "50", "100").stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // admin remove <uuid> — UUID öner (limit 20)
        if (args.length == 3 && isSubName("admin", args[0].toLowerCase()) && matchAdminSub("remove", args[1])) {
            if (sender.hasPermission("lbsmpcore.auction.admin")) {
                return manager.getData().getActiveListings().stream()
                        .map(l -> l.id().toString())
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .limit(20)
                        .collect(Collectors.toList());
            }
        }

        // admin ban <oyuncu> — çevrimiçi oyuncu adları öner
        if (args.length == 3 && isSubName("admin", args[0].toLowerCase()) && matchAdminSub("ban", args[1])) {
            if (sender.hasPermission("lbsmpcore.auction.admin")) {
                return org.bukkit.Bukkit.getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        // admin unban <oyuncu/UUID> — çevrimiçi oyuncu adları öner
        if (args.length == 3 && isSubName("admin", args[0].toLowerCase()) && matchAdminSub("unban", args[1])) {
            if (sender.hasPermission("lbsmpcore.auction.admin")) {
                return org.bukkit.Bukkit.getOnlinePlayers().stream()
                        .map(org.bukkit.entity.Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
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
     * Tüm admin alt-komut isimlerini + aliaslarını döndürür.
     * Değerler commands.yml / lang dosyasından çözülür (AuctionConfig).
     */
    private List<String> getAdminSubCommands() {
        List<String> result = new ArrayList<>();
        for (String key : List.of("stats", "logs", "clear", "remove", "ban", "unban", "banlist")) {
            result.addAll(config.getAdminSubAliases(key));
        }
        return result;
    }

    /**
     * Bir admin alt-komut adının beklenen değerle eşleşip eşleşmediğini kontrol eder.
     * @param expected beklenen ana isim (ör: "logs", "remove")
     * @param input   kullanıcının girdiği değer (alias da olabilir)
     */
    private boolean matchAdminSub(String expected, String input) {
        return config.isAdminSub(expected, input);
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
