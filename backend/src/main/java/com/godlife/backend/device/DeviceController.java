package com.godlife.backend.device;

import com.godlife.backend.device.DeviceService.Group;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자: 여러 계정이 함께 쓴 기기 · IP (/api/admin/** 는 관리자만 들어올 수 있다) */
@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/api/admin/devices")
    public List<Group> suspicious() {
        return deviceService.suspicious();
    }
}
