package dev.ensisdev.lbauctionhouse.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionMathTest {

    @Test
    void calculateNetAppliesTax() {
        assertEquals(95.0, AuctionMath.calculateNet(100.0, 5.0), 1e-9);
        assertEquals(90.0, AuctionMath.calculateNet(100.0, 10.0), 1e-9);
        assertEquals(0.0, AuctionMath.calculateNet(0.0, 5.0), 1e-9);
    }

    @Test
    void clampKeepsValueInRange() {
        assertEquals(50.0, AuctionMath.clampPrice(50.0, 10.0, 100.0), 1e-9);
        assertEquals(10.0, AuctionMath.clampPrice(5.0, 10.0, 100.0), 1e-9);
        assertEquals(100.0, AuctionMath.clampPrice(200.0, 10.0, 100.0), 1e-9);
    }

    @Test
    void withinPriceBoundaries() {
        assertTrue(AuctionMath.withinPrice(50.0, 10.0, 100.0));
        assertTrue(AuctionMath.withinPrice(10.0, 10.0, 100.0));
        assertFalse(AuctionMath.withinPrice(5.0, 10.0, 100.0));
        assertFalse(AuctionMath.withinPrice(200.0, 10.0, 100.0));
    }

    @Test
    void flashSalePriceAppliesDiscount() {
        assertEquals(80.0, AuctionMath.flashSalePrice(100.0, 20), 1e-9);
        assertEquals(100.0, AuctionMath.flashSalePrice(100.0, 0), 1e-9);
        assertEquals(0.0, AuctionMath.flashSalePrice(100.0, 100), 1e-9);
        assertEquals(475.0, AuctionMath.flashSalePrice(500.0, 5), 1e-9);
    }

    @Test
    void listingCostCombinesFees() {
        assertEquals(0.0, AuctionMath.listingCost(0, 0), 1e-9);
        assertEquals(3.0, AuctionMath.listingCost(3, 0), 1e-9);
        assertEquals(500.5, AuctionMath.listingCost(500, 0.5), 1e-9);
    }

    @Test
    void round2RoundsToKurusPrecision() {
        assertEquals(10.01, AuctionMath.round2(10.009), 1e-9);
        assertEquals(95.0, AuctionMath.round2(95.0), 1e-9);
        assertEquals(123.46, AuctionMath.round2(123.456), 1e-9);
    }
}