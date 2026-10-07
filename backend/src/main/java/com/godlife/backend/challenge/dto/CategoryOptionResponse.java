package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.Category;
import com.godlife.backend.challenge.CategorySubType;

import java.util.List;

/**
 * 개설 · 탐색 화면의 카테고리 고르기.
 *
 * @param subTypes 개설할 때 고를 수 있는 세부 종류 ('기타'만 있고, 나머지는 빈 목록)
 */
public record CategoryOptionResponse(Integer id, String name, List<SubTypeResponse> subTypes) {

    public static CategoryOptionResponse of(Category category, List<CategorySubType> subTypes) {
        return new CategoryOptionResponse(category.getId(), category.getName(), subTypes.stream()
                .filter(s -> s.getCategoryId().equals(category.getId())).map(SubTypeResponse::from).toList());
    }
}
