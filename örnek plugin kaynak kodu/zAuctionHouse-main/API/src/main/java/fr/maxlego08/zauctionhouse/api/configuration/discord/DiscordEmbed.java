package fr.maxlego08.zauctionhouse.api.configuration.discord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DiscordEmbed {

    private final List<Field> fields = new ArrayList<>();
    private String title;
    private String description;
    private int color;
    private Footer footer;
    private Author author;
    private Thumbnail thumbnail;
    private Image image;
    private String timestamp;

    // Webhook-level options
    private String username;
    private String avatarUrl;
    private String content;

    public static Builder builder() {
        return new Builder();
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");

        List<String> rootParts = new ArrayList<>();

        // Webhook-level options
        if (username != null && !username.isEmpty()) {
            rootParts.add("\"username\":" + escapeJson(username));
        }

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            rootParts.add("\"avatar_url\":" + escapeJson(avatarUrl));
        }

        if (content != null && !content.isEmpty()) {
            rootParts.add("\"content\":" + escapeJson(content));
        }

        // Build embed
        StringBuilder embedJson = new StringBuilder("\"embeds\":[{");

        List<String> parts = new ArrayList<>();

        if (title != null && !title.isEmpty()) {
            parts.add("\"title\":" + escapeJson(title));
        }

        if (description != null && !description.isEmpty()) {
            parts.add("\"description\":" + escapeJson(description));
        }

        parts.add("\"color\":" + color);

        if (!fields.isEmpty()) {
            StringBuilder fieldsJson = new StringBuilder("\"fields\":[");
            List<String> fieldStrings = new ArrayList<>();
            for (Field field : fields) {
                fieldStrings.add("{\"name\":" + escapeJson(field.name) + ",\"value\":" + escapeJson(field.value) + ",\"inline\":" + field.inline + "}");
            }
            fieldsJson.append(String.join(",", fieldStrings));
            fieldsJson.append("]");
            parts.add(fieldsJson.toString());
        }

        if (footer != null && footer.text != null && !footer.text.isEmpty()) {
            StringBuilder footerJson = new StringBuilder("\"footer\":{\"text\":" + escapeJson(footer.text));
            if (footer.iconUrl != null && !footer.iconUrl.isEmpty()) {
                footerJson.append(",\"icon_url\":").append(escapeJson(footer.iconUrl));
            }
            footerJson.append("}");
            parts.add(footerJson.toString());
        }

        if (author != null && author.name != null && !author.name.isEmpty()) {
            StringBuilder authorJson = new StringBuilder("\"author\":{\"name\":" + escapeJson(author.name));
            if (author.url != null && !author.url.isEmpty()) {
                authorJson.append(",\"url\":").append(escapeJson(author.url));
            }
            if (author.iconUrl != null && !author.iconUrl.isEmpty()) {
                authorJson.append(",\"icon_url\":").append(escapeJson(author.iconUrl));
            }
            authorJson.append("}");
            parts.add(authorJson.toString());
        }

        if (thumbnail != null && thumbnail.url != null && !thumbnail.url.isEmpty()) {
            parts.add("\"thumbnail\":{\"url\":" + escapeJson(thumbnail.url) + "}");
        }

        if (image != null && image.url != null && !image.url.isEmpty()) {
            parts.add("\"image\":{\"url\":" + escapeJson(image.url) + "}");
        }

        if (timestamp != null && !timestamp.isEmpty()) {
            parts.add("\"timestamp\":" + escapeJson(timestamp));
        }

        embedJson.append(String.join(",", parts));
        embedJson.append("}]");

        rootParts.add(embedJson.toString());
        json.append(String.join(",", rootParts));
        json.append("}");

        return json.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "null";

        StringBuilder escaped = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < ' ') {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        escaped.append("\"");
        return escaped.toString();
    }

    public static class Builder {
        private final DiscordEmbed embed = new DiscordEmbed();

        public Builder title(String title) {
            embed.title = title;
            return this;
        }

        public Builder description(String description) {
            embed.description = description;
            return this;
        }

        public Builder color(String hexColor) {
            embed.color = parseColor(hexColor);
            return this;
        }

        public Builder color(int color) {
            embed.color = color;
            return this;
        }

        public Builder addField(String name, String value, boolean inline) {
            embed.fields.add(new Field(name, value, inline));
            return this;
        }

        public Builder footer(String text, String iconUrl) {
            embed.footer = new Footer(text, iconUrl);
            return this;
        }

        public Builder author(String name, String url, String iconUrl) {
            embed.author = new Author(name, url, iconUrl);
            return this;
        }

        public Builder thumbnail(String url) {
            embed.thumbnail = new Thumbnail(url);
            return this;
        }

        public Builder image(String url) {
            embed.image = new Image(url);
            return this;
        }

        public Builder timestamp(boolean addTimestamp) {
            if (addTimestamp) {
                embed.timestamp = Instant.now().toString();
            }
            return this;
        }

        public Builder username(String username) {
            embed.username = username;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            embed.avatarUrl = avatarUrl;
            return this;
        }

        public Builder content(String content) {
            embed.content = content;
            return this;
        }

        public DiscordEmbed build() {
            return embed;
        }

        private int parseColor(String hexColor) {
            if (hexColor == null || hexColor.isEmpty()) {
                return 0xFFFFFF;
            }

            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                return 0xFFFFFF;
            }
        }
    }

    private record Field(String name, String value, boolean inline) {
    }

    private record Footer(String text, String iconUrl) {
    }

    private record Author(String name, String url, String iconUrl) {
    }

    private record Thumbnail(String url) {
    }

    private record Image(String url) {
    }
}
