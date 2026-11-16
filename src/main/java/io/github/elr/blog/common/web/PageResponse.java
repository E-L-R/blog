package io.github.elr.blog.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list, in the format every list endpoint returns:
 * <pre>{"content": [...], "page": {"number": 0, "size": 20, "totalElements": 42, "totalPages": 3}}</pre>
 * Spring Data's {@link Page} would serialize with many more fields that change between
 * versions, so endpoints convert it to this stable shape.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

    /** The largest page a client may ask for. */
    public static final int MAX_SIZE = 50;

    /** The page size when the client doesn't choose one. */
    public static final String DEFAULT_SIZE = "20";

    public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
        var content = page.getContent().stream().<T>map(mapper).toList();
        var metadata = new PageMetadata(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return new PageResponse<>(content, metadata);
    }

    /**
     * @param number        the page's index, from 0
     * @param size          the requested page size
     * @param totalElements the number of items on all pages together
     * @param totalPages    the number of pages
     */
    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }
}
