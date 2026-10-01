package com.godlife.backend.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 인증 사진 1건. (participant_id, verify_date) 가 유니크라 하루에 한 번만 올릴 수 있고, 지우거나 바꾸지 않는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "verifications")
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private Long participantId;

    /** 서버가 받은 시각의 날짜 (클라이언트 시각은 믿지 않는다) */
    @Column(name = "verify_date", nullable = false, updatable = false)
    private LocalDate verifyDate;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    /** 저장한 사진의 파일 키 (ImageStore) */
    @Column(name = "image_url", nullable = false, updatable = false)
    private String imageKey;

    /** 올린 파일 그대로의 SHA-256. 같은 파일을 다시 올리면 막는다. */
    @Column(name = "image_hash", nullable = false, updatable = false)
    private String imageHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    /** status: AI 가 통과시켰으면 APPROVED, 애매해서 관리자 검토로 넘겼으면 IN_REVIEW */
    public static Verification of(Long participantId, LocalDateTime receivedAt, String imageKey,
                                  String imageHash, VerificationStatus status) {
        Verification v = new Verification();
        v.participantId = participantId;
        v.receivedAt = receivedAt;
        v.verifyDate = receivedAt.toLocalDate();
        v.imageKey = imageKey;
        v.imageHash = imageHash;
        v.status = status;
        return v;
    }
}
