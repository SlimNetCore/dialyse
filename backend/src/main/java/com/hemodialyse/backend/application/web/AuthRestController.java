package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.auth.AuthService;
import com.hemodialyse.backend.application.auth.AuthService.LoginResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthRestController {

    private final AuthService authService;

    @Value("${app.auth.cookie.name:HEMO_AUTH}")
    private String authCookieName;

    @Value("${app.auth.cookie.secure:true}")
    private boolean authCookieSecure;

    @Value("${app.auth.cookie.same-site:Strict}")
    private String authCookieSameSite;

    @Value("${app.auth.cookie.path:/}")
    private String authCookiePath;

    @Value("${app.auth.cookie.domain:}")
    private String authCookieDomain;

    @Value("${app.jwt.expiration:28800}")
    private long authCookieMaxAgeSec;

    public AuthRestController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResult result = authService.login(request.centerId(), request.username(), request.password());
        ResponseCookie cookie = buildAuthCookie(result.token(), authCookieMaxAgeSec);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(
                        result.username(),
                        result.fullName(),
                        result.userId(),
                        result.centerId(),
                        result.centerName(),
                        result.roles()
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout() {
        ResponseCookie expiredCookie = buildAuthCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(new LogoutResponse(true));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(401).build();
        }
        if (principal.getCenterId() == null || principal.getCenterId().isBlank()) {
            return ResponseEntity.status(401).build();
        }

        UUID centerId = UUID.fromString(principal.getCenterId());
        UUID userId = UUID.fromString(principal.getId());
        List<String> roles = principal.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        LoginResult result = authService.rebuildSession(centerId, principal.getUsername(), userId, roles);

        return ResponseEntity.ok(new LoginResponse(
                result.username(),
                result.fullName(),
                result.userId(),
                result.centerId(),
                result.centerName(),
                result.roles()
        ));
    }

    private ResponseCookie buildAuthCookie(String tokenValue, long maxAgeSec) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(authCookieName, tokenValue)
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite(authCookieSameSite)
                .path(authCookiePath)
                .maxAge(maxAgeSec);

        if (authCookieDomain != null && !authCookieDomain.isBlank()) {
            builder.domain(authCookieDomain);
        }

        return builder.build();
    }

    public record LoginRequest(@NotNull UUID centerId, @NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String username, String fullName, UUID userId, UUID centerId, String centerName,
                                List<String> roles) {
    }

    public record LogoutResponse(boolean loggedOut) {
    }
}

