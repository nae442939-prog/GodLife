package com.godlife.backend.demo;

import com.godlife.backend.common.error.BusinessException;
import com.godlife.backend.common.error.ErrorCode;
import com.godlife.backend.user.Role;
import com.godlife.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 회사에서 바로 로그인해 둘러볼 테스트 계정 (일반 회원 하나, 보기만 되는 관리자 하나).
 * 아이디 · 비밀번호는 backend/.env 의 DEMO_USER_* / DEMO_ADMIN_* 에서 읽는다. 비워 두면 그 계정은 만들지 않는다.
 * 여러 회사가 같이 쓰므로 비밀번호 변경 · 찾기, 탈퇴, 휴대폰 등록은 막고(requireNotDemo), 매일 새벽 새로 만든다(DemoAccountReset).
 * 계정은 이메일로 알아본다: 초기화하면 예전 계정의 이메일이 바뀌어 더는 테스트 계정이 아니다.
 */
@Component
public class DemoAccounts {

    public static final String USER_NICKNAME = "갓생체험";
    public static final String ADMIN_NICKNAME = "테스트관리자";

    /** 테스트 계정 하나 (password 는 원문이라 로그에 찍지 않는다) */
    public record Account(String email, String password, String nickname, Role role) {
        @Override
        public String toString() {
            return "Account[email=" + email + ", password=***, nickname=" + nickname + ", role=" + role + "]";
        }
    }

    private final UserRepository userRepository;
    private final List<Account> accounts = new ArrayList<>();
    private final String adminEmail;

    public DemoAccounts(UserRepository userRepository,
                        @Value("${app.demo.user-email:}") String userEmail,
                        @Value("${app.demo.user-password:}") String userPassword,
                        @Value("${app.demo.admin-email:}") String adminEmail,
                        @Value("${app.demo.admin-password:}") String adminPassword) {
        this.userRepository = userRepository;
        add(userEmail, userPassword, USER_NICKNAME, Role.USER);
        add(adminEmail, adminPassword, ADMIN_NICKNAME, Role.ADMIN);
        this.adminEmail = normalize(adminEmail);
    }

    public List<Account> accounts() {
        return List.copyOf(accounts);
    }

    /** 지금 테스트 계정인 회원인지 */
    public boolean isDemo(Long userId) {
        return userId != null && !accounts.isEmpty() && userRepository.findById(userId)
                .map(u -> accounts.stream().anyMatch(a -> a.email().equals(u.getEmail())))
                .orElse(false);
    }

    public boolean isDemoEmail(String email) {
        String normalized = normalize(email);
        return accounts.stream().anyMatch(a -> a.email().equals(normalized));
    }

    /** 보기만 되는 테스트용 관리자인지 */
    public boolean isDemoAdmin(Long userId) {
        return userId != null && !adminEmail.isEmpty() && accounts.stream().anyMatch(a -> a.role() == Role.ADMIN)
                && userRepository.findById(userId).map(u -> adminEmail.equals(u.getEmail())).orElse(false);
    }

    /** 테스트 계정이면 막는다 (비밀번호 · 탈퇴 · 휴대폰처럼 다음 사람이 못 쓰게 될 수 있는 것) */
    public void requireNotDemo(Long userId) {
        if (isDemo(userId)) {
            throw new BusinessException(ErrorCode.DEMO_ACCOUNT_LOCKED);
        }
    }

    private void add(String email, String password, String nickname, Role role) {
        String normalized = normalize(email);
        if (!normalized.isEmpty() && password != null && !password.isBlank()) {
            accounts.add(new Account(normalized, password, nickname, role));
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
