package dev.ensisdev.lbauctionhouse.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Basit Discord Webhook göndericisi — satış/bid bildirimlerini Discord'a iletir.
 */
public class DiscordWebhook {

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
     * Basit mesaj gönderir.
     */
    public void send(String message) {
        if (url == null || url.isEmpty()) return;
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"content\":\"").append(escapeJson(message)).append("\"");
            if (username != null) json.append(",\"username\":\"").append(escapeJson(username)).append("\"");
            if (avatarUrl != null) json.append(",\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\"");
            json.append("}");

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
        new DiscordWebhook(webhookUrl).send("**" + playerName + "** " + itemName + " eşyasını **" + String.format("%,.0f", price) + "₺** karşılığında satın aldı!");
    }

    public static void notifyBid(String webhookUrl, String playerName, String itemName, double amount) {
        if (webhookUrl == null || webhookUrl.isEmpty()) return;
        new DiscordWebhook(webhookUrl).send("**" + playerName + "** " + itemName + " ilanına **" + String.format("%,.0f", amount) + "₺** teklif verdi!");
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
