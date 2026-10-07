package com.godlife.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<User> findByEmail(String email);

    boolean existsByPhoneHash(String phoneHash);

    Optional<User> findByPhoneHash(String phoneHash);
}
