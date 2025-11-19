package fr.maxlego08.zauctionhouse.api.configuration.commands;

import java.util.List;

public record CommandArgumentConfiguration(String name, boolean required, List<String> autoCompletion) {

}