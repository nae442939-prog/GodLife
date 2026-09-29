package com.godlife.backend.phone;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /** 가장 최근에 보낸 인증번호. SELECT ... FOR UPDATE 로 잠가 동시 시도에서도 횟수 제한이 지켜지게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerification> findFirstByPhoneHashOrderByIdDesc(String phoneHash);

    /** 증표는 한 번만 쓸 수 있으므로 잠가서 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PhoneVerification> findByProofHash(String proofHash);

    long countByPhoneHashAndExpiresAtAfter(String phoneHash, LocalDateTime after);
}
