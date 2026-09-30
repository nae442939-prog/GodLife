package com.godlife.backend.challenge;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    /**
     * 모집 중인 공개 챌린지 탐색(비공개는 초대 링크로만). 조건은 모두 선택(null 이면 무시).
     * titlePattern 은 서비스가 % 와 _ 를 ! 로 이스케이프해 만든 LIKE 패턴이다.
     */
    @Query(value = """
            SELECT c FROM Challenge c JOIN FETCH c.category
            WHERE c.status = com.godlife.backend.challenge.ChallengeStatus.RECRUITING
              AND c.startDate >= :today
              AND c.visibility = com.godlife.backend.challenge.ChallengeVisibility.PUBLIC
              AND (:categoryId IS NULL OR c.category.id = :categoryId)
              AND (:mode IS NULL OR c.mode = :mode)
              AND (:titlePattern IS NULL OR c.title LIKE :titlePattern ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(c) FROM Challenge c
            WHERE c.status = com.godlife.backend.challenge.ChallengeStatus.RECRUITING
              AND c.startDate >= :today
              AND c.visibility = com.godlife.backend.challenge.ChallengeVisibility.PUBLIC
              AND (:categoryId IS NULL OR c.category.id = :categoryId)
              AND (:mode IS NULL OR c.mode = :mode)
              AND (:titlePattern IS NULL OR c.title LIKE :titlePattern ESCAPE '!')
            """)
    Page<Challenge> searchRecruiting(@Param("today") LocalDate today,
                                     @Param("categoryId") Integer categoryId,
                                     @Param("mode") ChallengeMode mode,
                                     @Param("titlePattern") String titlePattern,
                                     Pageable pageable);

    /**
     * 내 챌린지: 내가 참여 중이거나(ACTIVE) 개설한, 아직 끝나지 않은 챌린지. 시작일 순.
     * (끝난 챌린지 기록은 갓생기록에서 본다)
     */
    @Query("""
            SELECT c FROM Challenge c JOIN FETCH c.category
            WHERE c.endDate >= :today
              AND c.status NOT IN (com.godlife.backend.challenge.ChallengeStatus.ENDED,
                                   com.godlife.backend.challenge.ChallengeStatus.SETTLED)
              AND (c.hostId = :userId OR EXISTS (
                    SELECT 1 FROM ChallengeParticipant p
                    WHERE p.challengeId = c.id AND p.userId = :userId
                      AND p.status = com.godlife.backend.challenge.ParticipantStatus.ACTIVE))
            ORDER BY c.startDate, c.id
            """)
    List<Challenge> findMine(@Param("userId") Long userId, @Param("today") LocalDate today);

    @Query("SELECT c FROM Challenge c JOIN FETCH c.category WHERE c.id = :id")
    Optional<Challenge> findWithCategory(@Param("id") Long id);

    @Query("SELECT c FROM Challenge c JOIN FETCH c.category WHERE c.inviteCode = :inviteCode")
    Optional<Challenge> findByInviteCode(@Param("inviteCode") String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /** 참여/취소는 챌린지 행을 SELECT ... FOR UPDATE 로 잠가 정원과 참가자 수를 지킨다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Challenge c WHERE c.id = :id")
    Optional<Challenge> findForUpdate(@Param("id") Long id);
}
