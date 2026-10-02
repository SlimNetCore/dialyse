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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RoleScopeFilterTest {

    private static final List<String> CENTRE_DATA = List.of("/api/v1/patients", "/api/v1/patients/123/dossier-medical",
            "/api/v1/seances", "/api/v1/stock/articles", "/api/v1/facturation/preview", "/api/v1/reglements",
            "/api/v1/users", "/api/v1/roles", "/api/v1/documents/print", "/api/v1/dashboard/stats",
            "/api/v1/referentials/medecins");

    private final RoleScopeFilter filter = new RoleScopeFilter();

    private static void authenticateAs(String... roles) {
        UserPrincipal principal = UserPrincipal.create(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "u", "", List.of(roles), true);
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

    private void assertAllowed(String path) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertEquals(200, call("GET", path, chain).getStatus(), path);
        verify(chain).doFilter(any(), any());
    }

    private void assertForbidden(String path) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = call("GET", path, chain);
        assertEquals(403, response.getStatus(), path);
        assertTrue(response.getContentAsString().contains("ROLE_SCOPE"), path);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void the_owner_reaches_societes_licenses_auth_and_system_only() throws Exception {
        authenticateAs("SUPERADMIN");
        for (String path : List.of("/api/v1/societes", "/api/v1/societes/abc/direction-accounts", "/api/v1/licenses",
                "/api/v1/licenses/issue-societe", "/api/v1/auth/me", "/api/v1/system/ping")) {
            assertAllowed(path);
        }
        for (String path : CENTRE_DATA) assertForbidden(path);
        assertForbidden("/api/v1/direction/overview");
    }

    @Test
    void the_direction_reaches_its_dashboards_auth_and_system_only() throws Exception {
        authenticateAs("DIRECTION");
        for (String path : List.of("/api/v1/direction", "/api/v1/direction/overview", "/api/v1/auth/me",
                "/api/v1/auth/refresh", "/api/v1/system/ping")) {
            assertAllowed(path);
        }
        for (String path : CENTRE_DATA) assertForbidden(path);
        assertForbidden("/api/v1/societes");
        assertForbidden("/api/v1/licenses");
        assertForbidden("/api/v1/directionx");
    }

    @Test
    void other_roles_are_not_affected() throws Exception {
        for (String role : List.of("ADMIN", "MEDECIN", "INFIRMIER")) {
            authenticateAs(role);
            assertAllowed("/api/v1/patients");
        }
    }

    @Test
    void a_nurse_alone_reaches_his_own_planning_and_the_sessions_but_not_staff_management() throws Exception {
        authenticateAs("INFIRMIER");
        for (String path : List.of("/api/v1/infirmiers/moi/planning", "/api/v1/infirmiers/moi/absences",
                "/api/v1/seances", "/api/v1/patients", "/api/v1/auth/me")) {
            assertAllowed(path);
        }
        for (String path : List.of("/api/v1/infirmiers", "/api/v1/infirmiers/presence/semaine",
                "/api/v1/infirmiers/absences", "/api/v1/infirmiers/comptes-liables",
                "/api/v1/planning/semaine", "/api/v1/planning/parametres", "/api/v1/planning/affectations")) {
            assertForbidden(path);
        }
        assertAllowed("/api/v1/infirmiersx");
    }

    @Test
    void a_nurse_who_also_holds_another_centre_role_keeps_staff_management() throws Exception {
        for (String autre : List.of("ADMIN", "MEDECIN", "SECRETAIRE")) {
            authenticateAs("INFIRMIER", autre);
            assertAllowed("/api/v1/infirmiers/presence/semaine");
            assertAllowed("/api/v1/planning/semaine");
        }
    }

    @Test
    void a_doctor_alone_reads_patients_and_the_daily_planning_but_not_management_areas() throws Exception {
        authenticateAs("MEDECIN");
        for (String path : List.of("/api/v1/patients", "/api/v1/patients/123/dossier-medical", "/api/v1/seances",
                "/api/v1/planning/semaine", "/api/v1/infirmiers/presence/semaine", "/api/v1/auth/me")) {
            assertAllowed(path);
        }
        for (String path : List.of("/api/v1/infirmiers", "/api/v1/infirmiers/absences", "/api/v1/infirmiers/moi/planning",
                "/api/v1/infirmiers/presence/alertes", "/api/v1/planning/parametres", "/api/v1/planning/affectations",
                "/api/v1/facturation/preview", "/api/v1/reglements", "/api/v1/comptabilite/journal", "/api/v1/gmao/equipements")) {
            assertForbidden(path);
        }
    }

    @Test
    void a_doctor_who_also_holds_another_centre_role_keeps_everything() throws Exception {
        for (String autre : List.of("ADMIN", "SECRETAIRE", "INFIRMIER")) {
            authenticateAs("MEDECIN", autre);
            assertAllowed("/api/v1/facturation/preview");
            assertAllowed("/api/v1/planning/parametres");
        }
    }

    @Test
    void non_api_paths_and_preflight_requests_are_ignored() throws Exception {
        authenticateAs("DIRECTION");
        FilterChain chain = mock(FilterChain.class);
        assertEquals(200, call("GET", "/actuator/health", chain).getStatus());
        assertEquals(200, call("OPTIONS", "/api/v1/patients", chain).getStatus());
    }

    @Test
    void anonymous_requests_are_left_to_the_security_chain() throws Exception {
        assertAllowed("/api/v1/patients");
    }
}
