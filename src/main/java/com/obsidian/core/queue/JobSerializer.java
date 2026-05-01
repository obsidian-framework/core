package com.obsidian.core.queue;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Handles serialization and deserialization of {@link Job} instances to/from JSON.
 */
public final class JobSerializer
{
    private static final Logger logger = LoggerFactory.getLogger(JobSerializer.class);

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    private final Set<String> allowedPackages;
    private final Set<String> allowedClasses;
    private volatile ObjectMapper mapper;

    private JobSerializer(Builder b) {
        this.allowedPackages = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(b.packages));
        this.allowedClasses  = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(b.classes));
        this.mapper          = buildMapper(allowedPackages, allowedClasses);
    }

    /** Serializes a job to JSON. */
    public String serializeJob(Job job) {
        try {
            return mapper.writeValueAsString(job);
        } catch (Exception e) {
            throw new QueueException("Failed to serialize job: " + job.getClass().getName(), e);
        }
    }

    /** Deserializes a job from JSON. */
    public Job deserializeJob(String json) {
        try {
            return mapper.readValue(json, Job.class);
        } catch (Exception e) {
            throw new QueueException("Failed to deserialize job payload: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Instance Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private final Set<String> packages = new java.util.LinkedHashSet<>();
        private final Set<String> classes  = new java.util.LinkedHashSet<>();

        public Builder() {
            packages.add("com.obsidian"); // always allowed
        }

        public Builder allowPackage(String pkg) {
            if (pkg == null || pkg.isBlank()) throw new IllegalArgumentException("Package must not be blank");
            packages.add(pkg);
            return this;
        }

        public Builder allowClass(String className) {
            if (className == null || className.isBlank()) throw new IllegalArgumentException("Class must not be blank");
            classes.add(className);
            return this;
        }

        public JobSerializer build() {
            return new JobSerializer(this);
        }
    }

    // =========================================================================
    // Static API — shared singleton, backwards-compatible
    // =========================================================================

    private static final Set<String> sharedPackages = new CopyOnWriteArraySet<>();
    private static final Set<String> sharedClasses  = new CopyOnWriteArraySet<>();
    private static volatile ObjectMapper sharedMapper;

    static {
        sharedPackages.add("com.obsidian");
    }

    /**
     * Allows all Job classes under a given package prefix (shared instance).
     * Call once at application boot.
     */
    public static void allowPackage(String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isBlank()) {
            throw new IllegalArgumentException("Package prefix must not be blank");
        }
        sharedPackages.add(packagePrefix);
        sharedMapper = null;
        logger.debug("Queue serializer: allowed package '{}'", packagePrefix);
    }

    /** Allows a specific class by fully-qualified name (shared instance). */
    public static void allowClass(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Class name must not be blank");
        }
        sharedClasses.add(className);
        sharedMapper = null;
        logger.debug("Queue serializer: allowed class '{}'", className);
    }

    /** Serializes a job using the shared instance. */
    public static String serialize(Job job) {
        try {
            return getSharedMapper().writeValueAsString(job);
        } catch (Exception e) {
            throw new QueueException("Failed to serialize job: " + job.getClass().getName(), e);
        }
    }

    /** Deserializes a job using the shared instance. */
    public static Job deserialize(String json) {
        try {
            return getSharedMapper().readValue(json, Job.class);
        } catch (Exception e) {
            throw new QueueException("Failed to deserialize job payload: " + e.getMessage(), e);
        }
    }

    private static ObjectMapper getSharedMapper() {
        if (sharedMapper == null) {
            synchronized (JobSerializer.class) {
                if (sharedMapper == null) {
                    sharedMapper = buildMapper(sharedPackages, sharedClasses);
                }
            }
        }
        return sharedMapper;
    }

    // =========================================================================
    // Internal mapper factory (shared by both static and instance APIs)
    // =========================================================================

    private static ObjectMapper buildMapper(Set<String> packages, Set<String> classes) {
        BasicPolymorphicTypeValidator.Builder validatorBuilder =
                BasicPolymorphicTypeValidator.builder();

        for (String pkg : packages) {
            validatorBuilder.allowIfBaseType(pkg);
        }
        for (String cls : classes) {
            try {
                validatorBuilder.allowIfSubType(Class.forName(cls));
            } catch (ClassNotFoundException e) {
                logger.warn("Queue serializer: whitelisted class not found on classpath: {}", cls);
            }
        }

        PolymorphicTypeValidator validator = validatorBuilder.build();

        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.activateDefaultTypingAsProperty(validator, ObjectMapper.DefaultTyping.NON_FINAL, "@class");

        return om;
    }
}