package com.godlife.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 앱 소셜 로그인의 1회용 코드 (godlife://login?code=...) */
public record AppLoginRequest(@NotBlank String code) {

    @Override
    public String toString() {
        return "AppLoginRequest[code=***]";
    }
}
