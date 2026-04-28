package com.obsidian.core.queue.drivers;

import com.obsidian.core.queue.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory queue driver backed by a {@link DelayQueue}.
 */
public final class InMemoryQueueDriver implements QueueDriver
{
    private static final Logger logger = LoggerFactory.getLogger(InMemoryQueueDriver.class);

    /** One DelayQueue per named queue. */
    private final ConcurrentMap<String, DelayQueue<DelayedJob>> queues = new ConcurrentHashMap<>();

    /** Reserved jobs, keyed by job id. */
    private final ConcurrentMap<String, DelayedJob> reserved = new ConcurrentHashMap<>();

    /** Failed jobs store. */
    private final CopyOnWriteArrayList<FailedJob> failedJobs = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // QueueDriver
    // -------------------------------------------------------------------------

    @Override
    public String push(String queue, Job job, int delay) {
        Objects.requireNonNull(job,   "job must not be null");
        validateQueue(queue);

        String id = UUID.randomUUID().toString();
        long availableAt = System.currentTimeMillis() + (Math.max(0, delay) * 1000L);
        DelayedJob dj = new DelayedJob(id, queue, job, 0, availableAt);
        getQueue(queue).offer(dj);
        logger.debug("Pushed job {} ({}) to in-memory queue '{}' delay={}s",
                id, job.getClass().getSimpleName(), queue, delay);
        return id;
    }

    @Override
    public Optional<QueuedJob> pop(String queue) {
        validateQueue(queue);
        DelayedJob dj = getQueue(queue).poll(); // non-blocking
        if (dj == null) return Optional.empty();

        DelayedJob incremented = dj.withIncrementedAttempts();
        reserved.put(incremented.id, incremented);

        logger.debug("Popped job {} from in-memory queue '{}' (attempt {})",
                incremented.id, queue, incremented.attempts);

        return Optional.of(QueuedJob.builder()
                .id(incremented.id)
                .queue(queue)
                .job(incremented.job)
                .attempts(incremented.attempts)
                .availableAt(Instant.ofEpochMilli(incremented.availableAt))
                .build());
    }

    @Override
    public void acknowledge(String jobId) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        if (reserved.remove(jobId) != null) {
            logger.debug("Acknowledged in-memory job {}", jobId);
        } else {
            logger.warn("acknowledge() called on unknown job: {}", jobId);
        }
    }

    @Override
    public void release(String jobId, int delay, Throwable exception) {
        Objects.requireNonNull(jobId, "jobId must not be null");
        DelayedJob dj = reserved.remove(jobId);
        if (dj == null) {
            logger.warn("release() called on unknown job: {}", jobId);
            return;
        }
        long newAvailableAt = System.currentTimeMillis() + (Math.max(0, delay) * 1000L);
        getQueue(dj.queue).offer(new DelayedJob(dj.id, dj.queue, dj.job, dj.attempts, newAvailableAt));
        logger.debug("Released job {} back to queue '{}' with delay={}s", jobId, dj.queue, delay);
    }

    @Override
    public void failed(Job job, String queue, int attempts, Throwable exception) {
        String id      = UUID.randomUUID().toString();
        String payload = safeSerialize(job);
        String exMsg   = exception != null ? exception.toString() : null;

        failedJobs.add(FailedJob.builder()
                .id(id)
                .queue(queue)
                .jobClass(job.getClass().getName())
                .payload(payload)
                .exception(exMsg)
                .totalAttempts(attempts)
                .failedAt(Instant.now())
                .build());

        logger.warn("In-memory job permanently failed after {} attempt(s): {}",
                attempts, job.getClass().getSimpleName());

        try {
            job.onFailed(exception);
        } catch (Exception e) {
            logger.warn("onFailed() callback threw for job {}", job.getClass().getSimpleName(), e);
        }
    }

    @Override
    public List<FailedJob> getFailedJobs(int limit, int offset) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be > 0");
        if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");

        List<FailedJob> all = new ArrayList<>(failedJobs);
        // Sort newest first
        all.sort(Comparator.comparing(FailedJob::getFailedAt).reversed());

        int fromIndex = Math.min(offset, all.size());
        int toIndex   = Math.min(offset + limit, all.size());
        return Collections.unmodifiableList(all.subList(fromIndex, toIndex));
    }

    @Override
    public boolean retryFailedJob(String failedJobId) {
        Objects.requireNonNull(failedJobId, "failedJobId must not be null");
        for (FailedJob fj : failedJobs) {
            if (fj.getId().equals(failedJobId)) {
                failedJobs.remove(fj);
                try {
                    Job job = JobSerializer.deserialize(fj.getPayload());
                    push(fj.getQueue(), job, 0);
                    logger.info("Re-queued in-memory failed job {} to '{}'", failedJobId, fj.getQueue());
                    return true;
                } catch (Exception e) {
                    logger.error("Failed to re-queue in-memory job {}", failedJobId, e);
                    return false;
                }
            }
        }
        logger.warn("retryFailedJob(): no failed job found with id={}", failedJobId);
        return false;
    }

    @Override
    public void flushFailedJobs() {
        int count = failedJobs.size();
        failedJobs.clear();
        logger.info("Flushed {} in-memory failed job(s)", count);
    }

    @Override
    public long size(String queue) {
        validateQueue(queue);
        return getQueue(queue).size();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DelayQueue<DelayedJob> getQueue(String name) {
        return queues.computeIfAbsent(name, k -> new DelayQueue<>());
    }

    private void validateQueue(String queue) {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("queue must not be null or blank");
        }
    }

    private String safeSerialize(Job job) {
        try {
            return JobSerializer.serialize(job);
        } catch (Exception e) {
            return "{\"@class\":\"" + job.getClass().getName() + "\",\"_error\":\"serialization_failed\"}";
        }
    }

    // -------------------------------------------------------------------------
    // Delayed wrapper
    // -------------------------------------------------------------------------

    private static final class DelayedJob implements Delayed {

        final String id;
        final String queue;
        final Job    job;
        final int    attempts;
        final long   availableAt; // epoch millis

        DelayedJob(String id, String queue, Job job, int attempts, long availableAt) {
            this.id          = id;
            this.queue       = queue;
            this.job         = job;
            this.attempts    = attempts;
            this.availableAt = availableAt;
        }

        DelayedJob withIncrementedAttempts() {
            return new DelayedJob(id, queue, job, attempts + 1, availableAt);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(availableAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}