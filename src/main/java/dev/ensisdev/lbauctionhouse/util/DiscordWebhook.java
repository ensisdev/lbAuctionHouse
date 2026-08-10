package dev.ensisdev.lbauctionhouse.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Basit Discord Webhook göndericisi — satış/bid bildirimlerini Discord'a iletir.
 */
public class DiscordWebhook {

    /** Discord webhook URL formatı: https://discord.com/api/webhooks/{id}/{token} (veya *.discord.com) */
    private static final java.util.regex.Pattern WEBHOOK_URL_PATTERN = java.util.regex.Pattern.compile(
            "^https://[\\w.-]*discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$");

    private final String url;
    private final String username;
    private final String avatarUrl;

    public DiscordWebhook(String url) {
        this(url, null, null);
    }

    public DiscordWebhook(String url, String username, String avatarUrl) {
        this.url = url;
        this.username = username;
        this.avatarUrl = avatarUrl;
    }

    /**
     * Webhook URL'inin geçerli bir Discord webhook adresi olup olmadığını doğrular.
     * <p>
     * Güvenlik: yalnızca https + discord.com ailesi host'ları kabul edilir.
     * Bu sayede SSRF benzeri istekler (localhost, dahili IP vb.) baştan engellenir.
     */
    public static boolean isValidWebhookUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && WEBHOOK_URL_PATTERN.matcher(url).matches();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Basit mesaj gönderir.
     */
    public void send(String message) {
        if (!isValidWebhookUrl(url)) return;
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"content\":\"").append(escapeJson(message)).append("\"");
            if (username != null) json.append(",\"username\":\"").append(escapeJson(username)).append("\"");
            if (avatarUrl != null) json.append(",\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\"");
            json.append("}");

            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                conn.getResponseCode();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            // Discord hatası kritik değil, sessiz geç
        }
    }

    /**
     * Satış/teklif bildirimi gönderir.
     */
    public static void notifySale(String webhookUrl, String playerName, String itemName, double price) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("🛒 **" + playerName + "** **" + itemName + "** eşyasını **" + fmt(price) + "** karşılığında satın aldı!");
    }

    public static void notifyBid(String webhookUrl, String playerName, String itemName, double amount) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("🔨 **" + playerName + "** **" + itemName + "** ilanına **" + fmt(amount) + "** teklif verdi!");
    }

    /** Yeni ilan bildirimi. */
    public static void notifyListing(String webhookUrl, String sellerName, String itemName, double price) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("📦 **" + sellerName + "** **" + itemName + "** eşyasını **" + fmt(price) + "** fiyatına ihaleye koydu!");
    }

    /** Admin eylemi bildirimi (temizlik, ilan kaldırma vb.). */
    public static void notifyAdminAction(String webhookUrl, String adminName, String action, String detail) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("🛡️ **" + adminName + "** " + action
                + (detail != null && !detail.isEmpty() ? " — " + detail : ""));
    }

    /** Oyuncu yasaklama bildirimi. */
    public static void notifyBan(String webhookUrl, String adminName, String targetName, String reason) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("⛔ **" + adminName + "** **" + targetName + "** oyuncusunu ihalelerden yasakladı!"
                + (reason != null && !reason.isEmpty() ? " Sebep: " + reason : ""));
    }

    /** Lootbox açılış bildirimi. */
    public static void notifyLootbox(String webhookUrl, String playerName, String itemName) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("🎁 **" + playerName + "** lootbox'tan **" + itemName + "** kazandı!");
    }

    /** Pazarlık teklifi kabul bildirimi. */
    public static void notifyOfferAccepted(String webhookUrl, String buyerName, String sellerName, String itemName, double price) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("🤝 **" + buyerName + "** ile **" + sellerName + "** anlaştı: **" + itemName + "** → **" + fmt(price) + "**");
    }

    private static String fmt(double v) {
        return String.format("%,.0f", v) + "₺";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
