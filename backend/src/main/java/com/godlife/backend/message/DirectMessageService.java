package com.godlife.backend.message;

import com.godlife.backend.block.UserBlockRepository;
import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.follow.FollowRepository;
import com.godlife.backend.message.DmThreadRepository.Status;
import com.godlife.backend.message.DmThreadRepository.Thread;
import com.godlife.backend.message.dto.MessageDtos.ConversationResponse;
import com.godlife.backend.message.dto.MessageDtos.InviteCard;
import com.godlife.backend.message.dto.MessageDtos.MessageResponse;
import com.godlife.backend.message.dto.MessageDtos.Partner;
import com.godlife.backend.message.dto.MessageDtos.RoomResponse;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 1:1 메시지.
 * - 맞팔로우거나 같은 챌린지 참가자면 바로 대화한다.
 * - 그 밖의 사람에게는 '메시지 요청'으로 간다: 받은 사람이 수락(또는 답장)하면 대화가 열리고,
 *   수락 전에는 보낸 사람이 3개까지만 보낼 수 있다. 거절하면 보낸 사람은 더 보낼 수 없다.
 * - 차단한 사이(어느 쪽이든)는 보낼 수 없다. 도배 방지: 1분에 30개.
 */
@Service
@RequiredArgsConstructor
public class DirectMessageService {

    public static final int PAGE = 50;
    /** 수락 전 메시지 요청으로 보낼 수 있는 개수 */
    public static final int REQUEST_MAX = 3;
    private static final int SEND_LIMIT = 30;
    private static final Duration SEND_WINDOW = Duration.ofMinutes(1);

    private final DirectMessageRepository messageRepository;
    private final DmThreadRepository threadRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final UserBlockRepository blockRepository;
    private final RequestThrottle throttle;
    private final NotificationService notificationService;
    private final ChallengeService challengeService;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    /** 지금 이 상대에게 보낼 수 있는지 (방 머리 · 보내기 · 목록이 같은 판단을 쓴다) */
    record Access(boolean canSend, String reason, boolean direct, boolean mutual, boolean shared, String request,
                  int requestLeft, Thread thread) {
    }

    Access access(Long me, Long partnerId) {
        if (blockRepository.existsByBlockerIdAndBlockedId(me, partnerId)
                || blockRepository.existsByBlockerIdAndBlockedId(partnerId, me)) {
            return new Access(false, "BLOCKED", false, false, false, null, 0, null);
        }
        boolean mutual = followRepository.exists(me, partnerId) && followRepository.exists(partnerId, me);
        Thread thread = threadRepository.find(me, partnerId).orElse(null);
        boolean shared = threadRepository.sharesChallenge(me, partnerId);
        if (mutual || shared || (thread != null && thread.status() == Status.ACCEPTED)) {
            return new Access(true, null, true, mutual, shared, null, 0, thread);
        }
        if (thread == null) {
            // 첫 메시지가 요청이 된다
            return new Access(true, null, false, false, false, null, REQUEST_MAX, null);
        }
        boolean iRequested = thread.requesterId().equals(me);
        if (thread.status() == Status.PENDING) {
            if (!iRequested) {
                // 받은 요청: 답장하면 수락
                return new Access(true, null, false, false, false, "RECEIVED", 0, thread);
            }
            int left = (int) Math.max(0, REQUEST_MAX - threadRepository.pendingSent(me, partnerId));
            return new Access(left > 0, left > 0 ? null : "REQUEST_LIMIT", false, false, false, "SENT", left, thread);
        }
        // 거절됨: 보낸 사람은 더 못 보내고, 거절한 사람이 먼저 보내면 다시 열린다
        return iRequested
                ? new Access(false, "NOT_AVAILABLE", false, false, false, null, 0, thread)
                : new Access(true, null, false, false, false, null, 0, thread);
    }

