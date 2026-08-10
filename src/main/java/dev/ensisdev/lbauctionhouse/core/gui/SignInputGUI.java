package dev.ensisdev.lbauctionhouse.core.gui;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.util.ColorUtil;

import net.wesjd.anvilgui.AnvilGUI;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Örs (Anvil) arayüzü ile metin girişi.
 * <p>
 * Eski tabela (Sign) tabanlı uygulamanın yerine AnvilGUI kütüphanesi kullanılır.
 * Sol slota bir kağıt (PAPER) item'ı konur; oyuncu kağıdın ismini (input metnini)
 * değiştirip çıktı slotuna (OUTPUT) tıkladığında yazılan isim geri çağrıya verilir.
 * API'si ({@link #create}, {@link #lines}, {@link #onComplete}, {@link #onClose}, {@link #open})
 * aynen korunur; çağıran sınıflar değişmeden çalışır.
 */
public class SignInputGUI {

    private final LbAuctionHouse plugin;
    private final Player player;
    private BiConsumer<Player, String> onComplete;
    private Consumer<Player> onClose;
    private String[] defaultLines;

    private SignInputGUI(LbAuctionHouse plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.defaultLines = new String[]{"", "~~~~~~~~~~~", "Arama metnini", "yazın"};
    }

    public static SignInputGUI create(LbAuctionHouse plugin, Player player) {
        return new SignInputGUI(plugin, player);
    }

    public SignInputGUI lines(String... lines) {
        this.defaultLines = lines;
        return this;
    }

    public SignInputGUI onComplete(BiConsumer<Player, String> callback) {
        this.onComplete = callback;
        return this;
    }

    public SignInputGUI onClose(Consumer<Player> callback) {
        this.onClose = callback;
        return this;
    }

    public void open() {
        // handled: metin işlendiğinde true olur. Böylece AnvilGUI'nin kapanma
        // event'i (ResponseAction.close() sonrası tetiklenir) onClose callback'ini
        // ikinci kez çağırmaz. ESC ile kapanışta ise handled=false olduğundan
        // kullanıcının onClose callback'i normal şekilde çalışır.
        boolean[] handled = {false};

        // Sol slot için kağıt item: Oyuncu bu kağıdın ismini değiştirerek giriş yapar.
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(buildInitialText());
            paper.setItemMeta(meta);
        }

        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(buildTitle())
                .itemLeft(paper)
                .text(buildInitialText())
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }
                    String text = snapshot.getText();
                    if (text == null || text.trim().isEmpty()) {
                        handled[0] = true;
                        if (onClose != null) onClose.accept(snapshot.getPlayer());
                    } else {
                        handled[0] = true;
                        if (onComplete != null) onComplete.accept(snapshot.getPlayer(), text.trim());
                    }
                    return Collections.singletonList(AnvilGUI.ResponseAction.close());
                })
                .onClose(snapshot -> {
                    if (!handled[0] && onClose != null) {
                        onClose.accept(snapshot.getPlayer());
                    }
                })
                .open(player);
    }

    /**
     * Örs başlığını tablodaki yönlendirme satırlarından üretir.
     * Ör. {@code ["", "~~~~~~~~~~~", "&#FFD54FFiyatı yazın", "&#8c8c8c( sayı )"]}
     * → başlık: "Fiyatı yazın" (renkler uygulanmış).
     */
    private String buildTitle() {
        if (defaultLines != null) {
            // 3. satır (index 2) yönlendirme metnidir
            if (defaultLines.length > 2 && defaultLines[2] != null && !defaultLines[2].trim().isEmpty()) {
                return ColorUtil.colorize(defaultLines[2]);
            }
            // Fallback: ilk anlamlı satır
            for (String line : defaultLines) {
                if (line != null && !line.trim().isEmpty() && !line.trim().startsWith("~")) {
                    return ColorUtil.colorize(line);
                }
            }
        }
        return "Metin girin";
    }

    /**
     * Kağıt item'ın başlangıç ismini üretir: defaultLines'ın ilk anlamlı satırı
     * (ör. "&7Bir şey yazın") renksiz haliyle kağıdın display name'i olur.
     * Böylece oyuncu kağıdın üzerinde ne yazacağını görür ve direkt değiştirir.
     */
    private String buildInitialText() {
        if (defaultLines != null) {
            for (String line : defaultLines) {
                if (line != null && !line.trim().isEmpty() && !line.trim().startsWith("~")) {
                    return ColorUtil.colorize(line);
                }
            }
        }
        return "Bir şey yazın";
    }
}