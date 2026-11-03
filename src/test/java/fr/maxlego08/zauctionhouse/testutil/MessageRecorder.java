package fr.maxlego08.zauctionhouse.testutil;

import fr.maxlego08.zauctionhouse.api.messages.Message;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enregistre les messages que les services envoient via {@code MessageUtils.message(...)}.
 * <p>
 * Le chemin de production passe par PlaceholderAPI et MiniMessage, qui exigent un serveur. Les
 * tests redefinissent {@code message(AuctionPlugin, CommandSender, Message, Object...)} dans une
 * sous-classe du service teste et deleguent ici : le test peut alors verifier QUEL message a ete
 * envoye, a qui, et avec quels arguments de remplacement.
 */
public final class MessageRecorder {

    /**
     * Un envoi : le destinataire, le message et ses arguments ({@code "%cle%", valeur, ...}).
     */
    public record Sent(CommandSender sender, Message message, List<Object> args) {

        /**
         * @param placeholder la cle de remplacement, par exemple {@code "%amount%"}
         * @return la valeur associee (sous forme de chaine), ou {@code null} si absente
         */
        public String arg(String placeholder) {
            for (int index = 0; index + 1 < this.args.size(); index += 2) {
                if (placeholder.equals(this.args.get(index))) {
                    return String.valueOf(this.args.get(index + 1));
                }
            }
            return null;
        }
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    public void record(CommandSender sender, Message message, Object... args) {
        List<Object> arguments = args == null ? List.of() : new ArrayList<>(Arrays.asList(args));
        this.sent.add(new Sent(sender, message, arguments));
    }

    public List<Sent> all() {
        return List.copyOf(this.sent);
    }

    public List<Message> messages() {
        return this.sent.stream().map(Sent::message).toList();
    }

    public boolean has(Message message) {
        return this.sent.stream().anyMatch(entry -> entry.message() == message);
    }

    public long count(Message message) {
        return this.sent.stream().filter(entry -> entry.message() == message).count();
    }

    public Optional<Sent> first(Message message) {
        return this.sent.stream().filter(entry -> entry.message() == message).findFirst();
    }

    public boolean isEmpty() {
        return this.sent.isEmpty();
    }

    public void clear() {
        this.sent.clear();
    }
}
