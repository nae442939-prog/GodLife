package com.godlife.backend.follow;

import com.godlife.backend.block.UserBlockRepository;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.user.UserRepository;
import com.godlife.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 팔로우: 요청·수락 없이 한쪽이 누르면 된다. 서로 팔로우하면 맞팔로우(1:1 메시지를 열 조건).
 * 차단한 사이(어느 쪽이든)는 팔로우할 수 없고, 차단하면 서로의 팔로우가 끊긴다.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    /** 팔로우 도배 방지: 한 사람이 1시간에 팔로우할 수 있는 횟수 */
    private static final int FOLLOW_LIMIT = 100;
    private static final Duration FOLLOW_WINDOW = Duration.ofHours(1);

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository blockRepository;
    private final RequestThrottle throttle;

    @Transactional
    public void follow(Long me, Long target) {
        if (me.equals(target)) {
            throw new BusinessException(ErrorCode.CANNOT_FOLLOW, "나 자신은 팔로우할 수 없어요.");
        }
        userRepository.findById(target)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (blockRepository.existsByBlockerIdAndBlockedId(me, target)
                || blockRepository.existsByBlockerIdAndBlockedId(target, me)) {
            throw new BusinessException(ErrorCode.CANNOT_FOLLOW);
        }
        throttle.check("follow:" + me, FOLLOW_LIMIT, FOLLOW_WINDOW);
        followRepository.follow(me, target);
    }

    @Transactional
    public void unfollow(Long me, Long target) {
        followRepository.unfollow(me, target);
    }
}
