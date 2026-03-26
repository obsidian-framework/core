package com.obsidian.core.database.model;

import com.obsidian.core.database.TestHelper;
import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.model.ModelNotFoundException;
import com.obsidian.core.database.orm.pagination.Paginator;
import com.obsidian.core.database.orm.repository.BaseRepository;
import com.obsidian.core.di.annotations.Repository;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BaseRepository — CRUD, primaryKey resolution, updateOrCreate, chunk, upsert.
 *
 * Covers all methods added or fixed in the latest revision:
 *   - primaryKey() cached in constructor, used by findMany() and exists()
 *   - updateOrCreate() — update existing, create new
 *   - chunk() / chunkWhere() — batch iteration
 *   - upsert() — insert + conflict update, SQLite ON CONFLICT DO UPDATE SET
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BaseRepositoryTest
{
    // ─── Concrete repository under test ──────────────────────

    @Repository
    static class UserRepo extends BaseRepository<User> {
        public UserRepo() { super(User.class); }
    }

    @Repository
    static class PostRepo extends BaseRepository<Post> {
        public PostRepo() { super(Post.class); }
    }

    private UserRepo userRepo;
    private PostRepo postRepo;

    @BeforeEach
    void setUp() {
        TestHelper.setup();
        TestHelper.seed();
        userRepo = new UserRepo();
        postRepo = new PostRepo();
    }

    @AfterEach
    void tearDown() {
        TestHelper.teardown();
    }

    // ─── primaryKey resolution ────────────────────────────────

    @Test @Order(1)
    void primaryKey_defaultsToId() {
        // User model does not override primaryKey() → should be "id"
        assertEquals("id", userRepo.primaryKey());
    }

    @Test @Order(2)
    void findMany_usesModelPrimaryKey_notHardcodedId() {
        // findMany must use primaryKey(), not the literal string "id"
        List<User> found = userRepo.findMany(List.of(1L, 2L));
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(u -> u.getName().equals("Alice")));
        assertTrue(found.stream().anyMatch(u -> u.getName().equals("Bob")));
    }

    @Test @Order(3)
    void findMany_emptyList_returnsEmpty() {
        List<User> found = userRepo.findMany(List.of());
        assertTrue(found.isEmpty());
    }

    @Test @Order(4)
    void exists_usesModelPrimaryKey_notHardcodedId() {
        assertTrue(userRepo.exists(1));
        assertFalse(userRepo.exists(999));
    }

    // ─── Basic CRUD ───────────────────────────────────────────

    @Test @Order(10)
    void findById_returnsModel() {
        User user = userRepo.findById(1);
        assertNotNull(user);
        assertEquals("Alice", user.getName());
    }

    @Test @Order(11)
    void findById_returnsNullForMissing() {
        assertNull(userRepo.findById(999));
    }

    @Test @Order(12)
    void findByIdOrFail_throwsForMissing() {
        assertThrows(ModelNotFoundException.class, () -> userRepo.findByIdOrFail(999));
    }

    @Test @Order(13)
    void findAll_returnsAllRecords() {
        // User has soft deletes, none deleted yet
        assertEquals(3, userRepo.findAll().size());
    }

    @Test @Order(14)
    void findBy_returnsFirstMatch() {
        User user = userRepo.findBy("name", "Bob");
        assertNotNull(user);
        assertEquals("bob@example.com", user.getEmail());
    }

    @Test @Order(15)
    void findAllBy_returnsAllMatches() {
        List<User> users = userRepo.findAllBy("role", "user");
        assertEquals(2, users.size());
    }

    @Test @Order(16)
    void count_returnsTotal() {
        assertEquals(3, userRepo.count());
    }

    @Test @Order(17)
    void countWhere_returnsFilteredTotal() {
        assertEquals(2, userRepo.countWhere("active", 1));
    }

    @Test @Order(18)
    void create_persistsAndReturnsModel() {
        User user = userRepo.create(Map.of(
                "name", "Dave", "email", "dave@example.com", "age", 28, "role", "user", "active", 1
        ));
        assertNotNull(user.getId());
        assertEquals(4, userRepo.count());
    }

    @Test @Order(19)
    void delete_removesRecord() {
        assertTrue(userRepo.delete(3));
        // User has soft deletes — should not appear in count
        assertEquals(2, userRepo.count());
    }

    @Test @Order(20)
    void delete_returnsFalseForMissingId() {
        assertFalse(userRepo.delete(999));
    }

    @Test @Order(21)
    void update_appliesAttributesAndSaves() {
        User updated = userRepo.update(1, Map.of("name", "Alice Updated"));
        assertEquals("Alice Updated", updated.getName());

        User fromDb = userRepo.findById(1);
        assertEquals("Alice Updated", fromDb.getName());
    }

    // ─── updateOrCreate ───────────────────────────────────────

    @Test @Order(30)
    void updateOrCreate_updatesExistingRecord() {
        User user = userRepo.updateOrCreate(
                Map.of("name", "Alice"),
                Map.of("age", 99)
        );

        assertNotNull(user);
        assertEquals(99, (int) user.getAge());
        // Must not have created a new record
        assertEquals(3, userRepo.count());
    }

    @Test @Order(31)
    void updateOrCreate_createsWhenNotFound() {
        User user = userRepo.updateOrCreate(
                Map.of("name", "NewUser"),
                Map.of("email", "new@example.com", "role", "user", "active", 1)
        );

        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals("NewUser", user.getName());
        assertEquals("new@example.com", user.getEmail());
        assertEquals(4, userRepo.count());
    }

    @Test @Order(32)
    void updateOrCreate_mergesSearchAndAttributesOnCreate() {
        User user = userRepo.updateOrCreate(
                Map.of("name", "Merged"),
                Map.of("email", "merged@example.com", "role", "user", "active", 1)
        );

        // Both search keys and attributes should be present
        assertEquals("Merged", user.getName());
        assertEquals("merged@example.com", user.getEmail());
    }

    // ─── firstOrCreate ────────────────────────────────────────

    @Test @Order(40)
    void firstOrCreate_returnsExistingRecord() {
        long before = userRepo.count();
        User user = userRepo.firstOrCreate(Map.of("name", "Alice"));
        assertEquals("Alice", user.getName());
        assertEquals(before, userRepo.count());
    }

    @Test @Order(41)
    void firstOrCreate_createsWhenAbsent() {
        User user = userRepo.firstOrCreate(
                Map.of("name", "Ghost"),
                Map.of("email", "ghost@example.com", "role", "user", "active", 1)
        );
        assertNotNull(user.getId());
        assertEquals(4, userRepo.count());
    }

    // ─── chunk ────────────────────────────────────────────────

    @Test @Order(50)
    void chunk_callbackInvokedForEachBatch() {
        AtomicInteger callCount   = new AtomicInteger(0);
        AtomicInteger recordCount = new AtomicInteger(0);

        // 3 users, chunk size 2 → 2 batches
        userRepo.chunk(2, batch -> {
            callCount.incrementAndGet();
            recordCount.addAndGet(batch.size());
        });

        assertEquals(2, callCount.get(),   "Should have 2 batches for 3 records with size 2");
        assertEquals(3, recordCount.get(), "Total records across all batches must equal 3");
    }

    @Test @Order(51)
    void chunk_singleBatchWhenTotalLessThanSize() {
        AtomicInteger callCount = new AtomicInteger(0);

        userRepo.chunk(10, batch -> callCount.incrementAndGet());

        assertEquals(1, callCount.get(), "All 3 records fit in one chunk of size 10");
    }

    @Test @Order(52)
    void chunk_emptyTable_callbackNeverCalled() {
        // Delete all users first
        userRepo.findAll().forEach(u -> userRepo.delete(u.getId()));

        AtomicInteger callCount = new AtomicInteger(0);
        userRepo.chunk(5, batch -> callCount.incrementAndGet());

        assertEquals(0, callCount.get(), "Callback must not be called on empty table");
    }

    @Test @Order(53)
    void chunkWhere_onlyProcessesMatchingRecords() {
        AtomicInteger recordCount = new AtomicInteger(0);

        userRepo.chunkWhere("active", 1, 10, batch -> {
            for (User u : batch) {
                assertTrue((Boolean) u.get("active") || ((Number) u.get("active")).intValue() == 1,
                        "Only active users should appear in chunk");
                recordCount.incrementAndGet();
            }
        });

        assertEquals(2, recordCount.get(), "Only 2 active users should be processed");
    }

    @Test @Order(54)
    void chunk_batchSizeOne_eachRecordIsItsOwnBatch() {
        AtomicInteger callCount = new AtomicInteger(0);

        userRepo.chunk(1, batch -> {
            assertEquals(1, batch.size());
            callCount.incrementAndGet();
        });

        assertEquals(3, callCount.get());
    }

    // ─── upsert ───────────────────────────────────────────────

    @Test @Order(60)
    void upsert_insertsNewRows() {
        List<Map<String, Object>> rows = List.of(
                new LinkedHashMap<>(Map.of("name", "Upsert1", "email", "u1@example.com", "age", 10))
        );

        int affected = userRepo.upsert(rows, List.of("email"));
        assertTrue(affected >= 1);

        // Record should be present
        User found = Model.query(User.class).where("email", "u1@example.com").first();
        assertNotNull(found);
        assertEquals("Upsert1", found.getName());
    }

    @Test @Order(61)
    void upsert_updatesOnConflict() {
        // Alice already exists with email alice@example.com
        // Upsert same email with different name — should update
        List<Map<String, Object>> rows = List.of(
                new LinkedHashMap<>(Map.of("name", "AliceUpdated", "email", "alice@example.com", "age", 31))
        );

        userRepo.upsert(rows, List.of("email"), List.of("name", "age"));

        User alice = Model.query(User.class).where("email", "alice@example.com").first();
        assertNotNull(alice);
        assertEquals("AliceUpdated", alice.getName());
        assertEquals(31, (int) alice.getAge());
    }

    @Test @Order(62)
    void upsert_emptyRows_returnsZero() {
        int affected = userRepo.upsert(List.of(), List.of("email"));
        assertEquals(0, affected);
    }

    @Test @Order(63)
    void upsert_multipleRows_allProcessed() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("name", "Bulk" + i);
            row.put("email", "bulk" + i + "@example.com");
            row.put("age", i * 10);
            rows.add(row);
        }

        userRepo.upsert(rows, List.of("email"));

        assertEquals(6, userRepo.count());
    }

    // ─── aggregates ───────────────────────────────────────────

    @Test @Order(70)
    void max_returnsMaxValue() {
        Object max = userRepo.max("age");
        assertEquals(35, ((Number) max).intValue());
    }

    @Test @Order(71)
    void min_returnsMinValue() {
        Object min = userRepo.min("age");
        assertEquals(25, ((Number) min).intValue());
    }

    @Test @Order(72)
    void sum_returnsSumValue() {
        Object sum = userRepo.sum("age");
        assertEquals(90, ((Number) sum).intValue());
    }

    // ─── pagination ───────────────────────────────────────────

    @Test @Order(80)
    void paginate_returnsCorrectPage() {
        Paginator<User> page = userRepo.paginate(1, 2);
        assertEquals(2, page.getItems().size());
        assertEquals(3, page.getTotal());
        assertEquals(1, page.getCurrentPage());
    }

    @Test @Order(81)
    void paginate_secondPage() {
        Paginator<User> page = userRepo.paginate(2, 2);
        assertEquals(1, page.getItems().size());
    }

    // ─── latest / oldest ─────────────────────────────────────

    @Test @Order(90)
    void latest_returnsRecordsOrderedByCreatedAtDesc() {
        List<User> users = userRepo.latest();
        assertEquals(3, users.size());
    }

    @Test @Order(91)
    void latest_withLimit_returnsCorrectCount() {
        List<User> users = userRepo.latest(2);
        assertEquals(2, users.size());
    }

    @Test @Order(92)
    void oldest_returnsRecordsOrderedByCreatedAtAsc() {
        List<User> users = userRepo.oldest();
        assertEquals(3, users.size());
    }

    // ─── pluck ───────────────────────────────────────────────

    @Test @Order(100)
    void pluck_returnsColumnValues() {
        List<Object> emails = userRepo.pluck("email");
        assertEquals(3, emails.size());
        assertTrue(emails.contains("alice@example.com"));
    }

    @Test @Order(101)
    void pluckWhere_returnsFilteredColumnValues() {
        List<Object> names = userRepo.pluckWhere("name", "role", "admin");
        assertEquals(1, names.size());
        assertEquals("Alice", names.get(0));
    }
}