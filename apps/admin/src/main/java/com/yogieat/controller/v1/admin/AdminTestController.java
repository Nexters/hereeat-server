package com.yogieat.controller.v1.admin;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminTestController {

    @GetMapping("/test")
    public Map<String, Object> test(Authentication authentication) {
        return Map.of(
                "message", "admin authenticated",
                "adminId", authentication.getPrincipal()
        );
    }

    @GetMapping("/admins/test")
    public Map<String, Object> superAdminTest(Authentication authentication) {
        return Map.of(
                "message", "super admin authenticated",
                "adminId", authentication.getPrincipal()
        );
    }
}
