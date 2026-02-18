package com.yogieat.controller.v1.admin;

import com.yogieat.config.web.AdminUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminTestController {

    @GetMapping("/test")
    public Map<String, Object> test(AdminUser adminUser) {
        return Map.of(
                "message", "admin authenticated",
                "adminId", adminUser.id(),
                "loginId", adminUser.loginId()
        );
    }
}
