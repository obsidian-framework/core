package com.obsidian.core.database.orm.pagination;

import java.util.*;

/**
 * Paginated result set.
 *
 * Usage:
 *   Paginator<User> page = User.query(User.class)
 *       .where("active", 1)
 *       .paginate(1, 15);
 *
 *   page.getItems();       // List<User>
 *   page.getCurrentPage();  // 1
 *   page.getLastPage();     // 5
 *   page.getTotal();        // 72
 *   page.hasMorePages();    // true
 */
public class Paginator<T> {

    private final List<T> items;
    private final long total;
    private final int perPage;
    private final int currentPage;
    private final int lastPage;

    /**
     * Creates a new Paginator instance.
     *
     * @param items The items
     * @param total The total
     * @param perPage Number of items per page
     * @param currentPage The current page
     */
    public Paginator(List<T> items, long total, int perPage, int currentPage) {
        this.items = items;
        this.total = total;
        this.perPage = perPage;
        this.currentPage = currentPage;
        this.lastPage = (int) Math.ceil((double) total / perPage);
    }

    // ─── Accessors ───────────────────────────────────────────

    /**
     * Returns the items.
     *
     * @return The items
     */
    public List<T> getItems()      { return Collections.unmodifiableList(items); }
    /**
     * Returns the total.
     *
     * @return The total
     */
    public long getTotal()         { return total; }
    /**
     * Returns the per page.
     *
     * @return The per page
     */
    public int getPerPage()        { return perPage; }
    /**
     * Returns the current page.
     *
     * @return The current page
     */
    public int getCurrentPage()    { return currentPage; }
    /**
     * Returns the last page.
     *
     * @return The last page
     */
    public int getLastPage()       { return lastPage; }

    /**
     * Has More Pages.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean hasMorePages()  { return currentPage < lastPage; }
    /**
     * Is First Page.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean isFirstPage()   { return currentPage == 1; }
    /**
     * Is Last Page.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean isLastPage()    { return currentPage == lastPage; }
    /**
     * Checks if the collection/result is empty.
     *
     * @return {@code true} if the operation succeeded, {@code false} otherwise
     */
    public boolean isEmpty()       { return items.isEmpty(); }
    /**
     * Returns the number of matching rows.
     *
     * @return The number of affected rows
     */
    public int count()             { return items.size(); }

    /**
     * Returns the from.
     *
     * @return The from
     */
    public int getFrom() {
        if (isEmpty()) return 0;
        return (currentPage - 1) * perPage + 1;
    }

    /**
     * Returns the to.
     *
     * @return The to
     */
    public int getTo() {
        if (isEmpty()) return 0;
        return getFrom() + items.size() - 1;
    }

    /**
     * Previous page number (null if on first page).
     */
    public Integer previousPage() {
        return currentPage > 1 ? currentPage - 1 : null;
    }

    /**
     * Next page number (null if on last page).
     */
    public Integer nextPage() {
        return hasMorePages() ? currentPage + 1 : null;
    }

    /**
     * Generate list of page numbers for UI rendering.
     * Example: [1, 2, 3, null, 8, 9, 10] where null = "..."
     */
    public List<Integer> pageNumbers(int onEachSide) {
        if (lastPage <= (onEachSide * 2 + 3)) {
            // Show all pages
            List<Integer> pages = new ArrayList<>();
            for (int i = 1; i <= lastPage; i++) {
                pages.add(i);
            }
            return pages;
        }

        List<Integer> pages = new ArrayList<>();

        // Always show first page
        pages.add(1);

        int rangeStart = Math.max(2, currentPage - onEachSide);
        int rangeEnd = Math.min(lastPage - 1, currentPage + onEachSide);

        // Ellipsis before range
        if (rangeStart > 2) {
            pages.add(null); // represents "..."
        }

        for (int i = rangeStart; i <= rangeEnd; i++) {
            pages.add(i);
        }

        // Ellipsis after range
        if (rangeEnd < lastPage - 1) {
            pages.add(null); // represents "..."
        }

        // Always show last page
        pages.add(lastPage);

        return pages;
    }

    /**
     * Page Numbers.
     *
     * @return A list of results
     */
    public List<Integer> pageNumbers() {
        return pageNumbers(2);
    }

    /**
     * Convert to map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", items);
        map.put("current_page", currentPage);
        map.put("per_page", perPage);
        map.put("total", total);
        map.put("last_page", lastPage);
        map.put("from", getFrom());
        map.put("to", getTo());
        map.put("has_more_pages", hasMorePages());
        return map;
    }

    @Override
    public String toString() {
        return "Paginator(page=" + currentPage + "/" + lastPage
                + ", total=" + total + ", items=" + items.size() + ")";
    }
}
