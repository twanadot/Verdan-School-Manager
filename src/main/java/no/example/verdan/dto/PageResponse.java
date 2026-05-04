package no.example.verdan.dto;

import java.util.List;

/**
 * Generic paginated response wrapper.
 * Used by list endpoints that support ?page= and ?size= query parameters.
 *
 * @param content     the items on the current page
 * @param page        current page number (0-indexed)
 * @param size        page size
 * @param totalItems  total number of items across all pages
 * @param totalPages  total number of pages
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalItems,
    int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalItems) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalItems / size) : 1;
        return new PageResponse<>(content, page, size, totalItems, totalPages);
    }
}
