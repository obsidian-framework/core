package com.obsidian.core.queue;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Handles serialization and deserialization of {@link Job} instances to/from JSON.
 */
public final class JobSerializer
{
    private static final Logger logger = LoggerFactory.getLogger(JobSerializer.class);

    /** Whitelisted base packages for deserialization. */
    private static final Set<String> allowedPackages = new CopyOnWriteArraySet<>();

    /** Whitelisted individual class names for deserialization. */
    private static final Set<String> allowedClasses = new CopyOnWriteArraySet<>();

    private static volatile ObjectMapper mapper;

    static {
        // Always allow obsidian internal jobs
        allowedPackages.add("com.obsidian");
    }

    private JobSerializer() {}

    // -------------------------------------------------------------------------
    // Whitelist configuration
    // -------------------------------------------------------------------------

    /**
     * Allows all Job classes under a given package prefix to be deserialized.
     * Call this once during application boot (e.g., in your ServiceProvider).
     *
     * @param packagePrefix e.g. "com.myapp.jobs"
     */
    public static void allowPackage(String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isBlank()) {
            throw new IllegalArgumentException("Package prefix must not be blank");
        }
        allowedPackages.add(packagePrefix);
        mapper = null; // invalidate cached mapper so whitelist is rebuilt
        logger.debug("Queue serializer: allowed package '{}'", packagePrefix);
    }

    /**
     * Allows a specific class by fully-qualified name.
     *
     * @param className fully-qualified class name
     */
    public static void allowClass(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Class name must not be blank");
        }
        allowedClasses.add(className);
        mapper = null;
        logger.debug("Queue serializer: allowed class '{}'", className);
    }

    // -------------------------------------------------------------------------
    // Serialize / Deserialize
    // -------------------------------------------------------------------------

    /**
     * Serializes a job to JSON, embedding the concrete class name for deserialization.
     *
     * @param job Job to serialize
     * @return JSON string
     * @throws QueueException if serialization fails
     */
    public static String serialize(Job job) {
        try {
            return getMapper().writeValueAsString(job);
        } catch (Exception e) {
            throw new QueueException("Failed to serialize job: " + job.getClass().getName(), e);
        }
    }

    /**
     * Deserializes a job from JSON.
     *
     * @param json JSON string produced by {@link #serialize}
     * @return Deserialized Job instance
     * @throws QueueException if deserialization fails or the class is not whitelisted
     */
    public static Job deserialize(String json) {
        try {
            return getMapper().readValue(json, Job.class);
        } catch (Exception e) {
            throw new QueueException("Failed to deserialize job payload: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            synchronized (JobSerializer.class) {
                if (mapper == null) {
                    mapper = buildMapper();
                }
            }
        }
        return mapper;
    }

    private static ObjectMapper buildMapper() {
        // Build strict allowlist validator from current whitelist state
        BasicPolymorphicTypeValidator.Builder validatorBuilder =
                BasicPolymorphicTypeValidator.builder();

        for (String pkg : allowedPackages) {
            validatorBuilder.allowIfBaseType(pkg);
        }
        for (String cls : allowedClasses) {
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