package fr.maxlego08.zauctionhouse.api.configuration.commands;

import java.util.List;

public record SimpleArgumentConfiguration(String name, String displayName, boolean required, List<String> autoCompletion) {
}
