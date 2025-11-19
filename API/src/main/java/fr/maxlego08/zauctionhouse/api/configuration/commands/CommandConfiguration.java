package fr.maxlego08.zauctionhouse.api.configuration.commands;

import java.util.List;

public record CommandConfiguration(List<String> aliases, List<CommandArgumentConfiguration> arguments) {


}
