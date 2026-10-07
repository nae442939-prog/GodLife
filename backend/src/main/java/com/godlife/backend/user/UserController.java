package com.godlife.backend.user;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.user.dto.PhoneRegisterRequest;
import com.godlife.backend.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return userService.toResponse(userService.getActive(authUser.id()));
    }

    @PostMapping("/me/phone")
    public UserResponse registerPhone(@AuthenticationPrincipal AuthUser authUser,
                                      @Valid @RequestBody PhoneRegisterRequest request) {
        return userService.toResponse(userService.registerPhone(authUser.id(), request.phoneProof()));
    }
}
