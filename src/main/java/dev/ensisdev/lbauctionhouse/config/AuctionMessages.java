package dev.ensisdev.lbauctionhouse.config;

import dev.ensisdev.lbauctionhouse.LbAuctionHouse;
import dev.ensisdev.lbauctionhouse.core.addon.AuctionAPI;

import net.kyori.adventure.text.Component;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mesaj yönetimi — Core'un {@link dev.ensisdev.lbauctionhouse.core.config.LanguageManager}'ına
 * addon mesajlarını kaydeder.
 * <p>
 * Tüm mesajlar {@code messages.yml} dosyasından okunur.
 * Placeholder formatı: {@code %seller%}, {@code %price%}, {@code %item%} vb.
 * <p>
 * Kullanım:
 * <pre>
 * auctionMessages.get("listing.sold", "seller", "Enes", "price", "100");
 * </pre>
 */
public class AuctionMessages {

    private final LbAuctionHouse plugin;
    private final AuctionAPI api;
    private final Logger logger;

    public AuctionMessages(LbAuctionHouse plugin, AuctionAPI api) {
        this.plugin = plugin;
        this.api = api;
        this.logger = api.getLogger();
    }

    /**
     * messages.yml dosyasını Core LanguageManager'a kaydeder.
     * <p>
     * 1) JAR'daki gömülü messages.yml data klasörüne kopyalanır (yoksa; "false" = üzerine yazmaz).
     * 2) Ardından <b>diskteki gerçek messages.yml</b> dosyası okunur ve LanguageManager'a verilir.
     *    Sunucu sahibinin messages.yml üzerindeki düzenlemeleri JAR'daki gömülü kopyayı ezmeden kullanılır.
     */
    public void register() {
        registerFromDisk(true);
    }

    /**
     * Reload sırasında çağrılır — messages.yml disketen YENİDEN okunur.
     * JAR varsayılanı yeniden kopyalanmaz, mevcut disk dosyası korunur.
     */
    public void reload() {
        registerFromDisk(false);
    }

    private void registerFromDisk(boolean saveDefaults) {
        var lang = api.getLanguageManager();

        // 1) JAR'daki gömülü varsayılanı diske kopyala — yalnızca dosya YOKSA
        if (saveDefaults) {
            try {
                plugin.saveResource("messages.yml", false);
            } catch (Exception e) {
                // TAM stack trace — hata yutulmaz (örn: JAR'da messages.yml yoksa IllegalArgument)
                logger.log(Level.SEVERE, "[lbAuctionHouse-Auction] messages.yml diske kopyalanamadı (JAR'da eksik olabilir).", e);
            }
        }

        // 2) Varsayılan messages.yml'i kaydet (Türkçe taban — her dilde olmayan anahtarlar buradan düşer)
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (messagesFile.exists()) {
            lang.registerAddonMessages("Auction", messagesFile);
        } else {
            // Fallback: diskte yoksa JAR içindeki kaynağı kullan
            InputStream in = plugin.getResource("messages.yml");
            if (in != null) {
                logger.warning("[lbAuctionHouse-Auction] messages.yml diskte bulunamadı, JAR içindeki kaynak kullanılıyor.");
                lang.registerAddonMessages("Auction", in);
            } else {
                logger.severe("[lbAuctionHouse-Auction] messages.yml NE diskte NE JAR'da bulunamadı! Mesajlar gösterilemez.");
                return;
            }
        }

        // 3) Seçili MESAJ dilini belirle (commands.yml'deki command-lang ile aynı mantık):
        //    a) messages.yml içindeki "message-lang" (none | tr | en | de | fr | ar)
        //    b) yoksa → config.yml içindeki "lang"
        String msgLangCode = null;
        if (messagesFile.exists()) {
            FileConfiguration diskMessages = YamlConfiguration.loadConfiguration(messagesFile);
            String ml = diskMessages.getString("message-lang", "");
            if (ml != null && !ml.isEmpty()) msgLangCode = ml;
        }
        if (msgLangCode == null) {
            AuctionConfig cfg = plugin.getAuctionConfig();
            msgLangCode = cfg != null ? cfg.getMessageLangCode() : "tr";
        }

        // "none" → dil dosyası birleştirilmez; messages.yml olduğu gibi kullanılır.
        if (!"none".equalsIgnoreCase(msgLangCode)) {
            File msgLangFile = new File(plugin.getDataFolder(), "lang" + File.separator + msgLangCode + ".yml");
            if (msgLangFile.exists()) {
                lang.registerAddonMessages("Auction", msgLangFile, true);
            } else {
                logger.warning("[lbAuctionHouse-Auction] Mesaj dili dosyası bulunamadı: lang/" + msgLangCode
                        + ".yml — varsayılan (messages.yml) kullanılıyor.");
            }
        }
    }

    /**
     * Ham mesaj metnini döndürür.
     */
    public String getRaw(String key) {
        return api.getLanguageManager().getRaw("auction." + key);
    }

    /**
     * Mesajı Component olarak döndürür (placeholder'lar ile).
     */
    public Component get(String key, String... placeholders) {
        return api.getLanguageManager().get("auction." + key, placeholders);
    }

    /**
     * Mesajı prefix ile döndürür.
     */
    public Component getPrefixed(String key, String... placeholders) {
        return api.getLanguageManager().getPrefixed("auction." + key, placeholders);
    }

    /**
     * Placeholder formatını dönüştür: {@code %seller%} → {@code {seller}}
     * Core LanguageManager {@code {key}} formatını kullanır.
     */
    public String[] convertPlaceholders(String... pairs) {
        // pairs: "seller", "Enes", "price", "100"
        // → "seller", "Enes", "price", "100" (Core zaten {seller} formatını kullanır)
        // messages.yml'de %seller% kullanılıyorsa convert et
        return pairs;
    }
}
