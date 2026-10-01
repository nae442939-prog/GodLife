package com.godlife.backend.support;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.support.InquiryService.AdminInquiry;
import com.godlife.backend.support.InquiryService.Inquiry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 고객센터 1:1 문의 (로그인 회원은 본인 문의만, 답변은 관리자) */
@RestController
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    public record InquiryRequest(
            @NotBlank(message = "문의 종류를 골라 주세요.")
            @Pattern(regexp = "ACCOUNT|CHALLENGE|POINT|BUG|ETC", message = "문의 종류를 다시 골라 주세요.")
            String category,
            @NotBlank(message = "제목을 입력해 주세요.")
            @Size(max = 100, message = "제목은 100자까지 쓸 수 있어요.")
            String title,
            @NotBlank(message = "문의 내용을 입력해 주세요.")
            @Size(max = 2000, message = "문의 내용은 2000자까지 쓸 수 있어요.")
            String content) {
    }

    public record AnswerRequest(
            @NotBlank(message = "답변을 입력해 주세요.")
            @Size(max = 2000, message = "답변은 2000자까지 쓸 수 있어요.")
            String answer) {
    }

    @PostMapping("/api/inquiries")
    public ResponseEntity<Map<String, Long>> create(@AuthenticationPrincipal AuthUser authUser,
                                                    @Valid @RequestBody InquiryRequest request) {
        Long id = inquiryService.create(authUser.id(), request.category(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    /** 내 문의 내역 (최근 것부터) */
    @GetMapping("/api/inquiries")
    public List<Inquiry> mine(@AuthenticationPrincipal AuthUser authUser) {
        return inquiryService.mine(authUser.id());
    }

    /** 관리자: 문의 목록 (?status=WAITING 이면 답변 대기만) */
    @GetMapping("/api/admin/inquiries")
    public List<AdminInquiry> all(@RequestParam(required = false) String status) {
        return inquiryService.all(status);
    }

    /** 관리자: 답변 달기 */
    @PostMapping("/api/admin/inquiries/{id}/answer")
    public ResponseEntity<Void> answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest request) {
        inquiryService.answer(id, request.answer());
        return ResponseEntity.noContent().build();
    }
}
