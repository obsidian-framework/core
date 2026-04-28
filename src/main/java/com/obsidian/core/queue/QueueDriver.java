package com.obsidian.core.queue;

import java.util.List;
import java.util.Optional;

/**
 * Contract for a queue backend driver.
 */
public interface QueueDriver
{
    /**
     * Pushes a job into the queue with an optional delay.
     *
     * @param queue Queue name (non-null, non-empty)
     * @param job   Job to enqueue (must be serializable)
     * @param delay Delay in seconds before the job becomes available (0 = immediate)
     * @return Unique job identifier
     * @throws QueueException if the push operation fails
     */
    String push(String queue, Job job, int delay);

    /**
     * Atomically leases the next available job from the queue.
     *
     * <p>The returned job is considered "reserved" and will not be visible to other
     * workers until it is acknowledged, released, or the reservation expires.
     *
     * @param queue Queue name
     * @return an Optional containing the leased job, or empty if the queue has no ready jobs
     */
    Optional<QueuedJob> pop(String queue);

    /**
     * Acknowledges successful execution and removes the job from the queue.
     *
     * @param jobId Job identifier returned by {@link #push}
     */
    void acknowledge(String jobId);

    /**
     * Releases a failed job back to the queue so it can be retried after a delay.
     *
     * @param jobId     Job identifier
     * @param delay     Delay in seconds before the job becomes available again
     * @param exception The exception that caused the failure (for logging)
     */
    void release(String jobId, int delay, Throwable exception);

    /**
     * Moves a permanently failed job to the failed jobs store.
     *
     * <p>Called when a job has exhausted all its attempts.
     *
     * @param job       The original job instance
     * @param queue     Queue name
     * @param attempts  Total number of attempts made
     * @param exception The exception that caused the final failure
     */
    void failed(Job job, String queue, int attempts, Throwable exception);

    /**
     * Returns a paginated list of failed jobs.
     *
     * @param limit  Maximum records to return (must be &gt; 0)
     * @param offset Pagination offset (must be &gt;= 0)
     * @return List of failed jobs, never null
     */
    List<FailedJob> getFailedJobs(int limit, int offset);

    /**
     * Requeues a specific failed job for a new attempt.
     *
     * @param failedJobId Identifier from the failed jobs store
     * @return true if the job was successfully requeued
     */
    boolean retryFailedJob(String failedJobId);

    /**
     * Permanently deletes all failed jobs.
     */
    void flushFailedJobs();

    /**
     * Returns the approximate number of jobs waiting in the given queue.
     * Implementations that cannot provide this efficiently may return -1.
     *
     * @param queue Queue name
     * @return job count, or -1 if unsupported
     */
    default long size(String queue) {
        return -1L;
    }
}