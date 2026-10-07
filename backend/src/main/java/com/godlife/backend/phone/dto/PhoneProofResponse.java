package com.godlife.backend.phone.dto;

/** 인증 완료 증표. 15분 안에 가입/아이디 찾기/번호 등록 요청에 한 번 제출한다. */
public record PhoneProofResponse(String phoneProof) {
}
