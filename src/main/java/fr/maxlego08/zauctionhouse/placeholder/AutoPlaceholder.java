package fr.maxlego08.zauctionhouse.placeholder;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AutoPlaceholder {

    private final String startWith;
    private final BiFunction<Player, String, String> biConsumer;
    private final Function<Player, String> consumer;
    private final String description;
    private final List<String> args;

    public AutoPlaceholder(String startWith, BiFunction<Player, String, String> biConsumer, String description, List<String> args) {
        super();
        this.startWith = startWith;
        this.biConsumer = biConsumer;
        this.description = description;
        this.args = args;
        this.consumer = null;
    }

    public AutoPlaceholder(String startWith, Function<Player, String> consumer, String description) {
        this.startWith = startWith;
        this.description = description;
        this.biConsumer = null;
        this.consumer = consumer;
        this.args = new ArrayList<>();
    }

    public List<String> getArgs() {
        return args;
    }

    public String getDescription() {
        return description;
    }

    public String getStartWith() {
        return startWith;
    }

    public BiFunction<Player, String, String> getBiConsumer() {
        return biConsumer;
    }

    public Function<Player, String> getConsumer() {
        return this.consumer;
    }

    public String accept(Player player, String value) {
        if (this.consumer != null) return this.consumer.apply(player);
        if (this.biConsumer != null) return this.biConsumer.apply(player, value);
        return "Error with consumer !";
    }

    public boolean startsWith(String string) {
        return this.consumer != null ? this.startWith.equalsIgnoreCase(string) : string.startsWith(this.startWith);
    }
}
