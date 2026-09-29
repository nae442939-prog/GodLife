package com.godlife.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatReportRepository extends JpaRepository<ChatReport, Long> {

    boolean existsByMessageIdAndReporterId(Long messageId, Long reporterId);

    /** 이 챌린지에서 이 사람에게 쌓인(처리 안 된) 신고 수 */
    long countByChallengeIdAndReportedUserIdAndStatus(Long challengeId, Long reportedUserId, ReportStatus status);

    /** 방장 알림용: 이 챌린지의 처리 안 된 신고 전부 (사람별로 묶는 건 서비스에서) */
    List<ChatReport> findByChallengeIdAndStatusOrderByIdDesc(Long challengeId, ReportStatus status);

    /** 방장이 강퇴하거나 넘기면 그 사람에게 쌓인 신고를 처리됨으로 */
    @Modifying
    @Query("""
            UPDATE ChatReport r SET r.status = com.godlife.backend.chat.ReportStatus.RESOLVED
            WHERE r.challengeId = :challengeId AND r.reportedUserId = :userId
              AND r.status = com.godlife.backend.chat.ReportStatus.OPEN
            """)
    int resolve(@Param("challengeId") Long challengeId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ChatReport r WHERE r.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);
}
