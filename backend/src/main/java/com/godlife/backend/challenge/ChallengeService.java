package com.godlife.backend.challenge;

import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.challenge.dto.ChallengeDetailResponse;
import com.godlife.backend.challenge.dto.ChallengeSummaryResponse;
import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.challenge.dto.ParticipantResponse;
import com.godlife.backend.chat.ChatMessageRepository;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    public static final int PAGE_SIZE = 12;
    private static final int CODE_ATTEMPTS = 5;
    private static final Duration INVITE_WINDOW = Duration.ofMinutes(10);

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ChatMessageRepository chatMessageRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final RequestThrottle throttle;
    private final Clock clock;

    /** 같은 IP 가 10분 동안 초대 코드로 조회/참여할 수 있는 횟수 (코드 무작위 대입 방지) */
    @Value("${app.invite.ip-limit:30}")
    private int inviteIpLimit;

    /** 정렬: popular(참가자 많은 순, 기본) / deadline(시작 임박 순) / latest(최신 개설 순) */
    public enum SortOption {
        POPULAR(Sort.by(Sort.Order.desc("participantCount"), Sort.Order.desc("id"))),
        DEADLINE(Sort.by(Sort.Order.asc("startDate"), Sort.Order.asc("id"))),
        LATEST(Sort.by(Sort.Order.desc("id")));

        private final Sort sort;

        SortOption(Sort sort) {
            this.sort = sort;
        }
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findByActiveTrueOrderByIdAsc();
    }

    @Transactional
    public Challenge create(Long userId, ChallengeCreateRequest req) {
        User host = requirePhoneVerified(userId);
        if (req.startDate().isBefore(today())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "시작일은 오늘 이후로 정해 주세요.");
        }
        Category category = categoryRepository.findById(req.categoryId())
                .filter(Category::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        ChallengeVisibility visibility = req.visibility() == null ? ChallengeVisibility.PUBLIC : req.visibility();
        Challenge challenge = Challenge.create(host.getId(), category, req.title().strip(),
                req.description().strip(), req.mode(), visibility, newInviteCode(), req.startDate(), req.endDate(),
                req.frequencyType(),
                req.weeklyCount(), req.entryFee() == null ? 0 : req.entryFee(), req.maxParticipants(),
                req.verifyFrom(), req.verifyUntil(), Boolean.TRUE.equals(req.partialRefund()));
        return challengeRepository.save(challenge);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChallengeSummaryResponse> search(Integer categoryId, ChallengeMode mode, String keyword,
                                                         SortOption sort, int page) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, sort.sort);
        return PageResponse.of(
                challengeRepository.searchRecruiting(today(), categoryId, mode, likePattern(keyword), pageable),
                ChallengeSummaryResponse::from);
    }

    /**
     * 상세. viewerId 는 비로그인이면 null.
     * 비공개 챌린지는 개설자·참가자 말고는 없는 것처럼(404) 보여 준다. (순번 id 를 넣어 보는 식으로 찾지 못하게)
     */
    @Transactional(readOnly = true)
    public ChallengeDetailResponse detail(Long challengeId, Long viewerId) {
        Challenge c = challengeRepository.findWithCategory(challengeId)
                .filter(found -> !found.isPrivate() || isMember(found, viewerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        return toDetail(c, viewerId);
    }

    /** 초대 링크로 들어온 사람에게 보여 줄 상세. 코드가 곧 열쇠라 비공개여도 보여 준다. */
    @Transactional(readOnly = true)
    public ChallengeDetailResponse detailByInvite(String inviteCode, Long viewerId, String clientIp) {
        throttle.check("invite:" + clientIp, inviteIpLimit, INVITE_WINDOW);
        Challenge c = challengeRepository.findByInviteCode(normalize(inviteCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));
        return toDetail(c, viewerId);
    }

    /** 목록/상세에서 참여. 비공개 챌린지는 이 경로로 참여할 수 없다(초대 링크로만). */
    @Transactional
    public void join(Long challengeId, Long userId) {
        requirePhoneVerified(userId);
        Challenge c = challengeRepository.findForUpdate(challengeId)
                .filter(found -> !found.isPrivate())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        addParticipant(c, userId);
    }

    /** 초대 링크로 참여. 참여한 챌린지 id 를 돌려준다. */
    @Transactional
    public Long joinByInvite(String inviteCode, Long userId, String clientIp) {
        throttle.check("invite:" + clientIp, inviteIpLimit, INVITE_WINDOW);
        requirePhoneVerified(userId);
        Long challengeId = challengeRepository.findByInviteCode(normalize(inviteCode))
                .map(Challenge::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));
        Challenge c = challengeRepository.findForUpdate(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_FOUND));
        addParticipant(c, userId);
        return challengeId;
    }

    /** 초대 링크가 새어 나갔을 때 개설자가 코드를 바꾼다. 이전 링크는 더 이상 열리지 않는다. */
    @Transactional
    public void regenerateInviteCode(Long challengeId, Long userId) {
        Challenge c = challengeRepository.findForUpdate(challengeId)
                .filter(found -> !found.isPrivate() || isMember(found, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (!c.isHost(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        c.changeInviteCode(newInviteCode());
    }

    /**
     * 개설자만, 시작일 전날까지 삭제할 수 있다. 참가 기록과 오픈채팅도 함께 지운다.
     * (시작한 뒤에는 인증·정산 기록이 생기므로 막는다. 포인트 챌린지는 참여가 열릴 때 '예치 포인트 환급'을 여기에 더한다)
     */
    @Transactional
    public void delete(Long challengeId, Long userId) {
        Challenge c = challengeRepository.findForUpdate(challengeId)
                .filter(found -> !found.isPrivate() || isMember(found, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (!c.isHost(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!c.canLeave(today())) {
            throw new BusinessException(ErrorCode.CHALLENGE_CANNOT_DELETE);
        }
        chatMessageRepository.deleteByChallengeId(challengeId);
        participantRepository.deleteByChallengeId(challengeId);
        challengeRepository.delete(c);
    }

    /**
     * 개설자·참가자(참여 취소 제외)만 통과. 채팅처럼 멤버 전용 기능이 쓴다.
     * 아니면 챌린지가 없는 것처럼 404 (비공개 챌린지가 있다는 것도 알리지 않게).
     */
    @Transactional(readOnly = true)
    public void requireMember(Long challengeId, Long userId) {
        challengeRepository.findById(challengeId)
                .filter(c -> isMember(c, userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
    }

    /**
     * 참여 공통. 챌린지 행을 잠근 채로 정원을 확인하고 참가자 수를 올린다. (동시에 여러 명이 눌러도 정원 초과 없음)
     * 포인트 챌린지는 지갑(충전/보상 포인트 분리)이 만들어진 뒤 연다.
     */
    private void addParticipant(Challenge c, Long userId) {
        if (!c.isRecruiting(today())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_RECRUITING);
        }
        if (c.getMode() == ChallengeMode.BET) {
            throw new BusinessException(ErrorCode.POINT_CHALLENGE_NOT_READY);
        }

        ChallengeParticipant existing = participantRepository.findByChallengeIdAndUserId(c.getId(), userId)
                .orElse(null);
        if (existing != null && existing.isActive()) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        if (c.isFull()) {
            throw new BusinessException(ErrorCode.CHALLENGE_FULL);
        }

        if (existing != null) {
            existing.rejoin(0);
        } else {
            participantRepository.save(ChallengeParticipant.join(c.getId(), userId, 0));
        }
        c.addParticipant();
        try {
            // 잠금으로 이미 막히지만, (challenge_id, user_id) 유니크 제약이 마지막 안전장치다.
            participantRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
    }

    private ChallengeDetailResponse toDetail(Challenge c, Long viewerId) {
        String hostNickname = userRepository.findById(c.getHostId()).map(User::getNickname).orElse("알 수 없음");
        boolean joined = viewerId != null && participantRepository.findByChallengeIdAndUserId(c.getId(), viewerId)
                .filter(ChallengeParticipant::isActive)
                .isPresent();
        List<ParticipantResponse> participants = participantRepository.findParticipants(c.getId());
        return ChallengeDetailResponse.of(c, today(), hostNickname, joined, c.isHost(viewerId),
                isMember(c, viewerId), participants);
    }

    /** 개설자이거나, 참여를 취소하지 않은 참가자. 초대 링크를 볼 수 있고 비공개 챌린지를 볼 수 있는 사람이다. */
    private boolean isMember(Challenge c, Long userId) {
        if (userId == null) {
            return false;
        }
        return c.isHost(userId) || participantRepository.findByChallengeIdAndUserId(c.getId(), userId)
                .filter(p -> p.getStatus() != ParticipantStatus.LEFT)
                .isPresent();
    }

    /** 겹치지 않는 초대 코드. 겹칠 확률은 매우 낮고, 동시에 같은 코드가 나와도 유니크 제약이 막는다. */
    private String newInviteCode() {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String code = inviteCodeGenerator.next();
            if (!challengeRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드를 만들지 못했습니다.");
    }

    /** 링크를 옮겨 적다 소문자·공백이 섞여도 같은 코드로 본다. */
    private static String normalize(String inviteCode) {
        return inviteCode == null ? "" : inviteCode.strip().toUpperCase(Locale.ROOT);
    }

    /** 시작 전 참여 취소. 시작한 뒤에 그만두는 것(포기 = 실패 처리)은 인증 기능과 함께 만든다. */
    @Transactional
    public void leave(Long challengeId, Long userId) {
        Challenge c = challengeRepository.findForUpdate(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        ChallengeParticipant p = participantRepository.findByChallengeIdAndUserId(challengeId, userId)
                .filter(ChallengeParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_JOINED));
        if (!c.canLeave(today())) {
            throw new BusinessException(ErrorCode.CHALLENGE_ALREADY_STARTED);
        }
        p.leave();
        c.removeParticipant();
    }

    private User requirePhoneVerified(Long userId) {
        User user = userService.getActive(userId);
        if (!user.hasPhone()) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }
        return user;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 검색어를 LIKE 패턴으로. %, _ 는 글자 그대로 찾도록 ! 로 이스케이프한다. 비어 있으면 null(조건 없음). */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
