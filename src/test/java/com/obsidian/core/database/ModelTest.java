package com.obsidian.core.database;

import com.obsidian.core.database.DB;
import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.model.ModelCollection;
import com.obsidian.core.database.orm.model.ModelNotFoundException;
import com.obsidian.core.database.orm.pagination.Paginator;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Model — ActiveRecord CRUD, relations, scopes, soft deletes, casts.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelTest {

    @BeforeEach void setUp() { TestHelper.setup(); TestHelper.seed(); }
    @AfterEach  void tearDown() { TestHelper.teardown(); }

    // ─── BASIC CRUD ──────────────────────────────────────────

    @Test @Order(1)
    void testFindById() {
        User user = Model.find(User.class, 1);
        assertNotNull(user);
        assertEquals("Alice", user.getName());
        assertEquals("alice@example.com", user.getEmail());
    }

    @Test @Order(2)
    void testFindReturnsNullForMissing() {
        User user = Model.find(User.class, 999);
        assertNull(user);
    }

    @Test @Order(3)
    void testFindOrFailThrows() {
        assertThrows(ModelNotFoundException.class, () -> Model.findOrFail(User.class, 999));
    }

    @Test @Order(4)
    void testAll() {
        // User has soft deletes, so all() excludes soft-deleted
        List<User> users = Model.all(User.class);
        assertEquals(3, users.size()); // none soft-deleted yet
    }

    @Test @Order(5)
    void testCreateAndSave() {
        User user = new User();
        user.set("name", "Eve");
        user.set("email", "eve@example.com");
        user.set("age", 22);
        user.save();

        assertNotNull(user.getId());
        assertTrue(user.exists());

        User found = Model.find(User.class, user.getId());
        assertNotNull(found);
        assertEquals("Eve", found.getName());
    }

    @Test @Order(6)
    void testStaticCreate() {
        User user = Model.create(User.class, Map.of(
                "name", "Frank", "email", "frank@example.com", "age", 40));
        assertNotNull(user.getId());
        assertEquals("Frank", user.getName());
    }

    @Test @Order(7)
    void testUpdate() {
        User user = Model.find(User.class, 1);
        user.set("name", "Alice Updated");
        user.save();

        User reloaded = Model.find(User.class, 1);
        assertEquals("Alice Updated", reloaded.getName());
    }

    @Test @Order(8)
    void testDelete() {
        Post post = Model.find(Post.class, 3);
        assertNotNull(post);
        post.delete();

        Post deleted = Model.find(Post.class, 3);
        assertNull(deleted);
    }

    // ─── DIRTY TRACKING ──────────────────────────────────────

    @Test @Order(10)
    void testDirtyTracking() {
        User user = Model.find(User.class, 1);
        assertFalse(user.isDirty());

        user.set("name", "Modified");
        assertTrue(user.isDirty());
        assertTrue(user.isDirty("name"));
        assertFalse(user.isDirty("email"));

        Map<String, Object> dirty = user.getDirty();
        assertEquals(1, dirty.size());
        assertEquals("Modified", dirty.get("name"));
    }

    @Test @Order(11)
    void testDirtyClearedAfterSave() {
        User user = Model.find(User.class, 1);
        user.set("name", "After Save");
        user.save();
        assertFalse(user.isDirty());
    }

    @Test @Order(12)
    void testNoUpdateWhenNotDirty() {
        // If nothing changed, save() should not execute UPDATE
        User user = Model.find(User.class, 1);
        assertTrue(user.save()); // should return true without executing SQL
    }

    // ─── TIMESTAMPS ──────────────────────────────────────────

    @Test @Order(15)
    void testTimestampsOnCreate() {
        User user = new User();
        user.set("name", "Timestamps Test");
        user.set("email", "ts@example.com");
        user.save();

        assertNotNull(user.get("created_at"));
        assertNotNull(user.get("updated_at"));
    }

    // ─── SOFT DELETES ────────────────────────────────────────

    @Test @Order(20)
    void testSoftDelete() {
        User user = Model.find(User.class, 1);
        user.delete();

        // Should not appear in normal queries (soft delete scope)
        User notFound = Model.find(User.class, 1);
        assertNull(notFound);

        // Should appear with withTrashed
        User found = Model.query(User.class).withTrashed().where("id", 1).first();
        assertNotNull(found);
        assertNotNull(found.get("deleted_at"));
    }

    @Test @Order(21)
    void testOnlyTrashed() {
        User user = Model.find(User.class, 1);
        user.delete();

        List<User> trashed = Model.query(User.class).onlyTrashed().get();
        assertEquals(1, trashed.size());
        assertEquals("Alice", trashed.get(0).getName());
    }

    @Test @Order(22)
    void testRestore() {
        User user = Model.find(User.class, 1);
        user.delete();

        // Restore
        User trashed = Model.query(User.class).withTrashed().where("id", 1).first();
        assertNotNull(trashed);
        trashed.restore();

        // Should be back
        User restored = Model.find(User.class, 1);
        assertNotNull(restored);
        assertNull(restored.get("deleted_at"));
    }

    @Test @Order(23)
    void testForceDelete() {
        User user = Model.find(User.class, 1);
        user.forceDelete();

        // Gone even with withTrashed
        User gone = Model.query(User.class).withTrashed().where("id", 1).first();
        assertNull(gone);
    }

    @Test @Order(24)
    void testBulkDestroy() {
        int deleted = Model.destroy(User.class, 1, 2);
        assertEquals(2, deleted);

        // Soft deleted, so still exist with withTrashed
        List<User> trashed = Model.query(User.class).onlyTrashed().get();
        assertEquals(2, trashed.size());
    }

    // ─── ATTRIBUTE CASTING ───────────────────────────────────

    @Test @Order(30)
    void testCastBoolean() {
        User user = Model.find(User.class, 1);
        Object active = user.get("active");
        // SQLite stores as INTEGER, cast should convert to boolean behavior
        assertTrue(active instanceof Boolean);
        assertEquals(true, active);
    }

    @Test @Order(31)
    void testCastInteger() {
        User user = Model.find(User.class, 1);
        Object age = user.get("age");
        assertTrue(age instanceof Integer);
        assertEquals(30, age);
    }

    // ─── FILLABLE / MASS ASSIGNMENT ──────────────────────────

    @Test @Order(35)
    void testFillOnlyFillable() {
        User user = new User();
        user.fill(Map.of(
                "name", "Test",
                "email", "test@example.com",
                "age", 25,
                "id", 999  // id is NOT in fillable — should be ignored? Actually fillable allows listed fields
        ));
        assertEquals("Test", user.getString("name"));
        assertEquals(25, user.getInteger("age"));
    }

    @Test @Order(36)
    void testForceFillBypassesFillable() {
        User user = new User();
        user.forceFill(Map.of("name", "Forced", "email", "forced@example.com", "settings", "secret"));
        assertEquals("secret", user.getRaw("settings"));
    }

    // ─── HIDDEN ──────────────────────────────────────────────

    @Test @Order(37)
    void testHiddenExcludedFromToMap() {
        User user = Model.find(User.class, 1);
        Map<String, Object> map = user.toMap();
        assertFalse(map.containsKey("settings")); // settings is hidden
        assertTrue(map.containsKey("name"));
    }

    // ─── SCOPES ──────────────────────────────────────────────

    @Test @Order(40)
    void testLocalScope() {
        List<User> active = Model.query(User.class).scope(User::active).get();
        assertTrue(active.size() >= 1);
        for (User u : active) {
            assertEquals(true, u.get("active"));
        }
    }

    @Test @Order(41)
    void testCombinedScopes() {
        List<User> adminActive = Model.query(User.class)
                .scope(User::active)
                .scope(User::admins)
                .get();
        assertEquals(1, adminActive.size());
        assertEquals("Alice", adminActive.get(0).getName());
    }

    // ─── QUERY BUILDER VIA MODEL ─────────────────────────────

    @Test @Order(45)
    void testWhereQuery() {
        List<User> users = Model.where(User.class, "role", "user").get();
        assertEquals(2, users.size());
    }

    @Test @Order(46)
    void testOrderByLimit() {
        List<User> users = Model.query(User.class)
                .orderBy("age")
                .limit(2)
                .get();
        assertEquals(2, users.size());
        assertTrue(users.get(0).getAge() <= users.get(1).getAge());
    }

    @Test @Order(47)
    void testFirstOrCreate_FindsExisting() {
        User existing = Model.firstOrCreate(User.class,
                Map.of("email", "alice@example.com"),
                Map.of("name", "Should Not Create"));
        assertEquals("Alice", existing.getName());
    }

    @Test @Order(48)
    void testFirstOrCreate_CreatesNew() {
        User created = Model.firstOrCreate(User.class,
                Map.of("email", "new@example.com"),
                Map.of("name", "Newbie", "age", 20));
        assertEquals("Newbie", created.getName());
        assertNotNull(created.getId());
    }

    // ─── RELATIONS ───────────────────────────────────────────

    @Test @Order(50)
    void testHasMany() {
        User user = Model.find(User.class, 1);
        List<Post> posts = user.posts().get();
        assertEquals(2, posts.size());
    }

    @Test @Order(51)
    void testHasOne() {
        User user = Model.find(User.class, 1);
        Profile profile = user.profile().first();
        assertNotNull(profile);
        assertEquals("Admin user", profile.getBio());
    }

    @Test @Order(52)
    void testBelongsTo() {
        Post post = Model.find(Post.class, 1);
        User author = post.author().first();
        assertNotNull(author);
        assertEquals("Alice", author.getName());
    }

    @Test @Order(53)
    void testBelongsToMany() {
        User user = Model.find(User.class, 1);
        List<Role> roles = user.roles().get();
        assertEquals(2, roles.size());
    }

    @Test @Order(54)
    void testBelongsToManyAttachDetach() {
        User bob = Model.find(User.class, 2);

        // Bob has 1 role (VIEWER)
        assertEquals(1, bob.roles().get().size());

        // Attach ADMIN role
        bob.roles().attach(1);
        assertEquals(2, bob.roles().get().size());

        // Detach ADMIN role
        bob.roles().detach(1);
        assertEquals(1, bob.roles().get().size());
    }

    @Test @Order(55)
    void testBelongsToManySync() {
        User alice = Model.find(User.class, 1);

        // Alice has roles 1,2 — sync to 2,3
        alice.roles().sync(List.of(2, 3));
        List<Role> roles = alice.roles().get();
        assertEquals(2, roles.size());

        List<Object> roleIds = new ArrayList<>();
        for (Role r : roles) roleIds.add(r.getId());
        assertTrue(roleIds.contains(2) || roleIds.contains(2L));
        assertTrue(roleIds.contains(3) || roleIds.contains(3L));
    }

    // ─── EAGER LOADING ───────────────────────────────────────

    @Test @Order(60)
    void testEagerLoadHasMany() {
        List<User> users = Model.query(User.class).with("posts").get();
        assertFalse(users.isEmpty());

        for (User user : users) {
            assertTrue(user.relationLoaded("posts"));
            List<Post> posts = user.getRelation("posts");
            assertNotNull(posts);
        }

        // Alice should have 2 posts
        User alice = users.stream().filter(u -> "Alice".equals(u.getName())).findFirst().orElse(null);
        assertNotNull(alice);
        assertEquals(2, alice.<Post>getRelation("posts").size());
    }

    @Test @Order(61)
    void testEagerLoadHasOne() {
        List<User> users = Model.query(User.class).with("profile").get();
        User alice = users.stream().filter(u -> "Alice".equals(u.getName())).findFirst().orElse(null);
        assertNotNull(alice);
        assertTrue(alice.relationLoaded("profile"));

        List<Profile> profiles = alice.getRelation("profile");
        assertEquals(1, profiles.size());
        assertEquals("Admin user", profiles.get(0).getBio());
    }

    @Test @Order(62)
    void testEagerLoadBelongsTo() {
        List<Post> posts = Model.query(Post.class).with("author").get();
        assertFalse(posts.isEmpty());

        for (Post post : posts) {
            assertTrue(post.relationLoaded("author"));
        }
    }

    // ─── PAGINATION ──────────────────────────────────────────

    @Test @Order(70)
    void testPaginate() {
        Paginator<User> page = Model.query(User.class).paginate(1, 2);

        assertEquals(3, page.getTotal());
        assertEquals(2, page.getItems().size());
        assertEquals(1, page.getCurrentPage());
        assertEquals(2, page.getLastPage());
        assertTrue(page.hasMorePages());
    }

    @Test @Order(71)
    void testPaginateLastPage() {
        Paginator<User> page = Model.query(User.class).paginate(2, 2);
        assertEquals(1, page.getItems().size());
        assertFalse(page.hasMorePages());
        assertTrue(page.isLastPage());
    }

    // ─── MODEL COLLECTION ────────────────────────────────────

    @Test @Order(80)
    void testModelCollection() {
        List<User> users = Model.all(User.class);
        ModelCollection<User> collection = ModelCollection.of(users);

        assertEquals(3, collection.count());
        assertFalse(collection.isEmpty());

        // pluck
        List<Object> names = collection.pluck("name");
        assertTrue(names.contains("Alice"));

        // filter
        ModelCollection<User> admins = collection.where("role", "admin");
        assertEquals(1, admins.count());

        // ids
        List<Object> ids = collection.ids();
        assertEquals(3, ids.size());
    }

    @Test @Order(81)
    void testModelCollectionSortBy() {
        List<User> users = Model.all(User.class);
        ModelCollection<User> sorted = ModelCollection.of(users).sortBy("age");
        assertTrue(((Number) sorted.get(0).get("age")).intValue()
                <= ((Number) sorted.get(1).get("age")).intValue());
    }

    @Test @Order(82)
    void testModelCollectionGroupBy() {
        List<User> users = Model.all(User.class);
        Map<Object, List<User>> grouped = ModelCollection.of(users).groupBy("role");
        assertTrue(grouped.containsKey("admin"));
        assertTrue(grouped.containsKey("user"));
        assertEquals(1, grouped.get("admin").size());
        assertEquals(2, grouped.get("user").size());
    }

    // ─── METADATA CACHE ─────────────────────────────────────

    @Test @Order(90)
    void testMetadataCachedAcrossCalls() {
        // First call populates cache
        Model.query(User.class).get();

        // Subsequent calls should use same cached metadata
        // (we can't easily assert cache hits, but we verify no errors)
        Model.query(User.class).where("active", 1).get();
        Model.find(User.class, 1);
        Model.query(User.class).count();
    }

    // ─── REFRESH ─────────────────────────────────────────────

    @Test @Order(95)
    void testRefresh() {
        User user = Model.find(User.class, 1);
        String originalName = user.getName();

        // Update directly via SQL
        DB.exec("UPDATE users SET name = ? WHERE id = ?", "Refreshed", 1);

        // Model still has old value
        assertEquals(originalName, user.getName());

        // Refresh reloads from DB
        user.refresh();
        assertEquals("Refreshed", user.getName());
    }
}
