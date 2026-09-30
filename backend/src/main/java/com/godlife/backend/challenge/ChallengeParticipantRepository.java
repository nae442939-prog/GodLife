package com.godlife.backend.challenge;

import com.godlife.backend.challenge.dto.ParticipantResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 참여 중인 사람 (취소한 사람·내보낸 사람 제외), 먼저 참여한 순 */
    @Query("""
            SELECT new com.godlife.backend.challenge.dto.ParticipantResponse(u.id, u.nickname, u.profileImageUrl)
            FROM ChallengeParticipant p JOIN User u ON u.id = p.userId
            WHERE p.challengeId = :challengeId
              AND p.status NOT IN (com.godlife.backend.challenge.ParticipantStatus.LEFT,
                                   com.godlife.backend.challenge.ParticipantStatus.KICKED)
            ORDER BY p.id
            """)
    List<ParticipantResponse> findParticipants(@Param("challengeId") Long challengeId);
}