    @Transactional(readOnly = true)
    public RoomResponse room(Long me, Long partnerId) {
        User partner = partner(me, partnerId);
        Access a = access(me, partnerId);
        return new RoomResponse(toPartner(partner), a.canSend(), a.reason(), a.direct(), a.mutual(), a.shared(),
                a.request(), a.requestLeft());
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
        // 받은 메시지 요청은 수락하기 전까지 읽어도 읽음으로 바꾸지 않는다 (보낸 사람에게 '읽음'이 안 보이게)
        if (before == null && !isReceivedRequest(me, partnerId)) {
            messageRepository.markRead(me, partnerId, LocalDateTime.now(clock));
        }
        Map<Long, InviteCard> cards = inviteCards(messages);
        return messages.stream()
                .map(m -> new MessageResponse(m.getId(), m.getContent(), m.getCreatedAt(),
                        m.getSenderId().equals(me), m.getReadAt() != null,
                        m.getChallengeId() == null ? null : cards.get(m.getChallengeId())))
                .toList();
    }

    /** 메시지에 달린 챌린지들의 초대 카드 (챌린지 id → 카드) */
    private Map<Long, InviteCard> inviteCards(List<DirectMessage> messages) {
        List<Long> ids = messages.stream().map(DirectMessage::getChallengeId).filter(Objects::nonNull).distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<Long, InviteCard> cards = new HashMap<>();
        jdbc.query("""
                SELECT c.id, c.title, cat.name AS category, c.mode, c.start_date, c.end_date, c.entry_fee,
                       c.invite_code, c.status
                FROM challenges c JOIN categories cat ON cat.id = c.category_id
                WHERE c.id IN (:ids)
                """, new MapSqlParameterSource("ids", ids), rs -> {
            LocalDate start = rs.getDate("start_date").toLocalDate();
            boolean open = List.of("RECRUITING", "ONGOING").contains(rs.getString("status")) && !today.isAfter(start);
            cards.put(rs.getLong("id"), new InviteCard(rs.getLong("id"), rs.getString("title"),
                    rs.getString("category"), rs.getString("mode"), start, rs.getDate("end_date").toLocalDate(),
                    rs.getLong("entry_fee"), rs.getString("invite_code"), open));
        });
        return cards;
    }

