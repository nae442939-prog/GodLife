package com.godlife.backend.chat;

import com.godlife.backend.chat.dto.ChatMessageRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 방(challengeId)별로 id 커서를 기준으로 읽는다. 개수 제한은 Pageable 로 준다. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    String ROW = """
            SELECT new com.godlife.backend.chat.dto.ChatMessageRow(
                m.id, m.senderId, u.nickname, u.profileImageUrl, m.content, m.createdAt)
            FROM ChatMessage m JOIN User u ON u.id = m.senderId
            """;

    /** 폴링: afterId 보다 새 메시지, 오래된 순 */
    @Query(ROW + "WHERE m.challengeId = :challengeId AND m.id > :afterId ORDER BY m.id ASC")
    List<ChatMessageRow> findAfter(@Param("challengeId") Long challengeId, @Param("afterId") Long afterId,
                                   Pageable limit);

    /** 처음 열 때(beforeId = 아주 큰 값) / 위로 스크롤: beforeId 보다 오래된 메시지, 최신 순 */
    @Query(ROW + "WHERE m.challengeId = :challengeId AND m.id < :beforeId ORDER BY m.id DESC")
    List<ChatMessageRow> findBefore(@Param("challengeId") Long challengeId, @Param("beforeId") Long beforeId,
                                    Pageable limit);
}
