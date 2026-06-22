package com.postwerk.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Slices an already-materialized, already-sorted list into a {@link Page} for the in-memory
 * filter/sort/paginate admin endpoints (low-volume tables). Replaces the identical private
 * {@code paginate(...)} helper that was copy-pasted across the admin service implementations.
 *
 * @since 1.0
 */
public final class InMemoryPage {

    private InMemoryPage() {}

    /** Returns the {@code pageable} slice of {@code rows} as a {@link Page} (empty slice past the end). */
    public static <T> Page<T> of(List<T> rows, Pageable pageable) {
        int total = rows.size();
        int start = (int) pageable.getOffset();
        if (start >= total) return new PageImpl<>(List.of(), pageable, total);
        int end = Math.min(start + pageable.getPageSize(), total);
        return new PageImpl<>(rows.subList(start, end), pageable, total);
    }
}
