package com.godlife.backend.challenge.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Spring Page 를 그대로 내보내지 않고 필요한 값만 담는다. page 는 0부터. */
public record PageResponse<T>(List<T> items, int page, int totalPages, long totalElements) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getTotalPages(),
                page.getTotalElements());
    }
}
