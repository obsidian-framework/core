package com.obsidian.core.database.orm.model;

import java.lang.annotation.*;

/**
 * Specifies the database table name for a Model.
 *
 * @Table("users")
 * public class User extends Model { }
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {
    String value();
}