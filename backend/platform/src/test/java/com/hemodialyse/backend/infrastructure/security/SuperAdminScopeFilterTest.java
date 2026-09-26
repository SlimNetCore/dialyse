package com.hemodialyse.backend.infrastructure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SuperAdminScopeFilterTest {

    private final SuperAdminScopeFilter filter = new SuperAdminScopeFilter();

    private static void authenticateAs(String role) {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "u", "", List.of(role), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String method, String path, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void the_owner_can_reach_societes_licenses_auth_and_system() throws Exception {
        authenticateAs("SUPERADMIN");
        for (String path : List.of("/api/v1/societes", "/api/v1/societes/abc/centres", "/api/v1/licenses",
                "/api/v1/licenses/issue-societe", "/api/v1/auth/me", "/api/v1/auth/centres", "/api/v1/system/ping")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = call("GET", path, chain);
            assertEquals(200, response.getStatus(), path);
            verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void the_owner_cannot_reach_any_centre_data() throws Exception {
        authenticateAs("SUPERADMIN");
        for (String path : List.of("/api/v1/patients", "/api/v1/patients/123/dossier-medical", "/api/v1/seances",
                "/api/v1/stock/articles", "/api/v1/facturation/preview", "/api/v1/reglements", "/api/v1/users",
                "/api/v1/roles", "/api/v1/documents/print", "/api/v1/dashboard/stats", "/api/v1/referentials/medecins")) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = call("GET", path, chain);
            assertEquals(403, response.getStatus(), path);
            assertTrue(response.getContentAsString().contains("SUPERADMIN_SCOPE"), path);
            verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void other_roles_are_not_affected() throws Exception {
        for (String role : List.of("ADMIN", "MEDECIN", "DIRECTION")) {
            authenticateAs(role);
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletResponse response = call("GET", "/api/v1/patients", chain);
            assertEquals(200, response.getStatus(), role);
            verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    void non_api_paths_and_preflight_requests_are_ignored() throws Exception {
        authenticateAs("SUPERADMIN");
        FilterChain chain = mock(FilterChain.class);
        assertEquals(200, call("GET", "/actuator/health", chain).getStatus());
        assertEquals(200, call("OPTIONS", "/api/v1/patients", chain).getStatus());
    }

    @Test
    void anonymous_requests_are_left_to_the_security_chain() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertEquals(200, call("GET", "/api/v1/patients", chain).getStatus());
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
