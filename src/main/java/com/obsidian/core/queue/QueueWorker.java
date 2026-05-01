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

    private final AtomicBoolean        running  = new AtomicBoolean(false);
    private ExecutorService            executor;

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

        if (this.queues.isEmpty()) throw new IllegalArgumentException("At least one queue is required");
        if (this.threads < 1)     throw new IllegalArgumentException("threads must be >= 1");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the worker threads. Idempotent — calling start() twice is a no-op.
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
            logger.info("QueueWorker started — queues={}, threads={}, reservationTimeout={}s",
                    queues, threads, reservationTimeout.getSeconds());
        }
    }

    /**
     * Signals all worker threads to stop and waits up to {@code timeout} for them to finish.
     *
     * @param timeout maximum time to wait for graceful shutdown
     */
    public synchronized void stop(Duration timeout) {
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

        while (running.get()) {
            boolean processedAny = false;

            for (String queue : queues) {
                if (!running.get()) break;

                try {
                    Optional<QueuedJob> maybeJob = driver.pop(queue);
                    if (maybeJob.isPresent()) {
                        processJob(maybeJob.get());
                        processedAny = true;
                    }
                } catch (Exception e) {
                    logger.error("Unexpected error popping from queue '{}': {}", queue, e.getMessage(), e);
                }
            }

            // If no queue had work, sleep before polling again to avoid busy-waiting
            if (!processedAny) {
                try {
                    Thread.sleep(idleSleep.toMillis());
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

        } catch (Exception e) {
            logger.warn("Job {} failed (attempt {}/{}): {}",
                    jobId, queuedJob.getAttempts(), job.maxAttempts(), e.getMessage());

            if (queuedJob.isExhausted()) {
                logger.error("Job {} exhausted all attempts — moving to failed store", jobId);
                driver.failed(job, queue, queuedJob.getAttempts(), e);
                // No acknowledge needed: failed() owns the cleanup
            } else {
                driver.release(jobId, job.retryDelay(), e);
            }
        }
    }

    private void sweepExpiredReservations() {
        long now = System.currentTimeMillis();
        if (now - lastExpirySweep < reservationTimeout.toMillis()) return;
        lastExpirySweep = now;

        if (driver instanceof InMemoryQueueDriver) {
            ((InMemoryQueueDriver) driver).requeueExpiredReservations();
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
        private final List<String> queues             = new ArrayList<>();
        private int                threads            = 1;
        private Duration           reservationTimeout = Duration.ofMinutes(5);
        private Duration           idleSleep          = Duration.ofMillis(500);

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

        public QueueWorker build() {
            return new QueueWorker(this);
        }
    }
}