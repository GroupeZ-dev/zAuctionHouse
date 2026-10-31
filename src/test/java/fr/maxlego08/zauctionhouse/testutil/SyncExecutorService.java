package fr.maxlego08.zauctionhouse.testutil;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executor synchrone : chaque tache s'execute immediatement sur le thread appelant.
 * <p>
 * Rend deterministes les chemins qui passent par {@code plugin.getExecutorService()} ou par
 * {@code CompletableFuture.runAsync(..., executor)} : le future est deja complete au retour.
 */
public final class SyncExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public void shutdown() {
        this.shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        this.shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return this.shutdown;
    }

    @Override
    public boolean isTerminated() {
        return this.shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
