package com.obsidian.core.database.orm.model.scope;

import com.obsidian.core.database.orm.query.QueryBuilder;

/**
 * Reusable query scope.
 *
 * Usage — as a class:
 *   public class ActiveScope implements Scope {
 *       public void apply(QueryBuilder query) {
 *           query.where("active", 1);
 *       }
 *   }
 *
 * Usage — in model (global scope):
 *   @Override
 *   protected List<Consumer<QueryBuilder>> globalScopes() {
 *       return List.of(new ActiveScope());
 *   }
 *
 * Usage — as local scope (lambda style):
 *   // Define on model:
 *   public static void active(QueryBuilder q) {
 *       q.where("active", 1);
 *   }
 *
 *   public static void published(QueryBuilder q) {
 *       q.where("status", "published");
 *   }
 *
 *   // Use:
 *   User.query(User.class).scope(User::active).scope(User::published).get();
 */
@FunctionalInterface
public interface Scope {

    void apply(QueryBuilder query);
}
