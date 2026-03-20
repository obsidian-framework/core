package com.obsidian.core.database.orm.model;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Enhanced collection of models with utility methods.
 *
 * Usage:
 *   ModelCollection<User> users = ModelCollection.of(User.all(User.class));
 *
 *   users.pluck("name");                           // List<Object>
 *   users.keyBy("id");                             // Map<Object, User>
 *   users.groupBy("role");                         // Map<Object, List<User>>
 *   users.filter(u -> u.getBoolean("active"));     // ModelCollection<User>
 *   users.sortBy("name");                          // ModelCollection<User>
 *   users.first();                                 // User or null
 *   users.last();                                  // User or null
 *   users.chunk(10);                               // List<ModelCollection<User>>
 *   users.ids();                                   // List<Object>
 *   users.toMapList();                             // List<Map<String, Object>>
 */
public class ModelCollection<T extends Model> implements Iterable<T> {

    private final List<T> items;

    /**
     * Creates a new ModelCollection instance.
     *
     * @param items The items
     */
    public ModelCollection(List<T> items) {
        this.items = new ArrayList<>(items);
    }

    /**
     * Of.
     *
     * @param items The items
     * @return This instance for method chaining
     */
    public static <T extends Model> ModelCollection<T> of(List<T> items) {
        return new ModelCollection<>(items);
    }

    /**
     * Empty.
     *
     * @return This instance for method chaining
     */
    public static <T extends Model> ModelCollection<T> empty() {
        return new ModelCollection<>(Collections.emptyList());
    }

    // ─── ACCESS ──────────────────────────────────────────────

    /**
     * Returns all items in the collection.
     *
     * @return A list of results
     */
    public List<T> all() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Executes the query and returns the results.
     *
     * @param index The item index
     * @return The model instance, or {@code null} if not found
     */
    public T get(int index) {
        return items.get(index);
    }

