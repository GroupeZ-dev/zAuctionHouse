package fr.maxlego08.zauctionhouse.api.messages;

import fr.maxlego08.zauctionhouse.api.configuration.Loadable;

public record MessageColor(String key, String color) implements Loadable {
}