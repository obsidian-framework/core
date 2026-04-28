package com.obsidian.core.queue;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a job that has permanently failed after exhausting all retry attempts.
 * Stored in the failed jobs table for inspection and manual retry.
 */
public final class FailedJob
{
    private final String id;
    private final String queue;
    private final String jobClass;
    private final String payload;
    private final String exception;
    private final int totalAttempts;
    private final Instant failedAt;

    private FailedJob(Builder builder)
    {
        this.id            = Objects.requireNonNull(builder.id,       "id must not be null");
        this.queue         = Objects.requireNonNull(builder.queue,    "queue must not be null");
        this.jobClass      = Objects.requireNonNull(builder.jobClass, "jobClass must not be null");
        this.payload       = Objects.requireNonNull(builder.payload,  "payload must not be null");
        this.exception     = builder.exception;
        this.totalAttempts = builder.totalAttempts;
        this.failedAt      = builder.failedAt != null ? builder.failedAt : Instant.now();
    }

    public String getId()           { return id; }
    public String getQueue()        { return queue; }
    public String getJobClass()     { return jobClass; }
    public String getPayload()      { return payload; }
    public String getException()    { return exception; }
    public int getTotalAttempts()   { return totalAttempts; }
    public Instant getFailedAt()    { return failedAt; }

    @Override
    public String toString() {
        return "FailedJob{id='" + id + "', queue='" + queue +
                "', jobClass='" + jobClass + "', failedAt=" + failedAt + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String queue;
        private String jobClass;
        private String payload;
        private String exception;
        private int totalAttempts;
        private Instant failedAt;

        private Builder() {}

        public Builder id(String id)                   { this.id = id; return this; }
        public Builder queue(String queue)             { this.queue = queue; return this; }
        public Builder jobClass(String jobClass)       { this.jobClass = jobClass; return this; }
        public Builder payload(String payload)         { this.payload = payload; return this; }
        public Builder exception(String exception)     { this.exception = exception; return this; }
        public Builder totalAttempts(int n)            { this.totalAttempts = n; return this; }
        public Builder failedAt(Instant failedAt)      { this.failedAt = failedAt; return this; }

        public FailedJob build() { return new FailedJob(this); }
    }
}