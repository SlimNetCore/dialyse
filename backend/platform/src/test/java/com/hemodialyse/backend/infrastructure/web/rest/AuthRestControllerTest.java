package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.auth.AuthService;
import com.hemodialyse.backend.application.auth.ChangementMotDePasseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        AuthRestController controller = new AuthRestController(authService, mock(ChangementMotDePasseService.class));
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

        when(authService.login(isNull(), eq(centerId), eq("admin"), eq("secret"), isNull())).thenReturn(loginResult);
        when(authService.issueRefreshToken(eq(userId), eq(centerId), isNull())).thenReturn("refresh-token");

        var response = controller.login(new AuthRestController.LoginRequest(null, centerId, "admin", "secret"));

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
    void login_should_tell_the_client_when_a_temporary_password_must_be_replaced() {
        AuthService authService = mock(AuthService.class);
        ChangementMotDePasseService motsDePasse = mock(ChangementMotDePasseService.class);
        AuthRestController controller = new AuthRestController(authService, motsDePasse);
        configureCookieFields(controller, false, "Lax");
        UUID centerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.randomUUID();
        when(authService.login(isNull(), eq(centerId), eq("sara"), eq("tmp"), isNull())).thenReturn(
                new AuthService.LoginResult("t", "sara", "Sara", userId, centerId, "ANNABA 1", List.of("ROLE_INFIRMIER")));
        when(authService.issueRefreshToken(eq(userId), eq(centerId), isNull())).thenReturn("r");
        when(motsDePasse.doitChanger(userId)).thenReturn(true);

        var response = controller.login(new AuthRestController.LoginRequest(null, centerId, "sara", "tmp"));

        assertTrue(response.getBody().mustChangePassword());
    }

    @Test
    void change_password_should_use_the_authenticated_user_and_never_a_request_field() {
        ChangementMotDePasseService motsDePasse = mock(ChangementMotDePasseService.class);
        AuthRestController controller = new AuthRestController(mock(AuthService.class), motsDePasse);
        UUID userId = UUID.randomUUID();
        var principal = com.hemodialyse.backend.infrastructure.security.UserPrincipal.create(userId.toString(),
                UUID.randomUUID().toString(), "sara", "x", List.of("INFIRMIER"), true);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        var response = controller.changePassword(new AuthRestController.ChangePasswordRequest("ancien", "Nouveau-mdp-2026"), auth);

        assertEquals(204, response.getStatusCode().value());
        org.mockito.Mockito.verify(motsDePasse).changer(userId, "ancien", "Nouveau-mdp-2026");
        assertEquals(401, controller.changePassword(new AuthRestController.ChangePasswordRequest("a", "b"), null)
                .getStatusCode().value());
    }

    @Test
    void login_should_emit_cross_site_secure_cookies_when_configured_for_https() {
        AuthService authService = mock(AuthService.class);
        AuthRestController controller = new AuthRestController(authService, mock(ChangementMotDePasseService.class));
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

        when(authService.login(isNull(), eq(centerId), eq("admin"), eq("secret"), isNull())).thenReturn(loginResult);
        when(authService.issueRefreshToken(eq(userId), eq(centerId), isNull())).thenReturn("refresh-token");

        var response = controller.login(new AuthRestController.LoginRequest(null, centerId, "admin", "secret"));

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

