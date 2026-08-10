package dev.ensisdev.lbauctionhouse.command.cmd;

import dev.ensisdev.lbauctionhouse.command.framework.AuctionCmd;
import dev.ensisdev.lbauctionhouse.config.FeatureRegistry;
import dev.ensisdev.lbauctionhouse.gui.GUILayoutLoader;

import java.util.Locale;
import java.util.Map;

/**
 * /ihaleadmin gui edit — GUI özelleştirme & runtime override komutu.
 * <p>
 * Alt komutlar:
 * <pre>
 *   /ihaleadmin gui list                       → tüm GUI dosyaları & özellikleri listele
 *   /ihaleadmin gui info &lt;file&gt;                → bir GUI dosyasındaki nav itemlarını ve özellikleri listele
 *   /ihaleadmin gui toggle &lt;feature&gt; [true|false]
 *                                                → bir özelliği aç/kapa (features.yml'i override eder)
 *   /ihaleadmin gui reload                     → tüm GUI'leri yeniden yükle
 *   /ihaleadmin gui refresh                    → ek olarak canlı menüleri açık oyuncularda yeniden aç
 * </pre>
 *
 * <p>Not: Slot-by-slot runtime editör (bir slot'a sağ tıklayıp materyal değiştirme akışı)
 * ayrı bir oyun-içi GUI builder modülüdür ve bu skeleton'a dahildir.
 * Daha gelişmiş edit modu için {@code /lbauctionhouse:gui edit} <strong>slot</strong> kullanın
 * (WIP — şu an için placeholder mesaj gösterilir).
 */
public class CmdGuiEdit extends AuctionCmd {

    public CmdGuiEdit() {
        super("gui", "lbauctionhouse.admin", true);
        setAliases("panel");
        setDescription("GUI özelleştirme yönetimi (admin)");
        // Bu komut admin-panel feature'ına bağlıdır (panel komutuyla aynı gruptadır)
        setFeatureKey(FeatureRegistry.Keys.ADMIN_PANEL);
    }

    @Override
    protected void execute() {
        if (!hasArg(0)) {
            showHelp();
            return;
        }

        switch (arg(0).toLowerCase(Locale.ROOT)) {
            case "list", "liste", "listele" -> listGuis();
            case "info", "bilgi" -> showGuiInfo(hasArg(1) ? arg(1) : null);
            case "toggle", "degistir" -> toggleFeature(hasArg(1) ? arg(1) : null,
                                                       hasArg(2) ? arg(2) : null);
            case "reload", "yenile" -> reloadAllGuis();
            case "refresh", "tazele" -> forceUiRefresh();
            case "edit", "duzenle" -> // slot-by-slot edit GUI (şu an için placeholder)
                    editPlaceholder(hasArg(1) ? arg(1) : null);
            default -> showHelp();
        }
    }

    private void showHelp() {
        msg("admin.gui.help-header", "cmd", label);
        msg("admin.gui.help-list");
        msg("admin.gui.help-info");
        msg("admin.gui.help-toggle");
        msg("admin.gui.help-reload");
        msg("admin.gui.help-refresh");
        msg("admin.gui.help-edit");
    }

    /** Tüm GUI dosyalarını listeler. */
    private void listGuis() {
        var all = manager.getLayouts();
        msg("admin.gui.list-header", "count", String.valueOf(all.size()));
        for (Map.Entry<String, GUILayoutLoader.GUILayout> e : all.entrySet()) {
            msg("admin.gui.list-entry", "key", e.getKey(), "rows", String.valueOf(e.getValue().rows()));
        }
    }

