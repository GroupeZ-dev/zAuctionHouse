package fr.maxlego08.zauctionhouse.testutil;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Capture les enregistrements emis par un {@link Logger} JUL pendant la duree d'un test.
 * <p>
 * Permet de verifier qu'un chemin de production a bien journalise (quarantaine en SEVERE,
 * repository non enregistre...) sans dependre de la sortie console. A fermer (try-with-resources)
 * pour detacher le handler : le logger des tests est partage entre toutes les classes.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Handler handler;
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        this.handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        this.handler.setLevel(Level.ALL);
        this.logger.addHandler(this.handler);
    }

    public static LogCapture of(Logger logger) {
        return new LogCapture(logger);
    }

    public List<LogRecord> records() {
        return this.records;
    }

    /**
     * Messages emis a EXACTEMENT ce niveau, dans l'ordre d'emission.
     */
    public List<String> messages(Level level) {
        return this.records.stream().filter(record -> record.getLevel().equals(level)).map(LogRecord::getMessage).toList();
    }

    /**
     * Vrai si au moins un message de ce niveau contient le fragment.
     */
    public boolean contains(Level level, String fragment) {
        return messages(level).stream().anyMatch(message -> message != null && message.contains(fragment));
    }

    public long count(Level level) {
        return messages(level).size();
    }

    @Override
    public void close() {
        this.logger.removeHandler(this.handler);
    }
}
