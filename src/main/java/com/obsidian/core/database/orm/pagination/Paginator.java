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
public class Paginator<T>
{
    private final List<T> items;
    private final long total;
    private final int perPage;
    private final int currentPage;
    private final int lastPage;

    /**
     * Creates a new paginator with the given items and pagination metadata.
     *
     * @param items       items for the current page
     * @param total       total number of records across all pages
     * @param perPage     number of items per page
     * @param currentPage current page number, starting at 1
     */
    public Paginator(List<T> items, long total, int perPage, int currentPage)
    {
        this.items = items;
        this.total = total;
        this.perPage = perPage;
        this.currentPage = currentPage;
        this.lastPage = (int) Math.ceil((double) total / perPage);
    }

    /**
     * Returns the items for the current page.
     *
     * @return unmodifiable list of items
     */
    public List<T> getItems()      { return Collections.unmodifiableList(items); }

    /**
     * Returns the total number of records across all pages.
     *
     * @return total record count
     */
    public long getTotal()         { return total; }

    /**
     * Returns the number of items per page.
     *
     * @return items per page
     */
    public int getPerPage()        { return perPage; }

    /**
     * Returns the current page number.
     *
     * @return current page, starting at 1
     */
    public int getCurrentPage()    { return currentPage; }

    /**
     * Returns the last page number.
     *
     * @return last page number
     */
    public int getLastPage()       { return lastPage; }

    /**
     * Returns {@code true} if there are pages beyond the current one.
     *
     * @return {@code true} if more pages exist
     */
    public boolean hasMorePages()  { return currentPage < lastPage; }

    /**
     * Returns {@code true} if the current page is the first page.
     *
     * @return {@code true} if on page 1
     */
    public boolean isFirstPage()   { return currentPage == 1; }

    /**
     * Returns {@code true} if the current page is the last page.
     *
     * @return {@code true} if on the last page
     */
    public boolean isLastPage()    { return currentPage == lastPage; }

    /**
     * Returns {@code true} if the current page has no items.
     *
     * @return {@code true} if the item list is empty
     */
    public boolean isEmpty()       { return items.isEmpty(); }

    /**
     * Returns the number of items on the current page.
     *
     * @return item count
     */
    public int count()             { return items.size(); }

    /**
     * Returns the 1-based index of the first item on the current page, or {@code 0} if empty.
     *
     * @return index of the first item
     */
    public int getFrom()
    {
        if (isEmpty()) return 0;
        return (currentPage - 1) * perPage + 1;
    }

    /**
     * Returns the 1-based index of the last item on the current page, or {@code 0} if empty.
     *
     * @return index of the last item
     */
    public int getTo()
    {
        if (isEmpty()) return 0;
        return getFrom() + items.size() - 1;
    }

    /**
     * Returns the previous page number, or {@code null} if on the first page.
     *
     * @return previous page number, or {@code null}
     */
    public Integer previousPage() {
        return currentPage > 1 ? currentPage - 1 : null;
    }

    /**
     * Returns the next page number, or {@code null} if on the last page.
     *
     * @return next page number, or {@code null}
     */
    public Integer nextPage() {
        return hasMorePages() ? currentPage + 1 : null;
    }

    /**
     * Returns a list of page numbers for UI rendering, with {@code null} representing ellipsis gaps.
     * Example: {@code [1, 2, 3, null, 8, 9, 10]}.
     *
     * @param onEachSide number of page numbers to show on each side of the current page
     * @return list of page numbers with {@code null} for gaps
     */
    public List<Integer> pageNumbers(int onEachSide)
    {
        if (lastPage <= (onEachSide * 2 + 3)) {
            List<Integer> pages = new ArrayList<>();
            for (int i = 1; i <= lastPage; i++) {
                pages.add(i);
            }
            return pages;
        }

        List<Integer> pages = new ArrayList<>();

        pages.add(1);

        int rangeStart = Math.max(2, currentPage - onEachSide);
        int rangeEnd = Math.min(lastPage - 1, currentPage + onEachSide);

        if (rangeStart > 2) {
            pages.add(null);
        }

        for (int i = rangeStart; i <= rangeEnd; i++) {
            pages.add(i);
        }

        if (rangeEnd < lastPage - 1) {
            pages.add(null);
        }

        pages.add(lastPage);

        return pages;
    }

    /**
     * Returns page numbers using a default window of 2 on each side.
     *
     * @return list of page numbers with {@code null} for gaps
     */
    public List<Integer> pageNumbers() {
        return pageNumbers(2);
    }

    /**
     * Serializes this paginator to a map suitable for JSON responses.
     *
     * @return ordered map of pagination metadata and items
     */
    public Map<String, Object> toMap()
    {
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
        return "Paginator(page=" + currentPage + "/" + lastPage + ", total=" + total + ", items=" + items.size() + ")";
    }
}