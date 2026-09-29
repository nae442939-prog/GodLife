package com.godlife.backend.chat;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.chat.dto.ChatReportRequest;
import com.godlife.backend.chat.dto.ReportAlertResponse;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.notification.Notification;
import com.godlife.backend.notification.NotificationRepository;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 오픈채팅 관리: 방장 공지 · 강퇴 / 참가자 신고 · 신고 누적 알림. (차단은 사람 단위라 block 패키지) */
@Service
@RequiredArgsConstructor
public class ChatModerationService {

    private static final int NOTICE_PREVIEW = 40;
    private static final int RECENT_DETAILS = 5;

    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;
    private final ChatService chatService;
    private final ChatMessageRepository messageRepository;
    private final ChatReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /** 한 참가자에게 이만큼 신고가 쌓이면 방장에게 알린다 */
    @Value("${app.chat.report-alert-threshold:20}")
    private int alertThreshold;

    /** 방장 공지. 올리면 채팅방에 안내 메시지도 남긴다. 빈 내용이면 내린다. */
    @Transactional
    public void changeNotice(Long challengeId, Long hostId, String content) {
        boolean posted = challengeService.changeNotice(challengeId, hostId, content);
        if (posted) {
            String text = content.strip();
            String preview = text.length() > NOTICE_PREVIEW ? text.substring(0, NOTICE_PREVIEW) + "…" : text;
            chatService.postSystem(challengeId, hostId, "📢 방장이 공지를 올렸어요: " + preview);
        }
    }

    /** 방장이 내보내기. 그 사람의 메시지는 가려지고, 쌓인 신고는 처리됨으로, 채팅방에 안내가 남는다. */
    @Transactional
    public void kick(Long challengeId, Long hostId, Long targetUserId) {
        String nickname = challengeService.kick(challengeId, hostId, targetUserId);
        reportRepository.resolve(challengeId, targetUserId);
        chatService.postSystem(challengeId, hostId, nickname + "님이 방장에 의해 내보내졌어요.");
    }

    /**
     * 메시지 신고. 참가자만, 남의 일반 메시지만, 한 메시지에 한 번만.
     * 이 챌린지에서 그 사람에게 쌓인 신고가 기준에 딱 닿으면 방장에게 알림을 남긴다(한 번만).
     */
    @Transactional
    public void report(Long challengeId, Long reporterId, Long messageId, ChatReportRequest request) {
        challengeService.requireMember(challengeId, reporterId);
        ChatMessage message = messageRepository.findById(messageId)
                .filter(m -> m.getChallengeId().equals(challengeId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        if (message.getType() == ChatMessageType.SYSTEM || message.getSenderId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT);
        }
        if (reportRepository.existsByMessageIdAndReporterId(messageId, reporterId)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        try {
            reportRepository.saveAndFlush(ChatReport.of(message, reporterId, request.reason(), request.detail()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        long open = reportRepository.countByChallengeIdAndReportedUserIdAndStatus(challengeId,
                message.getSenderId(), ReportStatus.OPEN);
        if (open == alertThreshold) {
            notifyHost(challengeId, message.getSenderId(), open);
        }
    }

    /** 방장 전용: 신고가 기준 이상 쌓인 참가자와 사유 */
    @Transactional(readOnly = true)
    public List<ReportAlertResponse> alerts(Long challengeId, Long hostId) {
        requireHost(challengeId, hostId);
        Map<Long, List<ChatReport>> byUser = reportRepository
                .findByChallengeIdAndStatusOrderByIdDesc(challengeId, ReportStatus.OPEN).stream()
                .collect(Collectors.groupingBy(ChatReport::getReportedUserId, LinkedHashMap::new, Collectors.toList()));
        byUser.values().removeIf(reports -> reports.size() < alertThreshold);
        Map<Long, String> nicknames = userRepository.findAllById(byUser.keySet()).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return byUser.entrySet().stream()
                .map(e -> toAlert(e.getKey(), nicknames.getOrDefault(e.getKey(), "알 수 없음"), e.getValue()))
                .toList();
    }

    /** 방장이 보고 넘김: 그 사람에게 쌓인 신고를 처리됨으로 (다시 쌓이면 다시 알린다) */
    @Transactional
    public void dismiss(Long challengeId, Long hostId, Long userId) {
        requireHost(challengeId, hostId);
        reportRepository.resolve(challengeId, userId);
    }

    private void requireHost(Long challengeId, Long userId) {
        challengeService.requireMember(challengeId, userId);
        if (!challengeService.isHost(challengeId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void notifyHost(Long challengeId, Long reportedUserId, long count) {
        Challenge c = challengeRepository.findById(challengeId).orElseThrow();
        String nickname = userRepository.findById(reportedUserId).map(User::getNickname).orElse("알 수 없음");
        notificationRepository.save(Notification.of(c.getHostId(), Notification.Type.REPORT_ALERT,
                "신고가 쌓인 참가자가 있어요",
                "'" + c.getTitle() + "' 오픈채팅에서 " + nickname + "님이 신고를 " + count
                        + "번 받았어요. 사유를 보고 내보낼지 정해 주세요."));
    }

    private static ReportAlertResponse toAlert(Long userId, String nickname, List<ChatReport> reports) {
        Map<ReportReason, Long> reasons = reports.stream()
                .collect(Collectors.groupingBy(ChatReport::getReason, () -> new EnumMap<>(ReportReason.class),
                        Collectors.counting()));
        List<String> details = reports.stream()
                .map(ChatReport::getDetail)
                .filter(Objects::nonNull)
                .limit(RECENT_DETAILS)
                .toList();
        return new ReportAlertResponse(userId, nickname, reports.size(), reasons, details);
    }
}
