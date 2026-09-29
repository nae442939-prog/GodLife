package com.godlife.backend.user;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.phone.PhoneVerification;
import com.godlife.backend.phone.PhoneVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PhoneVerificationService phoneVerificationService;

    @Transactional(readOnly = true)
    public User getActive(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 로그인한 회원의 휴대폰 번호 등록/변경. 소셜 가입자는 이걸 마쳐야 서비스를 쓸 수 있다.
     * 다른 계정이 쓰는 번호면 거절한다. (계정당 1번호 = 다중 계정 방지)
     */
    @Transactional
    public User registerPhone(Long userId, String phoneProof) {
        User user = getActive(userId);
        PhoneVerification phone = phoneVerificationService.consumeProof(phoneProof);
        userRepository.findByPhoneHash(phone.getPhoneHash())
                .filter(owner -> !owner.getId().equals(userId))
                .ifPresent(owner -> {
                    throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
                });
        user.registerPhone(phone.getPhoneHash());
        phone.linkUser(userId);
        try {
            // 동시에 같은 번호를 두 계정에 등록하면 users.phone_hash 유니크 제약이 최종적으로 막는다.
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        return user;
    }
}
