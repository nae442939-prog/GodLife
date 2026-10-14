package com.godlife.backend.demo;

/** 새 회원이 생겼다 (이메일 가입 · 소셜 첫 로그인). 가입 트랜잭션이 끝난 뒤 체험 데이터를 넣는 데 쓴다. */
public record MemberJoinedEvent(Long userId) {
}
