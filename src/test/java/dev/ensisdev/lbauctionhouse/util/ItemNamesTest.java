package dev.ensisdev.lbauctionhouse.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemNamesTest {

    @Test
    void humanizeTurnsUnderscoreEnumIntoReadableWords() {
        assertEquals("Enchanting Table", ItemNames.humanize("ENCHANTING_TABLE"));
        assertEquals("Diamond Sword", ItemNames.humanize("DIAMOND_SWORD"));
        assertEquals("Netherite Ingot", ItemNames.humanize("NETHERITE_INGOT"));
    }

    @Test
    void humanizeHandlesEmptyAndSingle() {
        assertEquals("", ItemNames.humanize(""));
        assertEquals("Bedrock", ItemNames.humanize("BEDROCK"));
    }
}