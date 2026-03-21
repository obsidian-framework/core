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
public class ModelCollection<T extends Model> implements Iterable<T>
{
    private final List<T> items;

    /**
     * Creates a new collection wrapping the given list of models.
     *
     * @param items list of model instances
     */
    public ModelCollection(List<T> items) {
        this.items = new ArrayList<>(items);
    }

    /**
     * Creates a new collection from the given list of models.
     *
     * @param items list of model instances
     * @return a new {@link ModelCollection}
     */
    public static <T extends Model> ModelCollection<T> of(List<T> items) {
        return new ModelCollection<>(items);
    }

    /**
     * Creates an empty collection.
     *
     * @return an empty {@link ModelCollection}
     */
    public static <T extends Model> ModelCollection<T> empty() {
        return new ModelCollection<>(Collections.emptyList());
    }

    /**
     * Returns all items in the collection as an unmodifiable list.
     *
     * @return unmodifiable list of all model instances
     */
    public List<T> all() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Returns the item at the given index.
     *
     * @param index zero-based index
     * @return model instance at the given position
     */
    public T get(int index) {
        return items.get(index);
    }

    /**
     * Returns the first item, or {@code null} if the collection is empty.
     *
     * @return first model instance, or {@code null}
     */
    public T first() {
        return items.isEmpty() ? null : items.get(0);
    }

    /**
     * Returns the last item, or {@code null} if the collection is empty.
     *
     * @return last model instance, or {@code null}
     */
    public T last() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    /**
     * Returns the number of items in the collection.
     *
     * @return item count
     */
    public int count() {
        return items.size();
    }

    /**
     * Returns {@code true} if the collection contains no items.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Returns {@code true} if the collection contains at least one item.
     *
     * @return {@code true} if not empty
     */
    public boolean isNotEmpty() {
        return !items.isEmpty();
    }

    /**
     * Returns the value of the given attribute from each model.
     *
     * @param key attribute name to extract
     * @return list of attribute values
     */
    public List<Object> pluck(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .collect(Collectors.toList());
    }

