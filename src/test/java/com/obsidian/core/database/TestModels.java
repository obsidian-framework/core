package com.obsidian.core.database;

import com.obsidian.core.database.orm.model.Model;
import com.obsidian.core.database.orm.model.Table;
import com.obsidian.core.database.orm.model.relation.*;
import com.obsidian.core.database.orm.query.QueryBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// ═══════════════════════════════════════════════════════════
//  USER (soft deletes, casts, fillable, scopes)
// ═══════════════════════════════════════════════════════════

@Table("users")
class User extends Model {

    public String getName()    { return getString("name"); }
    public String getEmail()   { return getString("email"); }
    public Integer getAge()    { return getInteger("age"); }
    public String getRole()    { return getString("role"); }
    public Boolean isActive()  { return getBoolean("active"); }

    // ─── Relations ───
    public HasMany<Post> posts() {
        return hasMany(Post.class, "user_id");
    }

    public HasOne<Profile> profile() {
        return hasOne(Profile.class, "user_id");
    }

    public BelongsToMany<Role> roles() {
        return belongsToMany(Role.class, "role_user", "user_id", "role_id");
    }

    // ─── Scopes ───
    public static void active(QueryBuilder q) {
        q.where("active", 1);
    }

    public static void admins(QueryBuilder q) {
        q.where("role", "admin");
    }

    // ─── Config ───
    @Override protected boolean softDeletes() { return true; }

    @Override protected List<String> fillable() {
        return List.of("name", "email", "age", "role", "active");
    }

    @Override protected List<String> hidden() {
        return List.of("settings");
    }

    @Override protected Map<String, String> casts() {
        return Map.of("active", "boolean", "age", "integer");
    }
}

// ═══════════════════════════════════════════════════════════
//  POST
// ═══════════════════════════════════════════════════════════

@Table("posts")
class Post extends Model {

    public String getTitle()    { return getString("title"); }
    public String getBody()     { return getString("body"); }
    public Integer getStatus()  { return getInteger("status"); }

    public BelongsTo<User> author() {
        return belongsTo(User.class, "user_id");
    }

    public HasMany<Comment> comments() {
        return hasMany(Comment.class, "post_id");
    }

    @Override protected List<String> fillable() {
        return List.of("user_id", "title", "body", "status");
    }
}

// ═══════════════════════════════════════════════════════════
//  PROFILE
// ═══════════════════════════════════════════════════════════

@Table("profiles")
class Profile extends Model {

    public String getBio()    { return getString("bio"); }
    public String getAvatar() { return getString("avatar"); }

    public BelongsTo<User> user() {
        return belongsTo(User.class, "user_id");
    }
}

// ═══════════════════════════════════════════════════════════
//  ROLE
// ═══════════════════════════════════════════════════════════

@Table("roles")
class Role extends Model {

    public String getName() { return getString("name"); }

    public BelongsToMany<User> users() {
        return belongsToMany(User.class, "role_user", "role_id", "user_id");
    }

    @Override protected boolean timestamps() { return false; }
}

// ═══════════════════════════════════════════════════════════
//  COMMENT
// ═══════════════════════════════════════════════════════════

@Table("comments")
class Comment extends Model {

    public String getBody() { return getString("body"); }

    public BelongsTo<Post> post() {
        return belongsTo(Post.class, "post_id");
    }
}
