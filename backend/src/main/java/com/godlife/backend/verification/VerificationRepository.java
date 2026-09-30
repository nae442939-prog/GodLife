package com.godlife.backend.verification;

import com.godlife.backend.verification.dto.VerificationResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VerificationRepository extends JpaRepository<Verification, Long> {

    boolean existsByParticipantIdAndVerifyDate(Long participantId, LocalDate verifyDate);

    /** from ~ to (둘 다 포함) 사이에 올린 인증 수. 주 N회 챌린지의 이번 주 횟수에 쓴다. */
    long countByParticipantIdAndVerifyDateBetween(Long participantId, LocalDate from, LocalDate to);

    boolean existsByImageHash(String imageHash);

    /** 그날 챌린지 참가자들의 인증 (내보낸 사람 제외), 먼저 올린 순. mine 은 서비스에서 채운다. */
    @Query("""
            SELECT new com.godlife.backend.verification.dto.VerificationResponse(
                       v.id, u.id, u.nickname, u.profileImageUrl, v.receivedAt, false)
            FROM Verification v
              JOIN com.godlife.backend.challenge.ChallengeParticipant p ON p.id = v.participantId
              JOIN com.godlife.backend.user.User u ON u.id = p.userId
            WHERE p.challengeId = :challengeId AND v.verifyDate = :date
              AND p.status <> com.godlife.backend.challenge.ParticipantStatus.KICKED
            ORDER BY v.receivedAt, v.id
            """)
    List<VerificationResponse> findByChallengeAndDate(@Param("challengeId") Long challengeId,
                                                      @Param("date") LocalDate date);

    /** 이 챌린지의 인증 사진 파일 키 (내보낸 사람 것은 가린다) */
    @Query("""
            SELECT v.imageKey
            FROM Verification v
              JOIN com.godlife.backend.challenge.ChallengeParticipant p ON p.id = v.participantId
            WHERE v.id = :id AND p.challengeId = :challengeId
              AND p.status <> com.godlife.backend.challenge.ParticipantStatus.KICKED
            """)
    Optional<String> findImageKey(@Param("challengeId") Long challengeId, @Param("id") Long id);
}
