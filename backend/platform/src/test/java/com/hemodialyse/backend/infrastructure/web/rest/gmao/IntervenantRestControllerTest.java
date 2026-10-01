package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervenant;
import com.hemodialyse.backend.domain.gmao.port.IntervenantRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.CreateIntervenantRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.IntervenantResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie la pagination obligatoire (AGENTS.md §9) et l'isolation multi-centre (AGENTS.md §2)
 * du référentiel Intervenant GMAO.
 */
class IntervenantRestControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creer_should_scope_the_intervenant_to_the_authenticated_center() {
        UUID centreId = authenticate();
        FakeIntervenantRepository repo = new FakeIntervenantRepository();
        var controller = new IntervenantRestController(repo);

        var request = new CreateIntervenantRequest("Ahmed B.", "INTERNE", "0555000000", null, new BigDecimal("1500"));
        ResponseEntity<IntervenantResponse> response = controller.creer(request, authentication());

        assertEquals(201, response.getStatusCode().value());
        assertEquals(centreId, response.getBody().centreId());
        assertEquals("Ahmed B.", response.getBody().nom());
    }

    @Test
    void lister_should_be_paginated_and_scoped_to_the_authenticated_center() {
        UUID centreId = authenticate();
        FakeIntervenantRepository repo = new FakeIntervenantRepository();
        repo.paged = PagedResult.of(List.of(
                Intervenant.creer(centreId, "Ahmed B.", TypeIntervenant.INTERNE, null, null, null)), 1, 0, 20);
        var controller = new IntervenantRestController(repo);

        ResponseEntity<PagedResult<IntervenantResponse>> response = controller.lister(0, 20, authentication());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().items().size());
        assertEquals(centreId, repo.lastCentreId);
    }

    @Test
    void modifier_should_reject_an_intervenant_from_another_center() {
        authenticate();
        FakeIntervenantRepository repo = new FakeIntervenantRepository();
        Intervenant fromOtherCenter = Intervenant.creer(UUID.randomUUID(), "X", TypeIntervenant.EXTERNE, null, null, null);
        repo.byId = Optional.of(fromOtherCenter);
        var controller = new IntervenantRestController(repo);

        var request = new CreateIntervenantRequest("Y", "EXTERNE", null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> controller.modifier(fromOtherCenter.id().toString(), request, authentication()));
    }

    @Test
    void desactiver_should_flip_actif_to_false() {
        UUID centreId = authenticate();
        FakeIntervenantRepository repo = new FakeIntervenantRepository();
        Intervenant intervenant = Intervenant.creer(centreId, "Ahmed B.", TypeIntervenant.INTERNE, null, null, null);
        repo.byId = Optional.of(intervenant);
        var controller = new IntervenantRestController(repo);

        ResponseEntity<IntervenantResponse> response = controller.desactiver(intervenant.id().toString(), authentication());

        assertFalse(response.getBody().actif());
    }

    private UUID authenticate() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "admin", "", List.of("ADMIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private org.springframework.security.core.Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static final class FakeIntervenantRepository implements IntervenantRepositoryPort {
        private PagedResult<Intervenant> paged = PagedResult.of(List.of(), 0, 0, 20);
        private Optional<Intervenant> byId = Optional.empty();
        private UUID lastCentreId;

        @Override
        public Intervenant save(Intervenant intervenant) {
            return intervenant;
        }

        @Override
        public Optional<Intervenant> findById(UUID id) {
            return byId;
        }

        @Override
        public PagedResult<Intervenant> findPaged(UUID centreId, int page, int size) {
            this.lastCentreId = centreId;
            return paged;
        }
    }
}
