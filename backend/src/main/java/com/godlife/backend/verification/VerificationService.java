package com.godlife.backend.verification;

import com.godlife.backend.challenge.Challenge;
import com.godlife.backend.challenge.ChallengeParticipant;
import com.godlife.backend.challenge.ChallengeParticipantRepository;
import com.godlife.backend.challenge.ChallengeRepository;
import com.godlife.backend.challenge.ChallengeService;
import com.godlife.backend.challenge.FrequencyType;
import com.godlife.backend.common.crypto.Hashing;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.notification.NotificationService;
import com.godlife.backend.verification.ai.AiVerifier;
import com.godlife.backend.verification.dto.MyChallengeResponse;
import com.godlife.backend.verification.dto.MyVerificationResponse;
import com.godlife.backend.verification.dto.VerificationResponse;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 챌린지 인증 사진.
 * - 하루는 서버 시각 기준 밤 12시(00:00)부터 다음 날 밤 12시까지. 하루 한 번, 취소·다시 올리기 없음.
 * - 사진은 같은 챌린지 참가자(개설자 포함)끼리 서로 볼 수 있다.
 * - 올릴 때 직접 학습한 분류 모델(ai-server)이 사진을 본다: 통과 / 다시 찍기(저장하지 않음) / 관리자 검토 ({@link AiVerifier}).
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final VerificationRepository verificationRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeService challengeService;
    private final ImageStore imageStore;
    private final NotificationService notificationService;
    private final AiVerifier aiVerifier;
    private final Clock clock;
    private final EntityManager entityManager;

    /**
     * 인증 사진 올리기. 참가자 행을 잠근 채로 오늘 인증 여부·이번 주 횟수를 확인하므로
     * 버튼을 여러 번 눌러도 한 번만 들어간다. (participant_id, verify_date) 유니크 제약이 마지막 안전장치.
     */
    @Transactional
    public VerificationResponse submit(Long challengeId, Long userId, MultipartFile file) {
        challengeService.requireMember(challengeId, userId);
        ChallengeParticipant participant = participantRepository.findForUpdate(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
        // requireMember 가 잠그지 않고 먼저 읽어 둔 객체가 있으면 잠금 쿼리를 보내도 예전 값이 돌아오므로 다시 읽는다
        entityManager.refresh(participant);
        if (!participant.isActive()) {
            throw new BusinessException(ErrorCode.NOT_JOINED);
        }
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 잠금을 얻은 뒤의 시각을 쓴다 (먼저 누른 요청이 끝나기를 기다리는 동안 날짜가 바뀔 수 있어서)
        LocalDateTime now = LocalDateTime.now(clock);
        rejectUnlessOpen(challenge, stateOf(challenge, participant, now));

        String hash = hashOf(file);
        if (verificationRepository.existsByImageHash(hash)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHOTO);
        }

        String key = imageStore.storeVerificationImage(challengeId, file);
        Verification saved;
        try {
            // 다시 그려 저장한 사진(촬영 정보가 지워진 JPEG)을 AI 서버에 보낸다
            AiVerifier.Judgement judgement = aiVerifier.judge(challenge, participant.getId(), userId,
                    imageStore.resolve(key), now.toLocalDate());
            if (judgement.rejected()) {
                // 아래 catch 에서 사진을 지운다. 인증 줄을 만들지 않으므로 바로 다시 찍어 올릴 수 있다
                throw new BusinessException(ErrorCode.VERIFICATION_REJECTED, AiVerifier.rejectMessage(challenge));
            }
            saved = verificationRepository.saveAndFlush(
                    Verification.of(participant.getId(), now, key, hash, judgement.status()));
            aiVerifier.record(saved.getId(), judgement);
        } catch (DataIntegrityViolationException e) {
            imageStore.delete(key);
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED_TODAY);
        } catch (RuntimeException e) {
            imageStore.delete(key);
            throw e;
        }
        boolean verifiedYesterday = verificationRepository.existsByParticipantIdAndVerifyDateAndStatusNot(
                participant.getId(), now.toLocalDate().minusDays(1), VerificationStatus.REJECTED);
        participant.recordVerification(verifiedYesterday);
        notificationService.resolve(userId, "verify:" + challengeId + ":" + now.toLocalDate());

        return verificationRepository.findByChallengeAndDate(challengeId, now.toLocalDate()).stream()
                .filter(v -> v.id().equals(saved.getId()))
                .findFirst()
                .orElseThrow()
                .withMine(userId);
    }

    /** 그날(기본 오늘) 참가자들이 올린 인증. 개설자·참가자만. */
    @Transactional(readOnly = true)
    public List<VerificationResponse> list(Long challengeId, Long userId, LocalDate date) {
        challengeService.requireMember(challengeId, userId);
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        return verificationRepository.findByChallengeAndDate(challengeId, day).stream()
                .map(v -> v.withMine(userId))
                .toList();
    }

    /** 내 인증 현황 (참여 중이거나, 끝난 챌린지에서 성공·실패 판정을 받은 사람) */
    @Transactional(readOnly = true)
    public MyVerificationResponse mine(Long challengeId, Long userId) {
        challengeService.requireMember(challengeId, userId);
        ChallengeParticipant participant = participantRepository.findByChallengeIdAndUserId(challengeId, userId)
                .filter(p -> p.getStatus().isMember())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        Integer weekCount = challenge.getFrequencyType() == FrequencyType.WEEKLY_N && challenge.isInProgress(today)
                ? (int) countThisWeek(challenge, participant, today)
                : null;
        return new MyVerificationResponse(stateOf(challenge, participant, now), participant.getStatus(), today,
                participant.getSuccessDays(), challenge.targetCount(), participant.getCurrentStreak(),
                participant.getMaxStreak(), weekCount);
    }

    /** 내 챌린지: 참여 중이거나 개설한, 아직 끝나지 않은 챌린지와 오늘 인증 상태. 진행 중인 것이 먼저. */
    @Transactional(readOnly = true)
    public List<MyChallengeResponse> myChallenges(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        return challengeRepository.findMine(userId, today).stream()
                .map(c -> {
                    ChallengeParticipant p = participantRepository.findByChallengeIdAndUserId(c.getId(), userId)
                            .filter(ChallengeParticipant::isActive)
                            .orElse(null);
                    return p == null
                            ? MyChallengeResponse.of(c, today, false, userId, 0, null)
                            : MyChallengeResponse.of(c, today, true, userId, p.getSuccessDays(), stateOf(c, p, now));
                })
                .sorted(Comparator.comparing((MyChallengeResponse r) -> !r.inProgress()))
                .toList();
    }

    /** 인증 사진 파일. 개설자·참가자만. */
    @Transactional(readOnly = true)
    public Path imageFile(Long challengeId, Long userId, Long verificationId) {
        challengeService.requireMember(challengeId, userId);
        String key = verificationRepository.findImageKey(challengeId, verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));
        Path path = imageStore.resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        }
        return path;
    }

    private VerifyState stateOf(Challenge c, ChallengeParticipant p, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (today.isBefore(c.getStartDate())) {
            return VerifyState.NOT_STARTED;
        }
        if (!c.isInProgress(today) || !p.isActive()) {
            return VerifyState.ENDED;
        }
        if (verificationRepository.existsByParticipantIdAndVerifyDate(p.getId(), today)) {
            return VerifyState.DONE_TODAY;
        }
        if (c.getFrequencyType() == FrequencyType.WEEKLY_N && countThisWeek(c, p, today) >= c.getWeeklyCount()) {
            return VerifyState.WEEK_DONE;
        }
        if (!c.isVerifyTimeOpen(now.toLocalTime())) {
            return VerifyState.TIME_CLOSED;
        }
        return VerifyState.OPEN;
    }

    private long countThisWeek(Challenge c, ChallengeParticipant p, LocalDate today) {
        LocalDate weekStart = c.weekStartOf(today);
        return verificationRepository.countByParticipantIdAndVerifyDateBetweenAndStatusNot(p.getId(), weekStart,
                weekStart.plusDays(6), VerificationStatus.REJECTED);
    }

    private static void rejectUnlessOpen(Challenge c, VerifyState state) {
        switch (state) {
            case OPEN -> {
            }
            case NOT_STARTED, ENDED -> throw new BusinessException(ErrorCode.CHALLENGE_NOT_IN_PROGRESS);
            case DONE_TODAY -> throw new BusinessException(ErrorCode.ALREADY_VERIFIED_TODAY);
            case WEEK_DONE -> throw new BusinessException(ErrorCode.WEEKLY_GOAL_DONE);
            case TIME_CLOSED -> throw new BusinessException(ErrorCode.VERIFY_TIME_CLOSED,
                    "이 챌린지는 %s ~ %s 에만 인증할 수 있어요.".formatted(
                            c.getVerifyFrom().format(HH_MM), c.getVerifyUntil().format(HH_MM)));
        }
    }

    private static String hashOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        try {
            return Hashing.sha256Hex(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }
}
