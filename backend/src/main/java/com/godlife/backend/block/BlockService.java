package com.godlife.backend.block;

import com.godlife.backend.block.dto.BlockedUserResponse;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.follow.FollowRepository;
import com.godlife.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 차단은 나에게만 적용된다. 차단한 사람의 채팅이 내 화면에서 안 보이고, 상대에게는 알리지 않는다. */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    /** 이미 차단했으면 그대로 둔다(멱등). */
    @Transactional
    public void block(Long blockerId, Long targetId) {
        if (blockerId.equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK);
        }
        if (!userRepository.existsById(targetId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "없는 사용자예요.");
        }
        // 차단하면 서로의 팔로우도 끊는다 (맞팔로우 1:1 메시지도 함께 막힌다)
        followRepository.removeBoth(blockerId, targetId);
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
            return;
        }
        try {
            blockRepository.saveAndFlush(UserBlock.of(blockerId, targetId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 눌러도 유니크 제약이 하나만 남긴다.
        }
    }

    @Transactional
    public void unblock(Long blockerId, Long targetId) {
        blockRepository.deleteBlock(blockerId, targetId);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> blocked(Long blockerId) {
        return blockRepository.findBlocked(blockerId);
    }
}
