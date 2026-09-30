package com.godlife.backend.challenge;

import com.godlife.backend.challenge.dto.ParticipantResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, Long> {

    Optional<ChallengeParticipant> findByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 인증 제출은 참가자 행을 잠가, 같은 사람이 동시에 여러 번 올려도 하나씩 처리한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ChallengeParticipant p WHERE p.challengeId = :challengeId AND p.userId = :userId")
    Optional<ChallengeParticipant> findForUpdate(@Param("challengeId") Long challengeId,
                                                 @Param("userId") Long userId);

    /** 챌린지 삭제 시 참가 기록을 한 번에 지운다. */
    @Modifying
    @Query("DELETE FROM ChallengeParticipant p WHERE p.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);

    /** 참여 중인 사람 (취소·강퇴·포기한 사람 제외), 먼저 참여한 순 */
    @Query("""
            SELECT new com.godlife.backend.challenge.dto.ParticipantResponse(u.id, u.nickname, u.profileImageUrl)
            FROM ChallengeParticipant p JOIN User u ON u.id = p.userId
            WHERE p.challengeId = :challengeId
              AND p.status NOT IN (com.godlife.backend.challenge.ParticipantStatus.LEFT,
                                   com.godlife.backend.challenge.ParticipantStatus.KICKED,
                                   com.godlife.backend.challenge.ParticipantStatus.GAVE_UP)
            ORDER BY p.id
            """)
    List<ParticipantResponse> findParticipants(@Param("challengeId") Long challengeId);

    /** 이 챌린지에서 아직 판정 전(ACTIVE)인 참가자 (종료 판정용) */
    List<ChallengeParticipant> findByChallengeIdAndStatus(Long challengeId, ParticipantStatus status);

    long countByUserIdAndStatus(Long userId, ParticipantStatus status);

    List<ChallengeParticipant> findByChallengeIdAndStatusIn(Long challengeId, Collection<ParticipantStatus> statuses);

    /** 챌린지 랭킹: 남아 있는 참가자(참여 중·성공·실패)를 인증 횟수 → 최장 연속 → 현재 연속 순으로 */
    @Query("""
            SELECT new com.godlife.backend.settlement.dto.RankingRow(
                       u.id, u.nickname, u.profileImageUrl, p.successDays, p.maxStreak, p.currentStreak)
            FROM ChallengeParticipant p JOIN com.godlife.backend.user.User u ON u.id = p.userId
            WHERE p.challengeId = :challengeId
              AND p.status IN (com.godlife.backend.challenge.ParticipantStatus.ACTIVE,
                               com.godlife.backend.challenge.ParticipantStatus.COMPLETED,
                               com.godlife.backend.challenge.ParticipantStatus.FAILED)
            ORDER BY p.successDays DESC, p.maxStreak DESC, p.currentStreak DESC, p.id
            """)
    List<com.godlife.backend.settlement.dto.RankingRow> findRanking(@Param("challengeId") Long challengeId);

    /**
     * 매일 챌린지에서 어제 인증하지 않은 참가자의 연속 기록을 0으로 (자정 스케줄러).
     * 여러 번 돌아도 결과가 같다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ChallengeParticipant p SET p.currentStreak = 0
            WHERE p.status = com.godlife.backend.challenge.ParticipantStatus.ACTIVE
              AND p.currentStreak > 0
              AND p.challengeId IN (
                    SELECT c.id FROM Challenge c
                    WHERE c.frequencyType = com.godlife.backend.challenge.FrequencyType.DAILY
                      AND c.startDate <= :yesterday AND c.endDate >= :yesterday)
              AND NOT EXISTS (
                    SELECT 1 FROM com.godlife.backend.verification.Verification v
                    WHERE v.participantId = p.id AND v.verifyDate = :yesterday)
            """)
    int resetMissedStreaks(@Param("yesterday") LocalDate yesterday);
}
