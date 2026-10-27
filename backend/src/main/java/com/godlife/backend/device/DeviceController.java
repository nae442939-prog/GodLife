package com.godlife.backend.device;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.demo.DemoAccounts;
import com.godlife.backend.demo.DemoMask;
import com.godlife.backend.device.DeviceService.Group;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자: 여러 계정이 함께 쓴 기기 · IP (/api/admin/** 는 관리자만 들어올 수 있다) */
@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DemoAccounts demoAccounts;

    @GetMapping("/api/admin/devices")
    public List<Group> suspicious(@AuthenticationPrincipal AuthUser authUser) {
        List<Group> groups = deviceService.suspicious();
        // 테스트용 관리자에게는 IP 뒷자리와 이메일을 가린다
        return demoAccounts.isDemoAdmin(authUser.id()) ? groups.stream().map(DemoMask::device).toList() : groups;
    }
}
