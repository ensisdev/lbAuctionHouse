package fr.maxlego08.zauctionhouse.api.storage.dto;

import java.util.UUID;

/**
 * Data Transfer Object for player data.
 * <p>
 * This DTO stores the mapping between player UUIDs and their usernames.
 *
 * @param unique_id the player's UUID
 * @param name      the player's username
 */
public record PlayerDTO(UUID unique_id, String name) {
}
