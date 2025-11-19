package fr.maxlego08.zauctionhouse.api.configuration.commands;

import java.util.List;

public record CommandConfiguration<T extends Enum<T>>(List<String> aliases, List<CommandArgumentConfiguration<T>> arguments) {


}
