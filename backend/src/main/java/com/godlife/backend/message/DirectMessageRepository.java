package com.godlife.backend.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /** 대화방 최신 메시지 (최신순 → 화면에서 뒤집는다) */
    @Query("SELECT m FROM DirectMessage m WHERE m.lowId = :low AND m.highId = :high ORDER BY m.id DESC")
    List<DirectMessage> findLatest(@Param("low") Long low, @Param("high") Long high, Pageable pageable);

    /** 3초마다: after 보다 새 메시지 (오래된 순) */
    @Query("""
            SELECT m FROM DirectMessage m
            WHERE m.lowId = :low AND m.highId = :high AND m.id > :after
            ORDER BY m.id
            """)
    List<DirectMessage> findAfter(@Param("low") Long low, @Param("high") Long high, @Param("after") Long after,
                                  Pageable pageable);

    /** 이전 메시지 더 보기: before 보다 오래된 메시지 (최신순) */
    @Query("""
            SELECT m FROM DirectMessage m
            WHERE m.lowId = :low AND m.highId = :high AND m.id < :before
            ORDER BY m.id DESC
            """)
    List<DirectMessage> findBefore(@Param("low") Long low, @Param("high") Long high, @Param("before") Long before,
                                   Pageable pageable);

    /** 상대가 보낸 메시지를 읽음으로 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DirectMessage m SET m.readAt = :now
            WHERE m.receiverId = :me AND m.senderId = :partner AND m.readAt IS NULL
            """)
    int markRead(@Param("me") Long me, @Param("partner") Long partner, @Param("now") LocalDateTime now);

    boolean existsByLowIdAndHighId(Long lowId, Long highId);
}
