package com.godlife.backend.challenge.dto;

import com.godlife.backend.challenge.CategorySubType;

public record SubTypeResponse(Integer id, String name) {

    /** 세부 종류가 없으면 null */
    public static SubTypeResponse from(CategorySubType subType) {
        return subType == null ? null : new SubTypeResponse(subType.getId(), subType.getName());
    }
}
