package com.godlife.backend.challenge;

import com.godlife.backend.challenge.dto.ChallengeCreateRequest;
import com.godlife.backend.challenge.dto.ChallengeDetailResponse;
import com.godlife.backend.challenge.dto.ChallengeSummaryResponse;
import com.godlife.backend.challenge.dto.PageResponse;
import com.godlife.backend.challenge.dto.ParticipantResponse;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.user.User;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    public static final int PAGE_SIZE = 12;

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Clock clock;

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

        Challenge challenge = Challenge.create(host.getId(), category, req.title().strip(),
                req.description().strip(), req.mode(), req.startDate(), req.endDate(), req.frequencyType(),
                req.weeklyCount(), req.entryFee() == null ? 0 : req.entryFee(), req.maxParticipants(),
                req.verifyFrom(), req.verifyUntil(), req.partialRefund());
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

    /** viewerId 는 비로그인이면 null */
    @Transactional(readOnly = true)
    public ChallengeDetailResponse detail(Long challengeId, Long viewerId) {
        Challenge c = challengeRepository.findWithCategory(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        String hostNickname = userRepository.findById(c.getHostId()).map(User::getNickname).orElse("알 수 없음");
        boolean joined = viewerId != null && participantRepository.findByChallengeIdAndUserId(challengeId, viewerId)
                .filter(ChallengeParticipant::isActive)
                .isPresent();
        List<ParticipantResponse> participants = participantRepository.findParticipants(challengeId);
        return ChallengeDetailResponse.of(c, today(), hostNickname, joined, c.getHostId().equals(viewerId),
                participants);
    }

    /**
     * 참여. 챌린지 행을 잠근 채로 정원을 확인하고 참가자 수를 올린다. (동시에 여러 명이 눌러도 정원 초과 없음)
     * 포인트 챌린지는 지갑(충전/보상 포인트 분리)이 만들어진 뒤 연다.
     */
    @Transactional
    public void join(Long challengeId, Long userId) {
        requirePhoneVerified(userId);
        Challenge c = challengeRepository.findForUpdate(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (!c.isRecruiting(today())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_RECRUITING);
        }
        if (c.getMode() == ChallengeMode.BET) {
            throw new BusinessException(ErrorCode.POINT_CHALLENGE_NOT_READY);
        }

        ChallengeParticipant existing = participantRepository.findByChallengeIdAndUserId(challengeId, userId)
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
            participantRepository.save(ChallengeParticipant.join(challengeId, userId, 0));
        }
        c.addParticipant();
        try {
            // 잠금으로 이미 막히지만, (challenge_id, user_id) 유니크 제약이 마지막 안전장치다.
            participantRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
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