    /**
     * Returns a map of {@code keyKey} → {@code valueKey} extracted from each model.
     *
     * @param valueKey attribute to use as map value
     * @param keyKey   attribute to use as map key
     * @return ordered map of extracted key-value pairs
     */
    public Map<Object, Object> pluck(String valueKey, String keyKey) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(item.get(keyKey), item.get(valueKey));
        }
        return map;
    }

    /**
     * Returns the primary key value of each model in the collection.
     *
     * @return list of primary key values
     */
    public List<Object> ids() {
        return pluck("id");
    }

    /**
     * Returns a map keyed by the given attribute, pointing to each model.
     *
     * @param key attribute name to use as key
     * @return ordered map of attribute value → model instance
     */
    public Map<Object, T> keyBy(String key) {
        Map<Object, T> map = new LinkedHashMap<>();
        for (T item : items) {
            map.put(item.get(key), item);
        }
        return map;
    }

    /**
     * Groups models by the value of the given attribute.
     *
     * @param key attribute name to group by
     * @return ordered map of attribute value → list of model instances
     */
    public Map<Object, List<T>> groupBy(String key) {
        Map<Object, List<T>> grouped = new LinkedHashMap<>();
        for (T item : items) {
            Object val = item.get(key);
            grouped.computeIfAbsent(val, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /**
     * Returns a new collection containing only items matching the predicate.
     *
     * @param predicate filter condition
     * @return filtered collection
     */
    public ModelCollection<T> filter(Predicate<T> predicate) {
        return new ModelCollection<>(items.stream()
                .filter(predicate)
                .collect(Collectors.toList()));
    }

    /**
     * Returns a new collection where the given attribute equals the given value.
     *
     * @param key   attribute name
     * @param value value to match
     * @return filtered collection
     */
    public ModelCollection<T> where(String key, Object value) {
        return filter(m -> Objects.equals(m.get(key), value));
    }

    /**
     * Returns a new collection where the given attribute is not {@code null}.
     *
     * @param key attribute name
     * @return filtered collection
     */
    public ModelCollection<T> whereNotNull(String key) {
        return filter(m -> m.get(key) != null);
    }

    /**
     * Returns a new collection where the given attribute is in the provided list.
     *
     * @param key    attribute name
     * @param values allowed values
     * @return filtered collection
     */
    public ModelCollection<T> whereIn(String key, List<?> values) {
        return filter(m -> values.contains(m.get(key)));
    }

    /**
     * Returns a new collection excluding items that match the predicate.
     *
     * @param predicate condition to reject
     * @return filtered collection
     */
    public ModelCollection<T> reject(Predicate<T> predicate) {
        return filter(predicate.negate());
    }

    /**
     * Returns a new collection with duplicate values of the given attribute removed,
     * keeping the first occurrence of each.
     *
     * @param key attribute name to deduplicate on
     * @return deduplicated collection
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

    /**
     * Returns a new collection sorted by the given attribute in ascending order.
     *
     * @param key attribute name to sort by
     * @return sorted collection
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
     * Returns a new collection sorted by the given attribute in descending order.
     *
     * @param key attribute name to sort by
     * @return sorted collection
     */
    public ModelCollection<T> sortByDesc(String key) {
        List<T> sorted = sortBy(key).items;
        Collections.reverse(sorted);
        return new ModelCollection<>(sorted);
    }

    /**
     * Maps each model to a value using the given function.
     *
     * @param mapper mapping function
     * @return list of mapped values
     */
    public <R> List<R> map(Function<T, R> mapper) {
        return items.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * Flat-maps each model to a list of values and returns a flattened result.
     *
     * @param mapper function returning a list per model
     * @return flattened list of values
     */
    public <R> List<R> flatMap(Function<T, List<R>> mapper) {
        return items.stream().flatMap(m -> mapper.apply(m).stream()).collect(Collectors.toList());
    }

    /**
     * Executes the given action on each model and returns this collection.
     *
     * @param action action to perform
     * @return this collection for chaining
     */
    public ModelCollection<T> each(java.util.function.Consumer<T> action) {
        items.forEach(action);
        return this;
    }

    /**
     * Returns a new collection containing only the first {@code n} items.
     *
     * @param n number of items to take
     * @return truncated collection
     */
    public ModelCollection<T> take(int n) {
        return new ModelCollection<>(items.stream().limit(n).collect(Collectors.toList()));
    }

    /**
     * Returns a new collection with the first {@code n} items removed.
     *
     * @param n number of items to skip
     * @return collection without the first {@code n} items
     */
    public ModelCollection<T> skip(int n) {
        return new ModelCollection<>(items.stream().skip(n).collect(Collectors.toList()));
    }

    /**
     * Splits the collection into chunks of the given size.
     *
     * @param size maximum size of each chunk
     * @return list of sub-collections
     */
    public List<ModelCollection<T>> chunk(int size) {
        List<ModelCollection<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            int end = Math.min(i + size, items.size());
            chunks.add(new ModelCollection<>(items.subList(i, end)));
        }
        return chunks;
    }

    /**
     * Returns the sum of the given numeric attribute across all models.
     *
     * @param key numeric attribute name
     * @return sum as a double
     */
    public double sum(String key) {
        return items.stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .mapToDouble(v -> ((Number) v).doubleValue())
                .sum();
    }

    /**
     * Returns the average of the given numeric attribute across all models.
     *
     * @param key numeric attribute name
     * @return average as a double, or {@code 0.0} if the collection is empty
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
     * Returns the maximum value of the given numeric attribute, or {@code null} if empty.
     *
     * @param key numeric attribute name
     * @return maximum value, or {@code null}
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
     * Returns the minimum value of the given numeric attribute, or {@code null} if empty.
     *
     * @param key numeric attribute name
     * @return minimum value, or {@code null}
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
     * Returns {@code true} if any model in the collection matches the predicate.
     *
     * @param predicate condition to test
     * @return {@code true} if at least one match is found
     */
    public boolean contains(Predicate<T> predicate) {
        return items.stream().anyMatch(predicate);
    }

    /**
     * Returns {@code true} if any model has the given attribute equal to the given value.
     *
     * @param key   attribute name
     * @param value value to match
     * @return {@code true} if at least one match is found
     */
    public boolean contains(String key, Object value) {
        return contains(m -> Objects.equals(m.get(key), value));
    }

    /**
     * Returns all models serialized to maps, respecting {@code hidden()} rules.
     *
     * @return list of attribute maps
     */
    public List<Map<String, Object>> toMapList() {
        return items.stream().map(Model::toMap).collect(Collectors.toList());
    }

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    @Override
    public String toString() {
        return "ModelCollection(size=" + items.size() + ")";
    }
}