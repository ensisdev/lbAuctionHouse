package fr.maxlego08.zauctionhouse.api.storage.dto;

import java.util.UUID;

public record OptionDTO(UUID player_unique_id, String option_name, String option_value) {
}
