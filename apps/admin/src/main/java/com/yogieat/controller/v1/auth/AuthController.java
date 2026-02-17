package com.yogieat.controller.v1.auth;

import com.yogieat.controller.v1.auth.request.LoginRequest;
import com.yogieat.controller.v1.auth.response.LoginResponse;
import com.yogieat.controller.v1.auth.response.LogoutResponse;
import com.yogieat.service.AuthFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFacade authFacade;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return LoginResponse.from(authFacade.login(request.email(), request.password()));
    }

    @PostMapping("/logout")
    public LogoutResponse logout() {
        return LogoutResponse.from(authFacade.logout());
    }
}
