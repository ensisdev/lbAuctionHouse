package fr.maxlego08.zauctionhouse.api.configuration.commands;

import java.util.List;

public record SimpleCommandConfiguration(List<String> aliases, List<SimpleArgumentConfiguration> arguments) {
}
