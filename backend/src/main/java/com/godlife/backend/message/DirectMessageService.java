package com.godlife.backend.message;

import com.godlife.backend.block.UserBlockRepository;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.follow.FollowRepository;
import com.godlife.backend.message.dto.MessageDtos.ConversationResponse;
import com.godlife.backend.message.dto.MessageDtos.MessageResponse;
import com.godlife.backend.message.dto.MessageDtos.Partner;
import com.godlife.backend.message.dto.MessageDtos.RoomResponse;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1:1 메시지. 서로 팔로우(맞팔로우)한 회원끼리만 보낼 수 있다.
 * - 차단한 사이(어느 쪽이든)는 보낼 수 없다 (차단하면 팔로우도 끊긴다)
 * - 맞팔로우가 풀려도 지난 대화는 볼 수 있지만 새로 보낼 수는 없다
 * - 도배 방지: 1분에 30개
 */
@Service
@RequiredArgsConstructor
public class DirectMessageService {

    public static final int PAGE = 50;
    private static final int SEND_LIMIT = 30;
    private static final Duration SEND_WINDOW = Duration.ofMinutes(1);

    private final DirectMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final UserBlockRepository blockRepository;
    private final RequestThrottle throttle;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** 대화방 머리: 상대 정보와 지금 보낼 수 있는지 */
    @Transactional(readOnly = true)
    public RoomResponse room(Long me, Long partnerId) {
        User partner = partner(me, partnerId);
        String reason = blockReason(me, partnerId);
        return new RoomResponse(toPartner(partner), reason == null, reason);
    }

    /**
     * 메시지 목록. after = 3초마다 새 메시지, before = 이전 메시지 더 보기, 둘 다 없으면 최신 50개.
     * 상대가 보낸 메시지는 읽음으로 바꾼다 (이전 메시지 더 보기는 제외).
     */
    @Transactional
    public List<MessageResponse> list(Long me, Long partnerId, Long after, Long before) {
        partner(me, partnerId);
        long low = Math.min(me, partnerId);
        long high = Math.max(me, partnerId);
        List<DirectMessage> messages;
        if (after != null) {
            messages = messageRepository.findAfter(low, high, after, PageRequest.of(0, PAGE));
        } else {
            messages = new ArrayList<>(before != null
                    ? messageRepository.findBefore(low, high, before, PageRequest.of(0, PAGE))
                    : messageRepository.findLatest(low, high, PageRequest.of(0, PAGE)));
            Collections.reverse(messages);
        }
        if (before == null) {
            messageRepository.markRead(me, partnerId, LocalDateTime.now(clock));
        }
        return messages.stream()
                .map(m -> new MessageResponse(m.getId(), m.getContent(), m.getCreatedAt(),
                        m.getSenderId().equals(me), m.getReadAt() != null))
                .toList();
    }

    @Transactional
    public MessageResponse send(Long me, Long partnerId, String content) {
        partner(me, partnerId);
        String reason = blockReason(me, partnerId);
        if (reason != null) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_ALLOWED, messageFor(reason));
        }
        throttle.check("dm:" + me, SEND_LIMIT, SEND_WINDOW);
        DirectMessage saved = messageRepository.saveAndFlush(DirectMessage.of(me, partnerId, content.strip()));
        DirectMessage read = messageRepository.findById(saved.getId()).orElse(saved);
        return new MessageResponse(read.getId(), read.getContent(), read.getCreatedAt(), true, false);
    }

    /** 대화 목록: 대화마다 마지막 메시지 + 안 읽은 수. 내가 차단한 사람은 빼고, 최근 대화부터. */
    @Transactional(readOnly = true)
    public List<ConversationResponse> conversations(Long me) {
        return jdbc.query("""
                SELECT u.id AS pid, u.nickname, u.profile_image_url, m.content, m.created_at, m.sender_id,
                       (SELECT COUNT(*) FROM direct_messages x
                        WHERE x.receiver_id = :me AND x.sender_id = u.id AND x.read_at IS NULL) AS unread
                FROM direct_messages m
                JOIN (SELECT MAX(id) AS id FROM direct_messages
                      WHERE sender_id = :me OR receiver_id = :me GROUP BY low_id, high_id) last ON last.id = m.id
                JOIN users u ON u.id = IF(m.sender_id = :me, m.receiver_id, m.sender_id)
                WHERE u.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = :me AND b.blocked_id = u.id)
                ORDER BY m.id DESC
                LIMIT 100
                """, new MapSqlParameterSource("me", me),
                (rs, i) -> new ConversationResponse(
                        new Partner(rs.getLong("pid"), rs.getString("nickname"), rs.getString("profile_image_url")),
                        rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getLong("sender_id") == me, rs.getLong("unread")));
    }

    /** 안 읽은 메시지 수 (헤더 표시용, 내가 차단한 사람 것은 빼고) */
    @Transactional(readOnly = true)
    public long unreadCount(Long me) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM direct_messages m
                WHERE m.receiver_id = :me AND m.read_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = :me AND b.blocked_id = m.sender_id)
                """, new MapSqlParameterSource("me", me), Long.class);
        return n == null ? 0 : n;
    }

    /** 보낼 수 없는 이유 (보낼 수 있으면 null) */
    private String blockReason(Long me, Long partnerId) {
        if (blockRepository.existsByBlockerIdAndBlockedId(me, partnerId)
                || blockRepository.existsByBlockerIdAndBlockedId(partnerId, me)) {
            return "BLOCKED";
        }
        if (!followRepository.exists(me, partnerId)) {
            return "NOT_FOLLOWING";
        }
        if (!followRepository.exists(partnerId, me)) {
            return "NOT_FOLLOWED_BACK";
        }
        return null;
    }

    private static String messageFor(String reason) {
        return switch (reason) {
            case "BLOCKED" -> "메시지를 보낼 수 없는 회원이에요.";
            case "NOT_FOLLOWING" -> "메시지는 서로 팔로우한 친구끼리만 보낼 수 있어요. 먼저 팔로우해 보세요.";
            default -> "상대도 나를 팔로우하면 메시지를 보낼 수 있어요.";
        };
    }

    private User partner(Long me, Long partnerId) {
        if (me.equals(partnerId)) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_ALLOWED, "나에게는 메시지를 보낼 수 없어요.");
        }
        return userRepository.findById(partnerId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private static Partner toPartner(User u) {
        return new Partner(u.getId(), u.getNickname(), u.getProfileImageUrl());
    }
}
