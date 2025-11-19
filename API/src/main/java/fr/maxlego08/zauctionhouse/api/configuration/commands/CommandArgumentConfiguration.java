package fr.maxlego08.zauctionhouse.api.configuration.commands;

import java.util.List;

public record CommandArgumentConfiguration<T extends Enum<T>>(
        T name,
        String displayName,
        boolean required,
        List<String> autoCompletion
) {}
