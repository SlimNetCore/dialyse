package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.auth.AuthService;
import com.hemodialyse.backend.application.auth.AuthService.LoginResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthRestController {

    private final AuthService authService;

    public AuthRestController(AuthService authService) { this.authService = authService; }

    record LoginRequest(@NotNull UUID centerId, @NotBlank String username, @NotBlank String password) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.centerId(), request.username(), request.password()));
    }
}

