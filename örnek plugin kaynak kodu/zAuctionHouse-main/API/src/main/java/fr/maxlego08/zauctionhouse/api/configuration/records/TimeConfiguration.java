package fr.maxlego08.zauctionhouse.api.configuration.records;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public record TimeConfiguration(
        String second,
        String seconds,
        String minute,
        String minutes,
        String hour,
        String hours,
        String day,
        String days,
        String timeDay,
        String timeHour,
        String timeMinute,
        String timeSecond
) {

    public static TimeConfiguration of(AuctionPlugin plugin, FileConfiguration configuration) {

        var second = getOrDefault(plugin, configuration, "time.second", "second");
        var seconds = getOrDefault(plugin, configuration, "time.seconds", "seconds");
        var minute = getOrDefault(plugin, configuration, "time.minute", "minute");
        var minutes = getOrDefault(plugin, configuration, "time.minutes", "minutes");
        var hour = getOrDefault(plugin, configuration, "time.hour", "hour");
        var hours = getOrDefault(plugin, configuration, "time.hours", "hours");
        var day = getOrDefault(plugin, configuration, "time.day", "day");
        var days = getOrDefault(plugin, configuration, "time.days", "days");
        var timeDay = getOrDefault(plugin, configuration, "time.time-day", "%02dᴅ %02dʜ %02dᴍ");
        var timeHour = getOrDefault(plugin, configuration, "time.time-hour", "%02dʜ %02dᴍ %02ds");
        var timeMinute = getOrDefault(plugin, configuration, "time.time-minute", "%02dᴍ %02ds");
        var timeSecond = getOrDefault(plugin, configuration, "time.time-second", "%02ds");

        return new TimeConfiguration(
                second, seconds,
                minute, minutes,
                hour, hours,
                day, days,
                timeDay, timeHour, timeMinute, timeSecond
        );
    }

    private static String getOrDefault(
            AuctionPlugin plugin,
            FileConfiguration configuration,
            String key,
            String defaultValue
    ) {
        String value = configuration.getString(key, defaultValue);

        if (value == null || value.isEmpty()) {
            plugin.getLogger().severe("The time key '" + key + "' is null or empty! You need to fix that in config.yml.");
            return defaultValue;
        }

        return value;
    }

    /**
     * @param millis temps en millisecondes
     */
    public String getFormatLongDays(long millis) {
        TimeParts parts = TimeParts.fromMillis(millis);

        String message = applyUnitLabels(this.timeDay, parts);
        message = String.format(message, parts.days(), parts.hours(), parts.minutes(), parts.seconds());

        return format(message);
    }

    /**
     * @param millis temps en millisecondes
     */
    public String getFormatLongHours(long millis) {
        TimeParts parts = TimeParts.fromMillis(millis);

        String message = applyUnitLabels(this.timeHour, parts);
        message = String.format(message, parts.hours(), parts.minutes(), parts.seconds());

        return format(message);
    }

    /**
     * @param millis temps en millisecondes
     */
    public String getFormatLongMinutes(long millis) {
        TimeParts parts = TimeParts.fromMillis(millis);

        String message = applyUnitLabels(this.timeMinute, parts);
        message = String.format(message, parts.minutes(), parts.seconds());

        return format(message);
    }

    /**
     * @param millis temps en millisecondes
     */
    public String getFormatLongSecondes(long millis) {
        TimeParts parts = TimeParts.fromMillis(millis);

        String message = applyUnitLabels(this.timeSecond, parts);
        message = String.format(message, parts.seconds());

        return format(message);
    }

    /**
     * @param milliSeconds temps en secondes
     */
    public String getStringTime(long milliSeconds) {
        if (milliSeconds < 60 * 1000L) {
            return getFormatLongSecondes(milliSeconds);
        } else if (milliSeconds < 3600 * 1000L) {
            return getFormatLongMinutes(milliSeconds);
        } else if (milliSeconds < 86400 * 1000L) {
            return getFormatLongHours(milliSeconds);
        } else {
            return getFormatLongDays(milliSeconds);
        }
    }

    /**
     * Supprime les parties " 00 unit" pour éviter les trucs du style "00 day 03 hours".
     * Reste volontairement simple pour ne pas casser les traductions custom.
     */
    public String format(String message) {
        message = message.replace(" 00 " + this.second, "");
        message = message.replace(" 00 " + this.hour, "");
        message = message.replace(" 00 " + this.day, "");
        message = message.replace(" 00 " + this.minute, "");
        return message.trim();
    }

    // ---------- Helpers privés ----------

    private String applyUnitLabels(String template, TimeParts parts) {
        String message = template;

        message = message.replace("%second%", parts.seconds() <= 1 ? this.second : this.seconds);
        message = message.replace("%minute%", parts.minutes() <= 1 ? this.minute : this.minutes);
        message = message.replace("%hour%", parts.hours() <= 1 ? this.hour : this.hours);
        message = message.replace("%day%", parts.days() <= 1 ? this.day : this.days);

        return message;
    }

    private record TimeParts(long days, long hours, long minutes, long seconds) {

        static TimeParts fromMillis(long millis) {
            long totalSecs = millis / 1000L;

            long days = totalSecs / 86400L;
            long hours = (totalSecs % 86400L) / 3600L;
            long minutes = (totalSecs % 3600L) / 60L;
            long seconds = totalSecs % 60L;

            return new TimeParts(days, hours, minutes, seconds);
        }
    }
}