    @Transactional
    public MessageResponse send(Long me, Long partnerId, String content) {
        partner(me, partnerId);
        Access a = access(me, partnerId);
        if (!a.canSend()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_ALLOWED, messageFor(a.reason()));
        }
        throttle.check("dm:" + me, SEND_LIMIT, SEND_WINDOW);
        if (!a.direct()) {
            Thread t = a.thread();
            if (t == null) {
                threadRepository.request(me, partnerId); // 첫 메시지 → 메시지 요청
                String nickname = userRepository.findById(me).map(User::getNickname).orElse("누군가");
                notificationService.notify(partnerId, Notification.Type.MESSAGE_REQUEST, "메시지 요청",
                        nickname + "님이 메시지 요청을 보냈어요.", "/messages", "dmreq:" + me);
            } else if (!t.requesterId().equals(me)) {
                threadRepository.setStatus(me, partnerId, Status.ACCEPTED); // 받은 요청에 답장 = 수락
            }
        }
        DirectMessage saved = messageRepository.saveAndFlush(DirectMessage.of(me, partnerId, content.strip()));
        DirectMessage read = messageRepository.findById(saved.getId()).orElse(saved);
        return new MessageResponse(read.getId(), read.getContent(), read.getCreatedAt(), true, false, null);
    }

    /**
     * 대화 상대를 챌린지에 초대한다: 대화방에 초대 카드가 올라간다 ([같이 챌린지 만들기]로 만든 직후에 부른다).
     * 내가 개설자·참가자인, 아직 참여할 수 있는 챌린지만. 수락 전인 메시지 요청으로는 초대할 수 없다.
     */
    @Transactional
    public MessageResponse invite(Long me, Long partnerId, Long challengeId) {
        partner(me, partnerId);
        Access a = access(me, partnerId);
        if (!a.canSend()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_ALLOWED, messageFor(a.reason()));
        }
        if (!a.direct()) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_ALLOWED, "대화가 시작된 뒤에 챌린지에 초대할 수 있어요.");
        }
        throttle.check("dm:" + me, SEND_LIMIT, SEND_WINDOW);
        Challenge challenge = challengeService.requireInvitable(challengeId, me);
        DirectMessage saved = messageRepository.saveAndFlush(DirectMessage.invite(me, partnerId,
                "'" + challenge.getTitle() + "' 챌린지에 초대했어요.", challenge.getId()));
        DirectMessage read = messageRepository.findById(saved.getId()).orElse(saved);
        return new MessageResponse(read.getId(), read.getContent(), read.getCreatedAt(), true, false,
                inviteCards(List.of(read)).get(challenge.getId()));
    }

    /** 받은 메시지 요청 수락 */
    @Transactional
    public void accept(Long me, Long partnerId) {
        respond(me, partnerId, Status.ACCEPTED);
    }

    /** 받은 메시지 요청 거절 (보낸 사람에게는 '지금은 보낼 수 없어요'로만 보인다) */
    @Transactional
    public void decline(Long me, Long partnerId) {
        respond(me, partnerId, Status.DECLINED);
    }

    private void respond(Long me, Long partnerId, Status status) {
        partner(me, partnerId);
        threadRepository.find(me, partnerId)
                .filter(th -> th.status() == Status.PENDING && th.requesterId().equals(partnerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_ALLOWED, "받은 메시지 요청이 없어요."));
        threadRepository.setStatus(me, partnerId, status);
    }

    private boolean isReceivedRequest(Long me, Long partnerId) {
        return threadRepository.find(me, partnerId)
                .filter(th -> th.status() == Status.PENDING && th.requesterId().equals(partnerId))
                .isPresent() && "RECEIVED".equals(access(me, partnerId).request());
    }

    /** 대기 중인 요청의 상대별 상태 (RECEIVED / SENT). 맞팔로우·같은 챌린지가 된 사이는 요청이 아니라서 빠진다. */
    private Map<Long, String> requestStates(Long me) {
        Map<Long, String> states = new HashMap<>();
        for (Long partnerId : threadRepository.pendingPartners(me)) {
            String request = access(me, partnerId).request();
            if (request != null) {
                states.put(partnerId, request);
            }
        }
        return states;
    }

    /**
     * 대화 목록: 대화마다 마지막 메시지 + 안 읽은 수 + 요청 상태. 최근 대화부터.
     * 내가 차단한 사람과 내가 거절한 요청은 뺀다. 받은 요청(RECEIVED)은 화면의 '요청' 탭으로 간다.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> conversations(Long me) {
        Map<Long, String> requests = requestStates(me);
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
                  AND NOT EXISTS (SELECT 1 FROM dm_threads t
                                  WHERE t.low_id = LEAST(:me, u.id) AND t.high_id = GREATEST(:me, u.id)
                                    AND t.status = 'DECLINED' AND t.requester_id = u.id)
                ORDER BY m.id DESC
                LIMIT 100
                """, new MapSqlParameterSource("me", me),
                (rs, i) -> new ConversationResponse(
                        new Partner(rs.getLong("pid"), rs.getString("nickname"), rs.getString("profile_image_url")),
                        rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getLong("sender_id") == me, rs.getLong("unread"), requests.get(rs.getLong("pid"))));
    }

    /**
     * 안 읽은 메시지 수(count)와 받은 메시지 요청 수(requests) (헤더 표시용).
     * 내가 차단한 사람 · 내가 거절한 요청 · 아직 수락 안 한 요청의 메시지는 안 읽은 수에서 뺀다.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(Long me) {
        List<Long> received = requestStates(me).entrySet().stream()
                .filter(e -> "RECEIVED".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM direct_messages m
                  JOIN users u ON u.id = m.sender_id AND u.status = 'ACTIVE'
                WHERE m.receiver_id = :me AND m.read_at IS NULL
                  AND m.sender_id NOT IN (:received)
                  AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE b.blocker_id = :me AND b.blocked_id = m.sender_id)
                  AND NOT EXISTS (SELECT 1 FROM dm_threads t
                                  WHERE t.low_id = m.low_id AND t.high_id = m.high_id
                                    AND t.status = 'DECLINED' AND t.requester_id = m.sender_id)
                """, new MapSqlParameterSource("me", me)
                        // 빈 목록이면 IN () 이 깨지므로 없는 id 하나를 넣는다
                        .addValue("received", received.isEmpty() ? List.of(-1L) : received), Long.class);
        return Map.of("count", n == null ? 0 : n, "requests", (long) received.size());
    }

    private static String messageFor(String reason) {
        return switch (reason) {
            case "BLOCKED" -> "메시지를 보낼 수 없는 회원이에요.";
            case "REQUEST_LIMIT" -> "상대가 메시지 요청을 수락하기 전에는 " + REQUEST_MAX + "개까지 보낼 수 있어요.";
            default -> "지금은 메시지를 보낼 수 없어요.";
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
