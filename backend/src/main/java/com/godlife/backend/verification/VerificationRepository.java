package com.godlife.backend.verification;

import com.godlife.backend.verification.dto.VerificationResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VerificationRepository extends JpaRepository<Verification, Long> {

    boolean existsByParticipantIdAndVerifyDate(Long participantId, LocalDate verifyDate);

    /** 그날 인정되는 인증이 있는지 (관리자 검토에서 거절된 것은 빼고). 연속 기록 계산에 쓴다. */
    boolean existsByParticipantIdAndVerifyDateAndStatusNot(Long participantId, LocalDate verifyDate,
                                                           VerificationStatus status);

    /** from ~ to (둘 다 포함) 사이에 올린 인증 수 (거절된 것은 빼고). 주 N회 챌린지의 이번 주 횟수에 쓴다. */
    long countByParticipantIdAndVerifyDateBetweenAndStatusNot(Long participantId, LocalDate from, LocalDate to,
                                                              VerificationStatus status);

    boolean existsByImageHash(String imageHash);

    /**
     * 그날 챌린지 참가자들의 인증 (내보내졌거나 포기하고 나간 사람, 거절된 사진 제외), 먼저 올린 순.
     * mine · reported 는 서비스에서 채운다.
     */
    @Query("""
            SELECT new com.godlife.backend.verification.dto.VerificationResponse(
                       v.id, u.id, u.nickname, u.profileImageUrl, v.receivedAt, v.status)
            FROM Verification v
              JOIN com.godlife.backend.challenge.ChallengeParticipant p ON p.id = v.participantId
              JOIN com.godlife.backend.user.User u ON u.id = p.userId
            WHERE p.challengeId = :challengeId AND v.verifyDate = :date
              AND v.status <> com.godlife.backend.verification.VerificationStatus.REJECTED
              AND p.status NOT IN (com.godlife.backend.challenge.ParticipantStatus.KICKED,
                                   com.godlife.backend.challenge.ParticipantStatus.GAVE_UP)
            ORDER BY v.receivedAt, v.id
            """)
    List<VerificationResponse> findByChallengeAndDate(@Param("challengeId") Long challengeId,
                                                      @Param("date") LocalDate date);

    /** 이 챌린지의 인증 사진 파일 키 (내보내졌거나 포기한 사람 것, 거절된 사진은 가린다) */
    @Query("""
            SELECT v.imageKey
            FROM Verification v
              JOIN com.godlife.backend.challenge.ChallengeParticipant p ON p.id = v.participantId
            WHERE v.id = :id AND p.challengeId = :challengeId
              AND v.status <> com.godlife.backend.verification.VerificationStatus.REJECTED
              AND p.status NOT IN (com.godlife.backend.challenge.ParticipantStatus.KICKED,
                                   com.godlife.backend.challenge.ParticipantStatus.GAVE_UP)
            """)
    Optional<String> findImageKey(@Param("challengeId") Long challengeId, @Param("id") Long id);

    /** 정산용: 참가자별 from ~ to (둘 다 포함) 인증 수 (거절된 것은 빼고) → [participant_id, count] */
    @Query("""
            SELECT v.participantId, COUNT(v) FROM Verification v
            WHERE v.participantId IN :participantIds AND v.verifyDate BETWEEN :from AND :to
              AND v.status <> com.godlife.backend.verification.VerificationStatus.REJECTED
            GROUP BY v.participantId
            """)
    List<Object[]> countByParticipants(@Param("participantIds") Collection<Long> participantIds,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);
}
