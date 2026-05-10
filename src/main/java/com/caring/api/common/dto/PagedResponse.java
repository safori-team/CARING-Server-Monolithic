package com.caring.api.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징된 리스트 응답의 공통 표준 형상.
 *
 * <p>모든 list API는 이 record를 반환한다. {@code page}는 1-based.</p>
 *
 * <pre>
 * {
 *   "items": [...],
 *   "page": 1,
 *   "size": 20,
 *   "totalElements": 47,
 *   "totalPages": 3,
 *   "hasNext": true
 * }
 * </pre>
 */
public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PagedResponse<T> from(Page<T> p) {
        return new PagedResponse<>(
                p.getContent(),
                p.getNumber() + 1,
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages() == 0 ? 1 : p.getTotalPages(),
                p.hasNext()
        );
    }
}
