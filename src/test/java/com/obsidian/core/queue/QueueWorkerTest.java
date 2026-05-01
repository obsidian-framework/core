package com.obsidian.core.queue;

import com.obsidian.core.queue.drivers.InMemoryQueueDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class QueueWorkerTest
{
    InMemoryQueueDriver driver;
    QueueWorker         worker;

    @BeforeEach
    void setUp() {
        driver = new InMemoryQueueDriver();
        JobSerializer.allowPackage("com.obsidian.core.queue");
    }

    @AfterEach
    void tearDown() {
        if (worker != null && worker.isRunning()) {
            worker.stop(Duration.ofSeconds(5));
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    static class LatchJob implements Job {
        private static final long serialVersionUID = 1L;
        transient CountDownLatch latch;
        LatchJob() {}
        LatchJob(CountDownLatch latch) { this.latch = latch; }
        @Override public void handle() { if (latch != null) latch.countDown(); }
    }

    static class FailThenSucceedJob implements Job {
        private static final long serialVersionUID = 1L;
        transient AtomicInteger attempts;
        transient CountDownLatch latch;
        FailThenSucceedJob() {}
        FailThenSucceedJob(AtomicInteger attempts, CountDownLatch latch) {
            this.attempts = attempts;
            this.latch    = latch;
        }
        @Override
        public void handle() throws Exception {
            if (attempts != null && attempts.incrementAndGet() < 3) {
                throw new RuntimeException("not yet");
            }
            if (latch != null) latch.countDown();
        }
        @Override public int maxAttempts() { return 5; }
        @Override public int retryDelay()  { return 0; }
    }

    static class AlwaysFailJob implements Job {
        private static final long serialVersionUID = 1L;
        transient CountDownLatch onFailedLatch;
        AlwaysFailJob() {}
        AlwaysFailJob(CountDownLatch latch) { this.onFailedLatch = latch; }
        @Override public void handle() throws Exception { throw new RuntimeException("always"); }
        @Override public int maxAttempts() { return 2; }
        @Override public int retryDelay()  { return 0; }
        @Override public void onFailed(Throwable cause) { if (onFailedLatch != null) onFailedLatch.countDown(); }
    }

    // -------------------------------------------------------------------------
    // Builder validation
    // -------------------------------------------------------------------------

    @Test
    void builder_noQueues_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                QueueWorker.builder(driver).threads(1).build());
    }

    @Test
    void builder_zeroThreads_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                QueueWorker.builder(driver).queues("default").threads(0).build());
    }

    @Test
    void builder_nullDriver_throws() {
        assertThrows(NullPointerException.class, () ->
                QueueWorker.builder(null).queues("default").build());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Test
    void start_isRunning_returnsTrue() {
        worker = QueueWorker.builder(driver).queues("default").build();
        worker.start();
        assertTrue(worker.isRunning());
    }

    @Test
    void stop_isRunning_returnsFalse() {
        worker = QueueWorker.builder(driver).queues("default").build();
        worker.start();
        worker.stop(Duration.ofSeconds(3));
        assertFalse(worker.isRunning());
    }

    @Test
    void start_calledTwice_isIdempotent() {
        worker = QueueWorker.builder(driver).queues("default").build();
        worker.start();
        worker.start(); // should not throw or spawn extra threads
        assertTrue(worker.isRunning());
    }

    // -------------------------------------------------------------------------
    // Job execution
    // -------------------------------------------------------------------------

    @Test
    void worker_consumesAndAcknowledgesJob() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        worker = QueueWorker.builder(driver)
                .queues("default")
                .idleSleep(Duration.ofMillis(50))
                .build();
        worker.start();

        driver.push("default", new LatchJob(latch), 0);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Job was not executed in time");
        // After ack, queue should be empty
        assertEquals(0, driver.size("default"));
    }

    @Test
    void worker_retriesJobUntilSuccess() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger(0);
        CountDownLatch latch   = new CountDownLatch(1);

        worker = QueueWorker.builder(driver)
                .queues("default")
                .idleSleep(Duration.ofMillis(50))
                .build();
        worker.start();

        driver.push("default", new FailThenSucceedJob(attempts, latch), 0);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Job never succeeded");
        assertEquals(3, attempts.get());
    }

    @Test
    void worker_exhaustedJob_movesToFailedStore() throws InterruptedException {
        CountDownLatch onFailedLatch = new CountDownLatch(1);

        worker = QueueWorker.builder(driver)
                .queues("default")
                .idleSleep(Duration.ofMillis(50))
                .build();
        worker.start();

        driver.push("default", new AlwaysFailJob(onFailedLatch), 0);

        assertTrue(onFailedLatch.await(5, TimeUnit.SECONDS), "onFailed() was never called");
        assertEquals(1, driver.getFailedJobs(10, 0).size());
        assertEquals(0, driver.size("default"));
    }

    @Test
    void worker_multipleQueues_consumesAll() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        worker = QueueWorker.builder(driver)
                .queues("q1", "q2")
                .idleSleep(Duration.ofMillis(50))
                .build();
        worker.start();

        driver.push("q1", new LatchJob(latch), 0);
        driver.push("q2", new LatchJob(latch), 0);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Not all jobs consumed");
    }

    @Test
    void worker_multipleThreads_consumesConcurrently() throws InterruptedException {
        int jobCount = 10;
        CountDownLatch latch = new CountDownLatch(jobCount);

        worker = QueueWorker.builder(driver)
                .queues("default")
                .threads(4)
                .idleSleep(Duration.ofMillis(50))
                .build();
        worker.start();

        for (int i = 0; i < jobCount; i++) {
            driver.push("default", new LatchJob(latch), 0);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Not all jobs consumed");
        assertEquals(0, driver.size("default"));
    }

    // -------------------------------------------------------------------------
    // Reservation expiry
    // -------------------------------------------------------------------------

    @Test
    void requeueExpiredReservations_requeuesTimedOutJobs() throws InterruptedException {
        // Driver with 100ms reservation timeout
        driver = new InMemoryQueueDriver(0, Duration.ofMillis(100));
        CountDownLatch latch = new CountDownLatch(1);

        // Push and pop manually to simulate a worker that crashed mid-job
        driver.push("default", new LatchJob(latch), 0);
        driver.pop("default"); // job is now reserved, never acknowledged

        // Wait for expiry
        Thread.sleep(200);

        // Sweep — job should come back to the queue
        int requeued = driver.requeueExpiredReservations();
        assertEquals(1, requeued);
        assertEquals(1, driver.size("default"));
    }

    @Test
    void worker_sweepsExpiredReservationsAutomatically() throws InterruptedException {
        driver = new InMemoryQueueDriver(0, Duration.ofMillis(200));
        CountDownLatch latch = new CountDownLatch(1);

        // Simulate stuck job: push, pop manually, never ack
        driver.push("default", new LatchJob(latch), 0);
        driver.pop("default");
        assertEquals(0, driver.size("default")); // confirmed reserved, not in queue

        // Start a worker with a fast sweep interval
        worker = QueueWorker.builder(driver)
                .queues("default")
                .reservationTimeout(Duration.ofMillis(200))
                .idleSleep(Duration.ofMillis(50))
                .build();
        worker.start();

        // Worker should detect expiry, requeue, then consume the job
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Expired job was never requeued and consumed");
    }
}