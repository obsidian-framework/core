package com.obsidian.core.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.obsidian.core.queue.drivers.InMemoryQueueDriver;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes jobs from one or more queues in a dedicated thread pool.
 */
public final class QueueWorker {

    private static final Logger logger = LoggerFactory.getLogger(QueueWorker.class);

    private final QueueDriver          driver;
    private final List<String>         queues;
    private final int                  threads;
    private final Duration reservationTimeout;
    private final Duration             idleSleep;
    private final boolean              registerShutdownHook;
    private final Duration             shutdownGracePeriod;

    private final AtomicBoolean        running  = new AtomicBoolean(false);
    private ExecutorService            executor;
    private Thread                     shutdownHookThread;

    /** Track last time we swept expired reservations (millis). */
    private volatile long lastExpirySweep = 0;

    // -------------------------------------------------------------------------
    // Constructor (use Builder)
    // -------------------------------------------------------------------------

    private QueueWorker(Builder b) {
        this.driver             = Objects.requireNonNull(b.driver,  "driver must not be null");
        this.queues             = Collections.unmodifiableList(new ArrayList<>(b.queues));
        this.threads            = b.threads;
        this.reservationTimeout = b.reservationTimeout;
        this.idleSleep          = b.idleSleep;
        this.registerShutdownHook = b.registerShutdownHook;
        this.shutdownGracePeriod  = b.shutdownGracePeriod;

        if (this.queues.isEmpty()) throw new IllegalArgumentException("At least one queue is required");
        if (this.threads < 1)     throw new IllegalArgumentException("threads must be >= 1");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the worker threads. Idempotent — calling start() twice is a no-op.
     *
     * <p>If {@code registerShutdownHook(true)} was set on the builder (the default),
     * a JVM shutdown hook is installed that calls {@link #stop(Duration)} with the
     * configured grace period. This means SIGTERM (Docker stop, Kubernetes pod
     * termination, Ctrl-C) will trigger a graceful drain instead of killing in-flight
     * jobs mid-execution.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "queue-worker-" + UUID.randomUUID().toString().substring(0, 8));
                t.setDaemon(true);
                return t;
            });
            for (int i = 0; i < threads; i++) {
                executor.submit(this::workerLoop);
            }

            if (registerShutdownHook) {
                shutdownHookThread = new Thread(() -> {
                    logger.info("JVM shutdown detected — draining QueueWorker (grace={}s)",
                            shutdownGracePeriod.getSeconds());
                    stopInternal(shutdownGracePeriod, false);
                }, "queue-worker-shutdown-hook");
                Runtime.getRuntime().addShutdownHook(shutdownHookThread);
            }

            logger.info("QueueWorker started — queues={}, threads={}, reservationTimeout={}s, shutdownHook={}",
                    queues, threads, reservationTimeout.getSeconds(), registerShutdownHook);
        }
    }

    /**
     * Signals all worker threads to stop and waits up to {@code timeout} for them to finish.
     *
     * @param timeout maximum time to wait for graceful shutdown
     */
    public synchronized void stop(Duration timeout) {
        stopInternal(timeout, true);
    }

    /**
     * Internal stop implementation.
     *
     * @param timeout              graceful timeout
     * @param removeShutdownHook   true when called explicitly by user code; false when
     *                             the JVM shutdown hook itself is calling us (you can't
     *                             remove a hook from inside a running hook).
     */
    private synchronized void stopInternal(Duration timeout, boolean removeShutdownHook) {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    logger.warn("QueueWorker did not terminate within {}ms — forcing shutdown", timeout.toMillis());
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            if (removeShutdownHook && shutdownHookThread != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
                } catch (IllegalStateException ignored) {
                    // JVM already shutting down — hook can't be removed, but that's fine.
                }
                shutdownHookThread = null;
            }

            logger.info("QueueWorker stopped");
        }
    }

    /** Convenience overload with a 30-second graceful shutdown timeout. */
    public void stop() {
        stop(Duration.ofSeconds(30));
    }

    public boolean isRunning() {
        return running.get();
    }

    // -------------------------------------------------------------------------
    // Worker loop
    // -------------------------------------------------------------------------

    private void workerLoop() {
        logger.debug("Worker thread started: {}", Thread.currentThread().getName());

        // Exponential backoff state for repeated pop() failures
        // (e.g. DB unreachable). Resets on any successful pop iteration.
        long currentBackoffMillis = 0L;
        final long maxBackoffMillis = 30_000L;

        while (running.get()) {
            boolean processedAny = false;
            boolean popFailed    = false;

            for (String queue : queues) {
                if (!running.get()) break;

                try {
                    Optional<QueuedJob> maybeJob = driver.pop(queue);
                    if (maybeJob.isPresent()) {
                        processJob(maybeJob.get());
                        processedAny = true;
                    }
                } catch (Throwable t) {
                    // Same VM-error policy as processJob: don't try to recover
                    // from OOM / unrecoverable VM state.
                    if (t instanceof VirtualMachineError) throw (VirtualMachineError) t;

                    popFailed = true;
                    logger.error("Unexpected error popping from queue '{}': {} (backoff={}ms)",
                            queue, t.getMessage(), currentBackoffMillis, t);
                }
            }

            // Update backoff: grow on failure, reset on any clean iteration.
            if (popFailed) {
                currentBackoffMillis = (currentBackoffMillis == 0L)
                        ? idleSleep.toMillis()
                        : Math.min(currentBackoffMillis * 2, maxBackoffMillis);
            } else {
                currentBackoffMillis = 0L;
            }

            // Sleep when idle OR when we hit errors. The backoff sleep replaces
            // the normal idle sleep when an error occurred, so we don't hammer
            // a failing driver.
            long sleepMillis = popFailed
                    ? currentBackoffMillis
                    : (processedAny ? 0L : idleSleep.toMillis());

            if (sleepMillis > 0L) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Periodically requeue expired reservations (once per reservationTimeout interval)
            sweepExpiredReservations();
        }

        logger.debug("Worker thread stopped: {}", Thread.currentThread().getName());
    }

    // -------------------------------------------------------------------------
    // Job execution
    // -------------------------------------------------------------------------

    private void processJob(QueuedJob queuedJob) {
        String jobId  = queuedJob.getId();
        String queue  = queuedJob.getQueue();
        Job    job    = queuedJob.getJob();

        // Guard: reservation timeout — skip if the job was reserved too long ago
        // (can happen if the driver does not enforce expiry itself)
        if (isReservationExpired(queuedJob)) {
            logger.warn("Job {} reservation expired (reserved at {}), releasing back to queue",
                    jobId, queuedJob.getReservedAt());
            driver.release(jobId, 0, null);
            return;
        }

        logger.debug("Processing job {} ({}) attempt {}/{}",
                jobId, job.getClass().getSimpleName(),
                queuedJob.getAttempts(), job.maxAttempts());

        try {
            job.handle();
            driver.acknowledge(jobId);
            logger.debug("Job {} completed successfully", jobId);

        } catch (Throwable t) {
            // Catch Throwable (not just Exception) so that an Error from a buggy
            // job (e.g. StackOverflowError, AssertionError) doesn't silently kill
            // this worker thread. We still re-throw VirtualMachineError (OOM etc.)
            // because the JVM is in an unrecoverable state and trying to release
            // the job would likely fail anyway.
            if (t instanceof VirtualMachineError) {
                logger.error("Fatal VM error in job {} — re-throwing", jobId, t);
                throw (VirtualMachineError) t;
            }

            logger.warn("Job {} failed (attempt {}/{}): {}",
                    jobId, queuedJob.getAttempts(), job.maxAttempts(), t.getMessage());

            // The driver release() path expects a Throwable; we already have one.
            try {
                if (queuedJob.isExhausted()) {
                    logger.error("Job {} exhausted all attempts — moving to failed store", jobId);
                    // failed() takes Throwable in the contract, which Throwable satisfies.
                    driver.failed(job, queue, queuedJob.getAttempts(), t);
                    // No acknowledge needed: failed() owns the cleanup
                } else {
                    driver.release(jobId, job.retryDelay(), t);
                }
            } catch (Exception cleanupErr) {
                // If the driver itself fails (e.g. DB blip), we don't want to lose
                // the worker thread. Log and move on — the lease will eventually
                // expire and be reaped.
                logger.error("Driver cleanup after job {} failure also failed: {}",
                        jobId, cleanupErr.getMessage(), cleanupErr);
            }
        }
    }

    private void sweepExpiredReservations() {
        long now = System.currentTimeMillis();
        if (now - lastExpirySweep < reservationTimeout.toMillis()) return;
        lastExpirySweep = now;

        if (driver instanceof InMemoryQueueDriver) {
            try {
                ((InMemoryQueueDriver) driver).reapExpiredReservations();
            } catch (Exception e) {
                logger.warn("Reservation reaping failed: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isReservationExpired(QueuedJob queuedJob) {
        return queuedJob.getReservedAt()
                .plus(reservationTimeout)
                .isBefore(Instant.now());
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder(QueueDriver driver) {
        return new Builder(driver);
    }

    public static final class Builder {
        private final QueueDriver  driver;
        private final List<String> queues               = new ArrayList<>();
        private int                threads              = 1;
        private Duration           reservationTimeout   = Duration.ofMinutes(5);
        private Duration           idleSleep            = Duration.ofMillis(500);
        private boolean            registerShutdownHook = true;
        private Duration           shutdownGracePeriod  = Duration.ofSeconds(30);

        private Builder(QueueDriver driver) {
            this.driver = driver;
        }

        /** One or more queue names to consume from, in priority order. */
        public Builder queues(String... queues) {
            this.queues.addAll(Arrays.asList(queues));
            return this;
        }

        /** Number of concurrent worker threads (default: 1). */
        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        /**
         * How long a reserved job can stay unacknowledged before being
         * released back to the queue (default: 5 minutes).
         */
        public Builder reservationTimeout(Duration timeout) {
            this.reservationTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        /**
         * How long worker threads sleep when all queues are empty (default: 500ms).
         * Lower = more responsive, higher = less CPU usage.
         */
        public Builder idleSleep(Duration idleSleep) {
            this.idleSleep = Objects.requireNonNull(idleSleep);
            return this;
        }

        /**
         * Whether to register a JVM shutdown hook that drains the worker
         * gracefully on SIGTERM / Ctrl-C / container stop (default: true).
         *
         * <p>Disable only if you manage worker lifecycle from a parent container
         * that already handles shutdown (e.g. an embedded servlet container with
         * its own lifecycle, or unit tests).
         */
        public Builder registerShutdownHook(boolean register) {
            this.registerShutdownHook = register;
            return this;
        }

        /**
         * How long the shutdown hook waits for in-flight jobs to complete before
         * forcing termination (default: 30 seconds).
         *
         * <p>Should be slightly less than your container's grace period (e.g. 30s
         * here for Kubernetes' default 30s {@code terminationGracePeriodSeconds}),
         * leaving room for the JVM itself to shut down cleanly.
         */
        public Builder shutdownGracePeriod(Duration grace) {
            this.shutdownGracePeriod = Objects.requireNonNull(grace);
            return this;
        }

        public QueueWorker build() {
            return new QueueWorker(this);
        }
    }
}