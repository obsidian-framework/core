package com.obsidian.core.queue;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable metadata for a job currently held in the queue.
 * Use {@link QueuedJob.Builder} to construct instances.
 */
public final class QueuedJob
{

    private final String id;
    private final String queue;
    private final Job job;
    private final int attempts;
    private final Instant availableAt;
    private final Instant reservedAt;

    private QueuedJob(Builder builder)
    {
        this.id          = Objects.requireNonNull(builder.id,          "id must not be null");
        this.queue       = Objects.requireNonNull(builder.queue,       "queue must not be null");
        this.job         = Objects.requireNonNull(builder.job,         "job must not be null");
        this.availableAt = Objects.requireNonNull(builder.availableAt, "availableAt must not be null");
        this.attempts    = builder.attempts;
        this.reservedAt  = builder.reservedAt != null ? builder.reservedAt : Instant.now();
    }

    public String getId()           { return id; }
    public String getQueue()        { return queue; }
    public Job getJob()             { return job; }
    public int getAttempts()        { return attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getReservedAt()  { return reservedAt; }

    /** Returns true if this job has exceeded its max attempts. */
    public boolean isExhausted() {
        return attempts >= job.maxAttempts();
    }

    @Override
    public String toString() {
        return "QueuedJob{id='" + id + "', queue='" + queue +
                "', attempts=" + attempts + ", job=" + job.getClass().getSimpleName() + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String queue;
        private Job job;
        private int attempts;
        private Instant availableAt;
        private Instant reservedAt;

        private Builder() {}

        public Builder id(String id)                   { this.id = id; return this; }
        public Builder queue(String queue)             { this.queue = queue; return this; }
        public Builder job(Job job)                    { this.job = job; return this; }
        public Builder attempts(int attempts)          { this.attempts = attempts; return this; }
        public Builder availableAt(Instant availableAt){ this.availableAt = availableAt; return this; }
        public Builder reservedAt(Instant reservedAt)  { this.reservedAt = reservedAt; return this; }

        public QueuedJob build() { return new QueuedJob(this); }
    }
}