package dev.ensisdev.lbauctionhouse.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Türkçe metni Unicode SMALL CAPS formas2na cevirir.
 * <p>
 * Minecraft'un yazı tipinde A-Z için küçük büyük harf glifleri vardır
 * (ᴬᴬᴘᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢ). Türkçe özel harfler (ğ, ş, ç, ı, ö, ü, İ)
 * bu glifterde olmadığı için karşılıkları transliterrasyon (Ğ→G, İ→I, Ş→S …)
 * yapılarak ASCII'ye indirgenip small caps'e çevrilir — bu sayede başlıklar
 * bozuk gly iaPromise görmez.
 * <p>
 * Örnek: {@code toSmallCaps("İHALE")} → {@code "ɪʜᴀʟᴇ"}
 */
public final class SmallCaps {

    private static final Map<Character, String> MAP = new HashMap<>();

    static {
        // a–z → small-cap glify
        MAP.put('a', "ᴀ"); MAP.put('b', "ʙ"); MAP.put('c', "ᴄ"); MAP.put('d', "ᴅ");
        MAP.put('e', "ᴇ"); MAP.put('f', "ꜰ"); MAP.put('g', "ɢ"); MAP.put('h', "ʜ");
        MAP.put('i', "ɪ"); MAP.put('j', "ᴊ"); MAP.put('k', "ᴋ"); MAP.put('l', "ʟ");
        MAP.put('m', "ᴍ"); MAP.put('n', "ɴ"); MAP.put('o', "ᴏ"); MAP.put('p', "ᴘ");
        MAP.put('q', "ǫ"); MAP.put('r', "ʀ"); MAP.put('s', "ꜱ"); MAP.put('t', "ᴛ");
        MAP.put('u', "ᴜ"); MAP.put('v', "ᴠ"); MAP.put('w', "ᴡ"); MAP.put('x', "ʀ");
        MAP.put('y', "ʏ"); MAP.put('z', "ᴢ");
    }

    private SmallCaps() {}

    /**
     * Türkçe metni small-caps ifadesine çevirir.
     */
    public static String toSmallCaps(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            sb.append(map(ch));
        }
        return sb.toString();
    }

    /**
     * Tek karakteri küçük-büyük-harf karşılığına çevirir.
     * A-Z ve a-z'yi küçük capital'e, Türkçe özel harfleri ise ASCII
     * karşılıklarına indirir (Ğ→G, İ→I, Ş→S, Ç→C, I→I, Ö→O, Ü→U).
     */
    private static String map(char ch) {
        char c = Character.toUpperCase(ch);
        switch (c) {
            case 'Ğ': c = 'G'; break;
            case 'Ş': c = 'S'; break;
            case 'Ç': c = 'C'; break;
            case 'İ': c = 'I'; break;
            case 'I': c = 'I'; break;
            case 'Ö': c = 'O'; break;
            case 'Ü': c = 'U'; break;
            case 'Â': c = 'A'; break;
            case 'Î': c = 'I'; break;
            case 'Ê': c = 'E'; break;
            case 'Û': c = 'U'; break;
            case 'Ô': c = 'O'; break;
            default: break;
        }
        char lower = Character.toLowerCase(c); // karakter bazlı, locale-etkisiz
        if (lower < 'a' || lower > 'z') return String.valueOf(ch);
        return MAP.getOrDefault(lower, String.valueOf(ch));
    }
}