package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRestControllerTest {

    private static void configureCookieFields(AuthRestController controller, boolean secure, String sameSite) {
        ReflectionTestUtils.setField(controller, "authCookieName", "HEMO_AUTH");
        ReflectionTestUtils.setField(controller, "refreshCookieName", "HEMO_REFRESH");
        ReflectionTestUtils.setField(controller, "authCookieSecure", secure);
        ReflectionTestUtils.setField(controller, "authCookieSameSite", sameSite);
        ReflectionTestUtils.setField(controller, "authCookiePath", "/");
        ReflectionTestUtils.setField(controller, "refreshCookiePath", "/api/v1/auth/refresh");
        ReflectionTestUtils.setField(controller, "authCookieDomain", "");
        ReflectionTestUtils.setField(controller, "authCookieMaxAgeSec", 28800L);
        ReflectionTestUtils.setField(controller, "refreshCookieMaxAgeSec", 604800L);
    }

    @Test
    void login_should_emit_localhost_friendly_cookies_by_default() {
        AuthService authService = mock(AuthService.class);
        AuthRestController controller = new AuthRestController(authService);
        configureCookieFields(controller, false, "Lax");

        UUID centerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.randomUUID();
        AuthService.LoginResult loginResult = new AuthService.LoginResult(
                "access-token",
                "admin",
                "Admin Test",
                userId,
                centerId,
                "ANNABA 1",
                List.of("ROLE_ADMIN")
        );

        when(authService.login(eq(centerId), eq("admin"), eq("secret"))).thenReturn(loginResult);
        when(authService.issueRefreshToken(eq(userId), eq(centerId))).thenReturn("refresh-token");

        var response = controller.login(new AuthRestController.LoginRequest(centerId, "admin", "secret"));

        assertEquals(200, response.getStatusCode().value());
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());

        String accessCookie = cookies.getFirst();
        assertTrue(accessCookie.contains("HEMO_AUTH=access-token"));
        assertTrue(accessCookie.contains("HttpOnly"));
        assertTrue(accessCookie.contains("SameSite=Lax"));
        assertFalse(accessCookie.contains("Secure"));

        String refreshCookie = cookies.get(1);
        assertTrue(refreshCookie.contains("HEMO_REFRESH=refresh-token"));
        assertTrue(refreshCookie.contains("Path=/api/v1/auth/refresh"));
        assertTrue(refreshCookie.contains("SameSite=Lax"));
        assertFalse(refreshCookie.contains("Secure"));
    }

    @Test
    void login_should_emit_cross_site_secure_cookies_when_configured_for_https() {
        AuthService authService = mock(AuthService.class);
        AuthRestController controller = new AuthRestController(authService);
        configureCookieFields(controller, true, "None");

        UUID centerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.randomUUID();
        AuthService.LoginResult loginResult = new AuthService.LoginResult(
                "access-token",
                "admin",
                "Admin Test",
                userId,
                centerId,
                "ANNABA 1",
                List.of("ROLE_ADMIN")
        );

        when(authService.login(eq(centerId), eq("admin"), eq("secret"))).thenReturn(loginResult);
        when(authService.issueRefreshToken(eq(userId), eq(centerId))).thenReturn("refresh-token");

        var response = controller.login(new AuthRestController.LoginRequest(centerId, "admin", "secret"));

        assertEquals(200, response.getStatusCode().value());
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());

        String accessCookie = cookies.getFirst();
        assertTrue(accessCookie.contains("HEMO_AUTH=access-token"));
        assertTrue(accessCookie.contains("HttpOnly"));
        assertTrue(accessCookie.contains("SameSite=None"));
        assertTrue(accessCookie.contains("Secure"));

        String refreshCookie = cookies.get(1);
        assertTrue(refreshCookie.contains("HEMO_REFRESH=refresh-token"));
        assertTrue(refreshCookie.contains("Path=/api/v1/auth/refresh"));
        assertTrue(refreshCookie.contains("SameSite=None"));
        assertTrue(refreshCookie.contains("Secure"));
    }
}

