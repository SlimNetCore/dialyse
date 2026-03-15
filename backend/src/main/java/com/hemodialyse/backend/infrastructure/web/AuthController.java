package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.application.auth.AuthService;
import com.hemodialyse.backend.application.auth.AuthService.LoginResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    record LoginRequest(
        @NotNull UUID centerId,
        @NotBlank String username,
        @NotBlank String password
    ) {
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@RequestBody @Valid LoginRequest request) {
        LoginResult result = authService.login(request.centerId(), request.username(), request.password());
        return ResponseEntity.ok(result);
    }
}

