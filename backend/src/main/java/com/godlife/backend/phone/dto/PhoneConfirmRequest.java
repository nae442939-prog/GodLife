package com.godlife.backend.phone.dto;

import com.godlife.backend.common.validation.InputRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneConfirmRequest(
        @NotBlank(message = "휴대폰 번호를 입력해 주세요.")
        @Pattern(regexp = InputRules.PHONE, message = InputRules.PHONE_MESSAGE)
        String phone,

        @NotBlank(message = InputRules.CODE_MESSAGE)
        @Pattern(regexp = InputRules.CODE, message = InputRules.CODE_MESSAGE)
        String code) {
}