    /**
     * Executes the query and returns the first result, or null.
     *
     * @return The model instance, or {@code null} if not found
     */
    public T first() {
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * Returns the last N entries from the query log.
     *
     * @return The model instance, or {@code null} if not found
     */
    public T last() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    /**
     * Returns the number of matching rows.
     *
     * @return The number of affected rows
     */
    public int count() {
        return items.size();
    }

    /**
     * Checks if the collection/result is empty.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Checks if the collection/result is not empty.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean isNotEmpty() {
        return !items.isEmpty();
    }

    // ─── EXTRACTION ──────────────────────────────────────────

    /**
     * Extract a single attribute from each model.
     */
    public List<Object> pluck(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .collect(Collectors.toList());
    }

    /**
     * Extract two attributes as key-value pairs.
     */
    public Map<Object, Object> pluck(String valueKey, String keyKey) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(item.get(keyKey), item.get(valueKey));
        }
        return map;
    }

    /**
     * Get all model IDs.
     */
    public List<Object> ids() {
        return pluck("id");
    }

    /**
     * Key the collection by an attribute.
     */
    public Map<Object, T> keyBy(String key) {
        Map<Object, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(item.get(key), item);
        }
        return map;
    }

    /**
     * Group by an attribute.
     */
    public Map<Object, List<T>> groupBy(String key) {
        Map<Object, List<T>> grouped = new LinkedHashMap<>();
        for (T item : items) {
            Object val = item.get(key);
            grouped.computeIfAbsent(val, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    // ─── FILTERING ───────────────────────────────────────────

    /**
     * Filter with a predicate.
     */
    public ModelCollection<T> filter(Predicate<T> predicate) {
        return new ModelCollection<>(items.stream()
                .filter(predicate)
                .collect(Collectors.toList()));
    }

    /**
     * Filter where attribute equals value.
     */
    public ModelCollection<T> where(String key, Object value) {
        return filter(m -> Objects.equals(m.get(key), value));
    }

    /**
     * Filter where attribute is not null.
     */
    public ModelCollection<T> whereNotNull(String key) {
        return filter(m -> m.get(key) != null);
    }

    /**
     * Filter where attribute is in the given list.
     */
    public ModelCollection<T> whereIn(String key, List<?> values) {
        return filter(m -> values.contains(m.get(key)));
    }

    /**
     * Reject items matching predicate (inverse of filter).
     */
    public ModelCollection<T> reject(Predicate<T> predicate) {
        return filter(predicate.negate());
    }

    /**
     * Get unique items by attribute.
     */
    public ModelCollection<T> unique(String key) {
        Set<Object> seen = new LinkedHashSet<>();
        List<T> result = new ArrayList<>();
        for (T item : items) {
            Object val = item.get(key);
            if (seen.add(val)) {
                result.add(item);
            }
        }
        return new ModelCollection<>(result);
    }

    // ─── SORTING ─────────────────────────────────────────────

    /**
     * Sort by attribute (ascending).
     */
    @SuppressWarnings("unchecked")
    public ModelCollection<T> sortBy(String key) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> {
            Comparable<Object> va = (Comparable<Object>) a.get(key);
            Object vb = b.get(key);
            if (va == null && vb == null) return 0;
            if (va == null) return -1;
            if (vb == null) return 1;
            return va.compareTo(vb);
        });
        return new ModelCollection<>(sorted);
    }

    /**
     * Sort by attribute (descending).
     */
    public ModelCollection<T> sortByDesc(String key) {
        List<T> sorted = sortBy(key).items;
        Collections.reverse(sorted);
        return new ModelCollection<>(sorted);
    }

    // ─── TRANSFORMATION ──────────────────────────────────────

    /**
     * Map each model to a value.
     */
    public <R> List<R> map(Function<T, R> mapper) {
        return items.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * FlatMap across model lists.
     */
    public <R> List<R> flatMap(Function<T, List<R>> mapper) {
        return items.stream().flatMap(m -> mapper.apply(m).stream()).collect(Collectors.toList());
    }

    /**
     * Execute action on each model.
     */
    public ModelCollection<T> each(java.util.function.Consumer<T> action) {
        items.forEach(action);
        return this;
    }

    // ─── SLICING ─────────────────────────────────────────────

    /**
     * Take first N items.
     */
    public ModelCollection<T> take(int n) {
        return new ModelCollection<>(items.stream().limit(n).collect(Collectors.toList()));
    }

    /**
     * Skip first N items.
     */
    public ModelCollection<T> skip(int n) {
        return new ModelCollection<>(items.stream().skip(n).collect(Collectors.toList()));
    }

    /**
     * Split into chunks.
     */
    public List<ModelCollection<T>> chunk(int size) {
        List<ModelCollection<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            int end = Math.min(i + size, items.size());
            chunks.add(new ModelCollection<>(items.subList(i, end)));
        }
        return chunks;
    }

    // ─── AGGREGATES ──────────────────────────────────────────

    /**
     * Sum a numeric attribute.
     */
    public double sum(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .mapToDouble(v -> ((Number) v).doubleValue())
                .sum();
    }

    /**
     * Average of a numeric attribute.
     */
    public double avg(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .mapToDouble(v -> ((Number) v).doubleValue())
                .average()
                .orElse(0.0);
    }

    /**
     * Max of a numeric attribute.
     */
    public Object max(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .max((a, b) -> Double.compare(
                        ((Number) a).doubleValue(),
                        ((Number) b).doubleValue()))
                .orElse(null);
    }

    /**
     * Min of a numeric attribute.
     */
    public Object min(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .min((a, b) -> Double.compare(
                        ((Number) a).doubleValue(),
                        ((Number) b).doubleValue()))
                .orElse(null);
    }

    /**
     * Check if any model matches.
     */
    public boolean contains(Predicate<T> predicate) {
        return items.stream().anyMatch(predicate);
    }

    /**
     * Checks if any item matches the given condition.
     *
     * @param key The attribute/column name
     * @param value The value to compare against
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean contains(String key, Object value) {
        return contains(m -> Objects.equals(m.get(key), value));
    }

    // ─── SERIALIZATION ───────────────────────────────────────

    /**
     * Convert all models to maps (respects hidden()).
     */
    public List<Map<String, Object>> toMapList() {
        return items.stream().map(Model::toMap).collect(Collectors.toList());
    }

    // ─── ITERABLE ────────────────────────────────────────────

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    @Override
    public String toString() {
        return "ModelCollection(size=" + items.size() + ")";
    }
}
