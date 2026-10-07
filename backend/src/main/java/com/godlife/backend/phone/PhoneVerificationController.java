package com.godlife.backend.phone;

import com.godlife.backend.phone.dto.PhoneConfirmRequest;
import com.godlife.backend.phone.dto.PhoneProofResponse;
import com.godlife.backend.phone.dto.PhoneSendRequest;
import com.godlife.backend.phone.dto.SendCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 없이 쓴다. (가입 전, 아이디 찾기) */
@RestController
@RequestMapping("/api/phone-verifications")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService service;

    @PostMapping
    public SendCodeResponse send(@Valid @RequestBody PhoneSendRequest request, HttpServletRequest http) {
        return new SendCodeResponse(service.send(request.phone(), http.getRemoteAddr()));
    }

    @PostMapping("/confirm")
    public PhoneProofResponse confirm(@Valid @RequestBody PhoneConfirmRequest request) {
        return new PhoneProofResponse(service.confirm(request.phone(), request.code()));
    }
}
