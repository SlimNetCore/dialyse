package com.hemodialyse.backend.application.web;

import com.hemodialyse.backend.application.auth.AuthService;
import com.hemodialyse.backend.application.auth.AuthService.LoginResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

    @Value("${app.auth.cookie.refresh-name:HEMO_REFRESH}")
    private String refreshCookieName;

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

    @Value("${app.jwt.refresh-expiration:604800}")
    private long refreshCookieMaxAgeSec;

    public AuthRestController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResult result = authService.login(request.centerId(), request.username(), request.password());
        String accessToken = result.token();
        String refreshToken = authService.issueRefreshToken(result.userId(), result.centerId());

        ResponseCookie accessCookie = buildCookie(authCookieName, accessToken, authCookieMaxAgeSec);
        ResponseCookie refreshCookie = buildCookie(refreshCookieName, refreshToken, refreshCookieMaxAgeSec);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
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
    public ResponseEntity<LogoutResponse> logout(HttpServletRequest request) {
        String refreshToken = readCookieValue(request, refreshCookieName);
        authService.revokeRefreshToken(refreshToken);
        ResponseCookie expiredAccess = buildCookie(authCookieName, "", 0);
        ResponseCookie expiredRefresh = buildCookie(refreshCookieName, "", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredAccess.toString(), expiredRefresh.toString())
                .body(new LogoutResponse(true));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request) {
        String refreshToken = readCookieValue(request, refreshCookieName);
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body(new RefreshResponse(false));
        }

        try {
            var rotation = authService.rotateFromRefreshToken(refreshToken);
            String newAccessToken = rotation.accessToken();
            String newRefreshToken = rotation.refreshToken();

            ResponseCookie accessCookie = buildCookie(authCookieName, newAccessToken, authCookieMaxAgeSec);
            ResponseCookie refreshCookie = buildCookie(refreshCookieName, newRefreshToken, refreshCookieMaxAgeSec);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                    .body(new RefreshResponse(true));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(new RefreshResponse(false));
        }
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
        LoginResult result = authService.rebuildSession(centerId, userId);

        return ResponseEntity.ok(new LoginResponse(
                result.username(),
                result.fullName(),
                result.userId(),
                result.centerId(),
                result.centerName(),
                result.roles()
        ));
    }

    private ResponseCookie buildCookie(String cookieName, String tokenValue, long maxAgeSec) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, tokenValue)
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

    private String readCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record LoginRequest(@NotNull UUID centerId, @NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String username, String fullName, UUID userId, UUID centerId, String centerName,
                                List<String> roles) {
    }

    public record LogoutResponse(boolean loggedOut) {
    }

    public record RefreshResponse(boolean refreshed) {
    }
}

