package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.Category;

public record CategoryResponse(Integer id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
