package com.godlife.backend.verification;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.verification.dto.MyChallengeResponse;
import com.godlife.backend.verification.dto.MyVerificationResponse;
import com.godlife.backend.verification.dto.VerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** 챌린지 인증 사진. 로그인 + 개설자/참가자만 (아니면 404). */
@RestController
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    /** 오늘 인증 올리기 (multipart: file = JPG/PNG 5MB 이하). 하루 한 번, 다시 올릴 수 없다. */
    @PostMapping(value = "/api/challenges/{id}/verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submit(@PathVariable Long id,
                                                       @AuthenticationPrincipal AuthUser authUser,
                                                       @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(verificationService.submit(id, authUser.id(), file));
    }

    /** 그날 참가자들의 인증 (?date=2026-10-01, 없으면 오늘) */
    @GetMapping("/api/challenges/{id}/verifications")
    public List<VerificationResponse> list(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return verificationService.list(id, authUser.id(), date);
    }

    /** 내 인증 현황 (오늘 인증했는지, 진행률, 연속 기록) */
    @GetMapping("/api/challenges/{id}/verifications/me")
    public MyVerificationResponse mine(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return verificationService.mine(id, authUser.id());
    }

    /** 내 챌린지 (참여 중이거나 개설한, 아직 끝나지 않은 챌린지와 오늘 인증 상태) */
    @GetMapping("/api/me/challenges")
    public List<MyChallengeResponse> myChallenges(@AuthenticationPrincipal AuthUser authUser) {
        return verificationService.myChallenges(authUser.id());
    }

    /** 인증 사진 받기. 이미지 태그로는 토큰을 못 보내서 프론트가 fetch 로 받아 보여 준다. */
    @GetMapping("/api/challenges/{id}/verifications/{verificationId}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id, @PathVariable Long verificationId,
                                          @AuthenticationPrincipal AuthUser authUser) {
        Path path = verificationService.imageFile(id, authUser.id(), verificationId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(path));
    }
}
