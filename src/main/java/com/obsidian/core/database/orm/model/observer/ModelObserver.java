package com.obsidian.core.database.orm.model.observer;

import com.obsidian.core.database.orm.model.Model;

/**
 * Model lifecycle observer.
 * Override only the methods you need.
 *
 * Usage:
 *   public class UserObserver extends ModelObserver<User> {
 *       @Override
 *       public void creating(User user) {
 *           user.set("slug", Slugify.make(user.getString("name")));
 *       }
 *
 *       @Override
 *       public void created(User user) {
 *           // Send welcome email
 *       }
 *
 *       @Override
 *       public void deleting(User user) {
 *           // Clean up related data
 *       }
 *   }
 *
 * Register in model:
 *   @Override
 *   protected ModelObserver<?> observer() {
 *       return new UserObserver();
 *   }
 */
public abstract class ModelObserver<T extends Model> {

    /** Called before insert. Return false to cancel. */
    public boolean creating(T model) { return true; }

    /** Called after insert. */
    public void created(T model) {}

    /** Called before update. Return false to cancel. */
    public boolean updating(T model) { return true; }

    /** Called after update. */
    public void updated(T model) {}

    /** Called before save (insert or update). Return false to cancel. */
    public boolean saving(T model) { return true; }

    /** Called after save (insert or update). */
    public void saved(T model) {}

    /** Called before delete. Return false to cancel. */
    public boolean deleting(T model) { return true; }

    /** Called after delete. */
    public void deleted(T model) {}

    /** Called before restore (soft delete). */
    public boolean restoring(T model) { return true; }

    /** Called after restore (soft delete). */
    public void restored(T model) {}
}
