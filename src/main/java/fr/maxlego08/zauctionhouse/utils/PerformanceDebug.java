package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Utility class for measuring and logging performance of heavy operations.
 * Only logs when performance debug is enabled in the configuration.
 */
public class PerformanceDebug {

    private final AuctionPlugin plugin;
    private final Logger logger;

    public PerformanceDebug(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Checks if performance debugging is enabled.
     *
     * @return true if performance debug is enabled
     */
    public boolean isEnabled() {
        return plugin.getConfiguration().isEnablePerformanceDebug();
    }

    /**
     * Measures and logs the execution time of a runnable operation.
     *
     * @param operationName name of the operation being measured
     * @param runnable      the operation to measure
     */
    public void measure(String operationName, Runnable runnable) {
        if (!isEnabled()) {
            runnable.run();
            return;
        }

        long startTime = System.nanoTime();
        runnable.run();
        long endTime = System.nanoTime();

        logPerformance(operationName, startTime, endTime, null);
    }

    /**
     * Measures and logs the execution time of a supplier operation.
     *
     * @param operationName name of the operation being measured
     * @param supplier      the operation to measure
     * @param <T>           the return type
     * @return the result of the supplier
     */
    public <T> T measure(String operationName, Supplier<T> supplier) {
        if (!isEnabled()) {
            return supplier.get();
        }

        long startTime = System.nanoTime();
        T result = supplier.get();
        long endTime = System.nanoTime();

        logPerformance(operationName, startTime, endTime, null);
        return result;
    }

    /**
     * Measures and logs the execution time of a supplier operation with additional context.
     *
     * @param operationName name of the operation being measured
     * @param supplier      the operation to measure
     * @param contextSupplier supplier for additional context (called after operation completes)
     * @param <T>           the return type
     * @return the result of the supplier
     */
    public <T> T measureWithContext(String operationName, Supplier<T> supplier, Supplier<String> contextSupplier) {
        if (!isEnabled()) {
            return supplier.get();
        }

        long startTime = System.nanoTime();
        T result = supplier.get();
        long endTime = System.nanoTime();

        logPerformance(operationName, startTime, endTime, contextSupplier.get());
        return result;
    }

    /**
     * Starts a performance measurement timer.
     *
     * @return the start time in nanoseconds, or -1 if disabled
     */
    public long start() {
        return isEnabled() ? System.nanoTime() : -1;
    }

    /**
     * Ends a performance measurement and logs the result.
     *
     * @param operationName name of the operation
     * @param startTime     the start time from {@link #start()}
     */
    public void end(String operationName, long startTime) {
        if (startTime < 0) return;

        long endTime = System.nanoTime();
        logPerformance(operationName, startTime, endTime, null);
    }

    /**
     * Ends a performance measurement and logs the result with context.
     *
     * @param operationName name of the operation
     * @param startTime     the start time from {@link #start()}
     * @param context       additional context information
     */
    public void end(String operationName, long startTime, String context) {
        if (startTime < 0) return;

        long endTime = System.nanoTime();
        logPerformance(operationName, startTime, endTime, context);
    }

    private void logPerformance(String operationName, long startTime, long endTime, String context) {
        double durationMs = (endTime - startTime) / 1_000_000.0;

        StringBuilder message = new StringBuilder();
        message.append("[Performance] ").append(operationName);
        message.append(" took ").append(String.format("%.3f", durationMs)).append("ms");

        if (context != null && !context.isEmpty()) {
            message.append(" (").append(context).append(")");
        }

        // Use warning level for slow operations (> 100ms)
        if (durationMs > 100) {
            logger.warning(message.toString());
        } else {
            logger.info(message.toString());
        }
    }
}
