package dev.ensisdev.lbauctionhouse.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallCapsTest {

    @Test
    void transliteratesTurkishSpecials() {
        // İ → I → ɪ, H → ʜ, A → ᴀ, L → ʟ, E → ᴇ
        assertEquals("ɪʜᴀʟᴇ", SmallCaps.toSmallCaps("İHALE"));
        // İLANLARIM
        assertEquals("ɪʟᴀɴʟᴀʀɪᴍ", SmallCaps.toSmallCaps("İLANLARIM"));
    }

    @Test
    void mapsPlainAscii() {
        assertEquals("ᴀʙᴄ", SmallCaps.toSmallCaps("abc"));
        assertEquals("ᴛᴏᴘʟᴜ", SmallCaps.toSmallCaps("TOPLU"));
    }

    @Test
    void handlesNull() {
        assertEquals("", SmallCaps.toSmallCaps(null));
        assertEquals("", SmallCaps.toSmallCaps(""));
    }
}