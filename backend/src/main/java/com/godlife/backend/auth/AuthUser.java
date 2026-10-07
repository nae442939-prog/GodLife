package com.godlife.backend.auth;

/** 인증된 요청의 주체. JWT 에서 꺼낸 값만 담고 DB 를 다시 조회하지 않는다. */
public record AuthUser(Long id, String role) {
}
