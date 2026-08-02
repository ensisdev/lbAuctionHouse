package dev.ensisdev.lbauctionhouse.data;

import java.util.UUID;

/**
 * Bir ilana yapılan teklifi temsil eder.
 */
public record AuctionBid(
        long id,
        UUID listingId,
        UUID bidderUUID,
        String bidderName,
        double amount,
        long timestamp
) {}
