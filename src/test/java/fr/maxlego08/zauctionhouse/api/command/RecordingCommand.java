package fr.maxlego08.zauctionhouse.api.command;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande concrete de test : enregistre chaque execution de {@link #perform(AuctionPlugin)} et
 * expose publiquement les reglages proteges de {@link VCommand} pour les tests d'autres packages
 * (ZCommandManagerTest).
 * <p>
 * Elle ne contient aucune logique metier : chaque assertion porte sur le comportement du framework.
 */
public class RecordingCommand extends VCommand {

    private CommandType result = CommandType.SUCCESS;
    private RuntimeException failure;

    public int performCount = 0;
    public CommandSender lastSender;
    public Player lastPlayer;
    /**
     * Les arguments vus par la commande au moment de perform, lus via argAsString(0..n) : ils
     * tiennent compte du decalage parentCount.
     */
    public List<String> lastArguments = new ArrayList<>();
    public int lastArgsMinLength;
    public int lastArgsMaxLength;

    public RecordingCommand(AuctionPlugin plugin, String... names) {
        super(plugin);
        if (names.length > 0) this.addSubCommand(names);
    }

    // ------------------------------------------------------------------ reglages publics

    public RecordingCommand result(CommandType result) {
        this.result = result;
        return this;
    }

    public RecordingCommand failing(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public RecordingCommand permission(String permission) {
        this.setPermission(permission);
        return this;
    }

    public RecordingCommand requireArg(String name) {
        this.addRequireArg(name);
        return this;
    }

    public RecordingCommand requireArg(String name, CollectionBiConsumer completion) {
        this.addRequireArg(name, completion);
        return this;
    }

    public RecordingCommand optionalArg(String name) {
        this.addOptionalArg(name);
        return this;
    }

    public RecordingCommand optionalArg(String name, CollectionBiConsumer completion) {
        this.addOptionalArg(name, completion);
        return this;
    }

    public RecordingCommand playersOnly() {
        this.onlyPlayers();
        return this;
    }

    public RecordingCommand async() {
        this.setRunAsync(true);
        return this;
    }

    public RecordingCommand deferredCooldown() {
        this.setDeferredCooldown(true);
        return this;
    }

    public RecordingCommand syntax(String syntax) {
        this.setSyntax(syntax);
        return this;
    }

    public RecordingCommand description(String description) {
        this.setDescription(description);
        return this;
    }

    public RecordingCommand child(RecordingCommand child) {
        this.addSubCommand(child);
        return this;
    }

    // ------------------------------------------------------------------ execution

    @Override
    protected CommandType perform(AuctionPlugin plugin) {
        this.performCount++;
        this.lastSender = this.sender;
        this.lastPlayer = this.player;
        this.lastArgsMinLength = this.getArgsMinLength();
        this.lastArgsMaxLength = this.getArgsMaxLength();
        this.lastArguments = new ArrayList<>();
        for (int index = 0; ; index++) {
            String value = this.argAsString(index);
            if (value == null) break;
            this.lastArguments.add(value);
        }
        if (this.failure != null) throw this.failure;
        return this.result;
    }
}
