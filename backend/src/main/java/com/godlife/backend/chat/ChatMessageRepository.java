package com.godlife.backend.chat;

import com.godlife.backend.chat.dto.ChatMessageRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 방(challengeId)별로 id 커서를 기준으로 읽는다. 개수 제한은 Pageable 로 준다.
 * - 보낸 사람이 강퇴됐으면 senderKicked = true (내용은 서비스가 가린다)
 * - 읽는 사람(viewerId)이 차단한 사람의 메시지는 빼고 준다. 안내(SYSTEM) 메시지는 항상 보인다.
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    String ROW = """
            SELECT new com.godlife.backend.chat.dto.ChatMessageRow(
                m.id, m.type, m.senderId, u.nickname, u.profileImageUrl, m.content, m.createdAt,
                CASE WHEN p.status = com.godlife.backend.challenge.ParticipantStatus.KICKED THEN true ELSE false END)
            FROM ChatMessage m
            JOIN User u ON u.id = m.senderId
            LEFT JOIN ChallengeParticipant p ON p.challengeId = m.challengeId AND p.userId = m.senderId
            WHERE m.challengeId = :challengeId
              AND (m.type = com.godlife.backend.chat.ChatMessageType.SYSTEM
                   OR NOT EXISTS (SELECT 1 FROM UserBlock b WHERE b.blockerId = :viewerId AND b.blockedId = m.senderId))
            """;

    /** 폴링: afterId 보다 새 메시지, 오래된 순 */
    @Query(ROW + "AND m.id > :afterId ORDER BY m.id ASC")
    List<ChatMessageRow> findAfter(@Param("challengeId") Long challengeId, @Param("viewerId") Long viewerId,
                                   @Param("afterId") Long afterId, Pageable limit);

    /** 처음 열 때(beforeId = 아주 큰 값) / 위로 스크롤: beforeId 보다 오래된 메시지, 최신 순 */
    @Query(ROW + "AND m.id < :beforeId ORDER BY m.id DESC")
    List<ChatMessageRow> findBefore(@Param("challengeId") Long challengeId, @Param("viewerId") Long viewerId,
                                    @Param("beforeId") Long beforeId, Pageable limit);

    /** 챌린지 삭제 시 채팅방 메시지를 한 번에 지운다. */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);
}
