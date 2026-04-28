package com.obsidian.core.queue.drivers;

import com.obsidian.core.queue.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Synchronous queue driver — executes jobs immediately in the caller's thread.
 */
public final class SyncQueueDriver implements QueueDriver
{
    private static final Logger logger = LoggerFactory.getLogger(SyncQueueDriver.class);

    @Override
    public String push(String queue, Job job, int delay) {
        Objects.requireNonNull(job,   "job must not be null");
        Objects.requireNonNull(queue, "queue must not be null");

        String id = "sync-" + UUID.randomUUID();
        executeWithRetry(id, queue, job);
        return id;
    }

    // -------------------------------------------------------------------------
    // No-op methods — sync driver has no persistent store
    // -------------------------------------------------------------------------

    @Override
    public Optional<QueuedJob> pop(String queue) {
        return Optional.empty();
    }

    @Override
    public void acknowledge(String jobId) { /* no-op */ }

    @Override
    public void release(String jobId, int delay, Throwable exception) { /* no-op */ }

    @Override
    public void failed(Job job, String queue, int attempts, Throwable exception) {
        logger.error("Job permanently failed after {} attempt(s): {}",
                attempts, job.getClass().getSimpleName(), exception);
        try {
            job.onFailed(exception);
        } catch (Exception e) {
            logger.warn("onFailed() callback threw for job {}", job.getClass().getSimpleName(), e);
        }
    }

    @Override
    public List<FailedJob> getFailedJobs(int limit, int offset) {
        return Collections.emptyList();
    }

    @Override
    public boolean retryFailedJob(String failedJobId) {
        return false;
    }

    @Override
    public void flushFailedJobs() { /* no-op */ }

    // -------------------------------------------------------------------------
    // Internal retry loop
    // -------------------------------------------------------------------------

    private void executeWithRetry(String id, String queue, Job job) {
        int maxAttempts = Math.max(1, job.maxAttempts());
        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.debug("Executing job {} (attempt {}/{})", id, attempt, maxAttempts);
                job.handle();
                logger.debug("Job {} completed successfully on attempt {}", id, attempt);
                return; // success
            } catch (Exception e) {
                lastException = e;
                logger.warn("Job {} failed on attempt {}/{}: {}",
                        id, attempt, maxAttempts, e.getMessage());
            }
        }

        // All attempts exhausted
        failed(job, queue, maxAttempts, lastException);
    }
}