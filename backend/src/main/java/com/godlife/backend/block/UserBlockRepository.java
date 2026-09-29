package com.godlife.backend.block;

import com.godlife.backend.block.dto.BlockedUserResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Modifying
    @Query("DELETE FROM UserBlock b WHERE b.blockerId = :blockerId AND b.blockedId = :blockedId")
    int deleteBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** 내가 차단한 사람들, 최근에 차단한 순 */
    @Query("""
            SELECT new com.godlife.backend.block.dto.BlockedUserResponse(u.id, u.nickname, u.profileImageUrl, b.createdAt)
            FROM UserBlock b JOIN User u ON u.id = b.blockedId
            WHERE b.blockerId = :blockerId
            ORDER BY b.id DESC
            """)
    List<BlockedUserResponse> findBlocked(@Param("blockerId") Long blockerId);
}