    /** Tek GUI dosyasının içeriğini gösterir. */
    @SuppressWarnings("unchecked")
    private void showGuiInfo(String key) {
        if (key == null || key.isBlank()) {
            sender.sendMessage("§cKullanım: /" + label + " gui info <gui-anahtarı>");
            return;
        }
        // Duck-typed erişim — AuctionManager.getLayouts() Map<String, GUILayout> döndürür
        @SuppressWarnings("rawtypes")
        Map<String, Object> layouts = (Map<String, Object>) (Map) manager.getLayouts();
        var layoutRaw = layouts.get(key.toLowerCase(java.util.Locale.ROOT));
        if (layoutRaw == null) {
            sender.sendMessage("§cBilinmeyen GUI anahtarı: " + key + ". 'list' ile geçerli olanlara bakın.");
            return;
        }
        // Reflection-free erişim — GUILayout record'unun rows() ve navItems() methodları çağrılır
        try {
            Object rowsVal = layoutRaw.getClass().getMethod("rows").invoke(layoutRaw);
            java.util.List<Object> navItems = (java.util.List<Object>) layoutRaw.getClass().getMethod("navItems").invoke(layoutRaw);
            sender.sendMessage("§6=== " + key + " (" + rowsVal + " satır) ===");
            for (Object nav : navItems) {
                java.lang.reflect.Method idM = nav.getClass().getMethod("id");
                java.lang.reflect.Method slotM = nav.getClass().getMethod("slot");
                java.lang.reflect.Method amountM = nav.getClass().getMethod("amount");
                java.lang.reflect.Method cmdM = nav.getClass().getMethod("customModelData");
                java.lang.reflect.Method glowM = nav.getClass().getMethod("glow");
                java.lang.reflect.Method hideM = nav.getClass().getMethod("hideFlags");
                sender.sendMessage("§7- §f" + idM.invoke(nav) + " §7[slot=" + slotM.invoke(nav)
                        + ", amount=" + amountM.invoke(nav)
                        + ", cmd=" + cmdM.invoke(nav)
                        + ", glow=" + glowM.invoke(nav)
                        + ", hideFlags=" + hideM.invoke(nav) + "]");
            }
        } catch (Exception ex) {
            sender.sendMessage("§cGUI bilgisi okunamadı: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /** Bir özelliği aç/kapa (features.yml üzerinden kalıcı). */
    private void toggleFeature(String key, String value) {
        if (key == null || key.isBlank()) {
            sender.sendMessage("§cKullanım: /" + label + " gui toggle <feature-key> [true|false]");
            return;
        }
        boolean newValue = value == null
                ? !config.getFeatures().isEnabled(key)
                : Boolean.parseBoolean(value.toLowerCase(Locale.ROOT));
        boolean result = config.getFeatures().set(key, newValue);
        sender.sendMessage((result ? "§a" : "§c") + "Feature §f" + key + "§a → " +
                (result ? "AÇIK" : "KAPALI") + "§a (yeniden yüklemede aktif)");
        sender.sendMessage("§7Kalıcı olması için: /" + label + " reload");
    }

    /** Tüm GUI'leri yeniden yükle. */
    private void reloadAllGuis() {
        // CmdReload.execute ile aynı etki
        new CmdReload().reloaded(this);
        sender.sendMessage("§aTüm GUI dosyaları yeniden yüklendi.");
    }

    /** Tüm açık menüleri açık oyuncularda yeniden aç. */
    private void forceUiRefresh() {
        int count = dev.ensisdev.lbauctionhouse.core.gui.MenuManager.getInstance().refreshAllOpen();
        sender.sendMessage("§a" + count + " açık menü yenilendi.");
    }

    /** Slot-by-slot runtime edit GUI (WIP). */
    private void editPlaceholder(String targetSlot) {
        sender.sendMessage("§eGUI edit modu (WIP)");
        sender.sendMessage("§7Tam sürüm yakında — şu an için features.yml ve gui/*.yml içinden yapabilirsiniz.");
        sender.sendMessage("§7— Yapılandırılabilir alanlar: material, slot, name, lore, amount, custom-model-data,");
        sender.sendMessage("§7  glow, hide-flags, texture (base64) ve link/aksiyon.");
        sender.sendMessage("§7— Canlı override ile: /" + label + " gui toggle &lt;feature&gt; [true|false]");
    }
}
