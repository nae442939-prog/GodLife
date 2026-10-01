package com.godlife.backend.user;

import com.godlife.backend.auth.RefreshTokenRepository;
import com.godlife.backend.auth.social.SocialAccountRepository;
import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.common.ratelimit.RequestThrottle;
import com.godlife.backend.common.upload.ImageStore;
import com.godlife.backend.user.dto.ProfileEditDtos.AccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 설정 화면의 내 정보 고치기: 닉네임 · 자기소개 · 프로필 사진 · 비밀번호.
 * - 프로필 사진은 랭킹·프로필처럼 비로그인도 보는 화면에 나오므로 누구나 받을 수 있는 주소로 준다.
 *   (다시 그려 저장해 촬영 정보를 지우고, 파일 이름은 추측할 수 없는 값)
 * - 비밀번호를 바꾸면 모든 기기에서 로그아웃된다. 소셜로만 가입한 계정은 비밀번호가 없어 바꿀 수 없다.
 */
@Service
@RequiredArgsConstructor
public class ProfileEditService {

    /** 올린 프로필 사진의 주소 앞부분. 뒤에 '{회원 id}/{파일 이름}' 이 붙는다 */
    public static final String IMAGE_URL_PREFIX = "/api/profile-images/";
    private static final String IMAGE_KEY_PREFIX = "profile/";
    private static final Pattern IMAGE_NAME = Pattern.compile("[0-9a-f-]{36}\\.jpg");
    private static final int PASSWORD_TRIES = 10;
    private static final Duration PASSWORD_WINDOW = Duration.ofMinutes(10);

    private final UserService userService;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageStore imageStore;
    private final RequestThrottle throttle;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AccountResponse account(Long userId) {
        User user = userService.getActive(userId);
        return new AccountResponse(user.getEmail(), user.getPasswordHash() != null,
                socialAccountRepository.findByUserId(userId).stream().map(a -> a.getProvider().name()).sorted().toList());
    }

    /** 닉네임 · 자기소개 고치기. 다른 회원이 쓰는 닉네임이면 거절한다. */
    @Transactional
    public User updateProfile(Long userId, String nickname, String bio) {
        User user = userService.getActive(userId);
        if (!user.getNickname().equals(nickname) && userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        String text = bio == null ? "" : bio.strip();
        user.updateProfile(nickname, text.isEmpty() ? null : text);
        try {
            // 동시에 같은 닉네임으로 바꾸면 users.nickname 유니크 제약이 최종적으로 막는다.
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return user;
    }

    /** 프로필 사진 바꾸기 (JPG·PNG). 전에 올린 사진 파일은 지운다. */
    @Transactional
    public User changeImage(Long userId, MultipartFile file) {
        User user = userService.getActive(userId);
        String old = user.getProfileImageUrl();
        String key = imageStore.storeProfileImage(userId, file);
        user.changeProfileImage(IMAGE_URL_PREFIX + key.substring(IMAGE_KEY_PREFIX.length()));
        deleteUploaded(old);
        return user;
    }

    /** 프로필 사진 빼기 (기본 아이콘으로) */
    @Transactional
    public User removeImage(Long userId) {
        User user = userService.getActive(userId);
        String old = user.getProfileImageUrl();
        user.changeProfileImage(null);
        deleteUploaded(old);
        return user;
    }

    /** 올린 프로필 사진 파일 (누구나). 주소 모양이 다르거나 파일이 없으면 404 */
    public Path imageFile(Long ownerId, String name) {
        if (!IMAGE_NAME.matcher(name).matches()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사진을 찾을 수 없어요.");
        }
        Path path = imageStore.resolve(IMAGE_KEY_PREFIX + ownerId + "/" + name);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사진을 찾을 수 없어요.");
        }
        return path;
    }

    /** 비밀번호 바꾸기: 현재 비밀번호를 확인하고, 바꾼 뒤에는 모든 기기에서 로그아웃시킨다. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userService.getActive(userId);
        if (user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "소셜 로그인으로 가입한 계정은 비밀번호가 없어요.");
        }
        // 로그인된 기기를 잠깐 빌린 사람이 현재 비밀번호를 마구 넣어 보지 못하게
        throttle.check("password-change:" + userId, PASSWORD_TRIES, PASSWORD_WINDOW);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "현재 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllByUserId(userId, LocalDateTime.now(clock));
    }

    /** 우리 서버에 올린 사진만 지운다 (소셜 계정의 사진 주소는 남의 서버 것이라 건드리지 않는다) */
    private void deleteUploaded(String url) {
        if (url != null && url.startsWith(IMAGE_URL_PREFIX)) {
            imageStore.delete(IMAGE_KEY_PREFIX + url.substring(IMAGE_URL_PREFIX.length()));
        }
    }
}
