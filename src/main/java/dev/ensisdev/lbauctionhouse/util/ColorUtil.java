package dev.ensisdev.lbauctionhouse.util;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renk dönüşüm yardımcıları: & kodları, &#RRGGBB hex, <gradient:...> degrade. */
public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern GRAD = Pattern.compile("<gradient:#([0-9a-fA-F]{6}):#([0-9a-fA-F]{6})>(.*?)</gradient>");
    private ColorUtil() {}

    public static String colorize(String text) {
        if (text == null) return "";
        text = applyGradients(text);
        text = applyHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String applyGradients(String text) {
        Matcher m = GRAD.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(gradient(m.group(1), m.group(2), m.group(3))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String gradient(String fromHex, String toHex, String text) {
        if (text == null || text.isEmpty()) return "";
        int fr = Integer.parseInt(fromHex.substring(0,2),16), fg = Integer.parseInt(fromHex.substring(2,4),16), fb = Integer.parseInt(fromHex.substring(4,6),16);
        int tr = Integer.parseInt(toHex.substring(0,2),16), tg = Integer.parseInt(toHex.substring(2,4),16), tb = Integer.parseInt(toHex.substring(4,6),16);
        int len = text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            double t = len == 1 ? 0 : (double) i / (len - 1);
            sb.append(ChatColor.of(String.format("#%02X%02X%02X", (int)Math.round(fr+(tr-fr)*t), (int)Math.round(fg+(tg-fg)*t), (int)Math.round(fb+(tb-fb)*t)))).append(text.charAt(i));
        }
        return sb.toString();
    }

    private static String applyHex(String text) {
        Matcher m = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of("#" + m.group(1)).toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Metni small caps'e çevirir (renk kodları ve sıralaması korunur). */
    public static String smallCaps(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '#' && i + 8 < text.length()) {
                    sb.append(text, i, i + 8);
                    i += 7;
                    continue;
                }
                if ("0123456789abcdefklmnor".indexOf(Character.toLowerCase(next)) >= 0) {
                    sb.append(c).append(next);
                    i++;
                    continue;
                }
            }
            sb.append(SmallCaps.toSmallCaps(String.valueOf(c)));
        }
        return sb.toString();
    }
}