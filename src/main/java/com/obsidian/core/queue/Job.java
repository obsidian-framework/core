package com.obsidian.core.queue;

import java.io.Serializable;

/**
 * A unit of work to be processed by the queue.
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