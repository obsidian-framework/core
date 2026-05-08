package com.obsidian.core.queue;

import java.io.Serializable;

/**
 * A unit of work to be processed by the queue.
 *
 * <h2>Idempotence requirement</h2>
 * <p>The queue system provides <strong>at-least-once delivery</strong>. A job
 * may run more than once in the following situations:
 * <ul>
 *   <li>The job throws an exception and is retried (up to {@link #maxAttempts()})</li>
 *   <li>The worker process crashes between {@code handle()} returning and the
 *       driver acknowledging — the job's reservation will eventually expire and
 *       it will be picked up again</li>
 *   <li>A network blip causes the acknowledge to fail after a successful run</li>
 * </ul>
 *
 * <p><strong>Job implementations must be idempotent</strong>: running the same
 * job twice must produce the same observable outcome as running it once.
 * Common patterns:
 * <ul>
 *   <li>Use a unique business key + database constraint to deduplicate</li>
 *   <li>Check whether the work is already done before doing it (e.g. "is this
 *       email already sent for this user+template+date?")</li>
 *   <li>Use external service idempotency keys (Stripe, SendGrid, etc. all
 *       support these — pass a stable key derived from your job inputs)</li>
 * </ul>
 *
 * <h2>Serialization</h2>
 * <p>Jobs are serialized to JSON for transport between push and pop. All fields
 * must be JSON-serializable. Avoid storing references to non-serializable
 * resources (open connections, file handles, lambdas capturing services); pass
 * identifiers instead and look up the resource in {@link #handle()}.
 */
public interface Job extends Serializable
{
    /**
     * Executes the job logic.
     *
     * @throws Exception any exception that causes the job to fail and be retried or moved to failed
     */
    void handle() throws Exception;

    /**
     * Maximum number of attempts before the job is considered permanently failed.
     * Defaults to 3.
     *
     * @return max attempts
     */
    default int maxAttempts() {
        return 3;
    }

    /**
     * Delay in seconds before retrying a failed attempt.
     * Defaults to 60 seconds.
     *
     * @return retry delay in seconds
     */
    default int retryDelay() {
        return 60;
    }

    /**
     * Called when the job has permanently failed (all attempts exhausted).
     * Override to send alerts, clean up state, etc.
     *
     * @param cause the exception that caused the final failure
     */
    default void onFailed(Throwable cause) {
        // no-op by default
    }
}