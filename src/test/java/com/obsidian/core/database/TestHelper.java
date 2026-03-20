package com.obsidian.core.database;

import com.obsidian.core.database.orm.query.grammar.GrammarFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test helper — initializes an in-memory SQLite database for tests.
 *
 * Usage:
 *   @BeforeEach void setUp() { TestHelper.setup(); }
 *   @AfterEach  void tearDown() { TestHelper.teardown(); }
 */
public class TestHelper {

    private static final Logger logger = LoggerFactory.getLogger(TestHelper.class);

    /**
     * Initializes in-memory SQLite DB with test tables.
     */
    public static void setup() {
        DB.initSQLite(":memory:", logger);
        GrammarFactory.initialize("sqlite");
        DB.getInstance().connect();

        // Create test tables
        DB.exec("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL,
                age INTEGER DEFAULT 0,
                role TEXT DEFAULT 'user',
                active INTEGER DEFAULT 1,
                settings TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);

        DB.exec("""
            CREATE TABLE IF NOT EXISTS posts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                body TEXT,
                status INTEGER DEFAULT 1,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id)
            )
        """);

        DB.exec("""
            CREATE TABLE IF NOT EXISTS comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                commentable_id INTEGER,
                commentable_type TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (post_id) REFERENCES posts(id)
            )
        """);

        DB.exec("""
            CREATE TABLE IF NOT EXISTS roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
            )
        """);

        DB.exec("""
            CREATE TABLE IF NOT EXISTS role_user (
                user_id INTEGER NOT NULL,
                role_id INTEGER NOT NULL,
                assigned_at TEXT,
                PRIMARY KEY (user_id, role_id),
                FOREIGN KEY (user_id) REFERENCES users(id),
                FOREIGN KEY (role_id) REFERENCES roles(id)
            )
        """);

        DB.exec("""
            CREATE TABLE IF NOT EXISTS profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL UNIQUE,
                bio TEXT,
                avatar TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id)
            )
        """);
    }

    /**
     * Closes and cleans up the database connection.
     */
    public static void teardown() {
        DB.closeConnection();
    }

    /**
     * Inserts seed data for tests that need pre-populated tables.
     */
    public static void seed() {
        DB.exec("INSERT INTO users (name, email, age, role, active) VALUES (?, ?, ?, ?, ?)",
                "Alice", "alice@example.com", 30, "admin", 1);
        DB.exec("INSERT INTO users (name, email, age, role, active) VALUES (?, ?, ?, ?, ?)",
                "Bob", "bob@example.com", 25, "user", 1);
        DB.exec("INSERT INTO users (name, email, age, role, active) VALUES (?, ?, ?, ?, ?)",
                "Charlie", "charlie@example.com", 35, "user", 0);

        DB.exec("INSERT INTO posts (user_id, title, body, status) VALUES (?, ?, ?, ?)",
                1, "First Post", "Hello World", 1);
        DB.exec("INSERT INTO posts (user_id, title, body, status) VALUES (?, ?, ?, ?)",
                1, "Second Post", "More content", 1);
        DB.exec("INSERT INTO posts (user_id, title, body, status) VALUES (?, ?, ?, ?)",
                2, "Bob's Post", "Bob writes", 0);

        DB.exec("INSERT INTO profiles (user_id, bio, avatar) VALUES (?, ?, ?)",
                1, "Admin user", "alice.png");
        DB.exec("INSERT INTO profiles (user_id, bio, avatar) VALUES (?, ?, ?)",
                2, "Regular user", "bob.png");

        DB.exec("INSERT INTO roles (name) VALUES (?)", "ADMIN");
        DB.exec("INSERT INTO roles (name) VALUES (?)", "EDITOR");
        DB.exec("INSERT INTO roles (name) VALUES (?)", "VIEWER");

        DB.exec("INSERT INTO role_user (user_id, role_id) VALUES (?, ?)", 1, 1);
        DB.exec("INSERT INTO role_user (user_id, role_id) VALUES (?, ?)", 1, 2);
        DB.exec("INSERT INTO role_user (user_id, role_id) VALUES (?, ?)", 2, 3);

        DB.exec("INSERT INTO comments (post_id, body) VALUES (?, ?)", 1, "Great post!");
        DB.exec("INSERT INTO comments (post_id, body) VALUES (?, ?)", 1, "Thanks for sharing");
        DB.exec("INSERT INTO comments (post_id, body) VALUES (?, ?)", 2, "Nice");
    }
}
