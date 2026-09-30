package com.godlife.backend.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    /** 지갑이 없으면 만든다. 동시에 두 번 불려도 user_id 유니크라 하나만 생긴다. */
    @Modifying
    @Query(value = "INSERT IGNORE INTO wallets (user_id) VALUES (:userId)", nativeQuery = true)
    void createIfMissing(@Param("userId") Long userId);
}
