package com.godlife.backend.common.validation;

/** 여러 요청 DTO 가 같이 쓰는 입력 규칙. 프론트 검증도 같은 규칙을 따른다. */
public final class InputRules {

    /** 휴대폰 번호 (010-1234-5678 / 01012345678). 저장 전에 숫자만 남긴다. */
    public static final String PHONE = "^01[016789]-?\\d{3,4}-?\\d{4}$";
    public static final String PHONE_MESSAGE = "휴대폰 번호 형식이 올바르지 않습니다.";

    public static final String CODE = "^\\d{6}$";
    public static final String CODE_MESSAGE = "인증번호 6자리를 입력해 주세요.";

    /** 영문+숫자 필수, 특수문자 허용, 공백/한글 불가. 길이 8~20자. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$";
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 포함해야 하며, 공백과 한글은 쓸 수 없습니다. (특수문자 가능)";
    public static final String PASSWORD_MIN_MESSAGE = "비밀번호는 8자 이상이어야 합니다.";
    public static final String PASSWORD_MAX_MESSAGE = "비밀번호는 20자 이하여야 합니다.";

    /** 한글(완성형), 영문, 숫자만. 특수문자/공백/낱자(ㅋ, ㅏ) 불가. */
    public static final String NICKNAME = "^[가-힣a-zA-Z0-9]+$";
    public static final String NICKNAME_MESSAGE = "특수문자는 닉네임에 사용할 수 없습니다.";

    private InputRules() {
    }
}
