package com.godlife.backend.challenge;

import com.godlife.backend.challenge.dto.ParticipantResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, Long> {

    Optional<ChallengeParticipant> findByChallengeIdAndUserId(Long challengeId, Long userId);

    /** 챌린지 삭제 시 참가 기록을 한 번에 지운다. */
    @Modifying
    @Query("DELETE FROM ChallengeParticipant p WHERE p.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);

    /** 참여 중인 사람 (취소한 사람 제외), 먼저 참여한 순 */
    @Query("""
            SELECT new com.godlife.backend.challenge.dto.ParticipantResponse(u.nickname, u.profileImageUrl)
            FROM ChallengeParticipant p JOIN User u ON u.id = p.userId
            WHERE p.challengeId = :challengeId
              AND p.status <> com.godlife.backend.challenge.ParticipantStatus.LEFT
            ORDER BY p.id
            """)
    List<ParticipantResponse> findParticipants(@Param("challengeId") Long challengeId);
}
