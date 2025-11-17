package fr.maxlego08.zauctionhouse.utils.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

@FunctionalInterface
public interface CollectionBiConsumer {

    /**
     * Accepts a {@link CommandSender} and an array of {@link String}s,
     * and returns a list of strings.
     *
     * @param sender the sender of the command.
     * @param args   the arguments of the command.
     * @return a list of strings.
     */
    List<String> accept(CommandSender sender, String[] args);

}