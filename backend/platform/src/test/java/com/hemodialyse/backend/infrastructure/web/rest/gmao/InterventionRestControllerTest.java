package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.AjouterLigneCoutRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.CreateInterventionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.InterventionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InterventionRestControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creerIntervention_should_use_the_authenticated_center_and_intervenant() {
        UUID centreId = authenticate();
        UUID intervenantId = UUID.randomUUID();
        FakeInterventionRepository repo = new FakeInterventionRepository();
        var controller = new InterventionRestController(repo);

        var request = new CreateInterventionRequest(
                UUID.randomUUID(), "CURATIVE", LocalDateTime.now(), "Panne pompe", intervenantId);
        ResponseEntity<InterventionResponse> response = controller.creerIntervention(request, authentication());

        assertEquals(201, response.getStatusCode().value());
        assertEquals(centreId, response.getBody().centreId());
        assertEquals(intervenantId, response.getBody().intervenantId());
    }

    @Test
    void ajouterLigneCout_should_accumulate_and_return_the_updated_total() {
        authenticate();
        FakeInterventionRepository repo = new FakeInterventionRepository();
        Intervention intervention = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                LocalDateTime.now(), "Panne", null, UUID.randomUUID());
        repo.byId = Optional.of(intervention);
        var controller = new InterventionRestController(repo);

        var request = new AjouterLigneCoutRequest("PIECE", "Filtre RO", new BigDecimal("1"), new BigDecimal("1500.00"), null);
        ResponseEntity<InterventionResponse> response = controller.ajouterLigneCout(
                intervention.getId().toString(), request, authentication());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().lignesCout().size());
        assertEquals(new BigDecimal("1500.00"), response.getBody().coutTotal());
        assertNotNull(repo.saved);
    }

    @Test
    void listerInterventions_should_be_paginated_and_scoped_to_the_authenticated_center() {
        UUID centreId = authenticate();
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.paged = PagedResult.of(List.of(), 0, 0, 20);
        var controller = new InterventionRestController(repo);

        controller.listerInterventions(null, null, 0, 20, authentication());

        assertEquals(centreId, repo.lastCentreId);
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

    private static final class FakeInterventionRepository implements InterventionRepositoryPort {
        private PagedResult<Intervention> paged = PagedResult.of(List.of(), 0, 0, 20);
        private Optional<Intervention> byId = Optional.empty();
        private Intervention saved;
        private UUID lastCentreId;

        @Override
        public void save(Intervention intervention) {
            this.saved = intervention;
        }

        @Override
        public Optional<Intervention> findById(UUID id) {
            return byId;
        }

        @Override
        public List<Intervention> findByEquipementId(UUID equipementId) {
            return List.of();
        }

        @Override
        public List<Intervention> findByCentreId(UUID centreId) {
            return List.of();
        }

        @Override
        public List<Intervention> findByCentreIdAndStatut(UUID centreId, String statut) {
            return List.of();
        }

        @Override
        public List<Intervention> findByCentreIdAndDateRange(UUID centreId, LocalDateTime debut, LocalDateTime fin) {
            return List.of();
        }

        @Override
        public List<Intervention> findByIntervenantId(UUID intervenantId) {
            return List.of();
        }

        @Override
        public List<Intervention> findPendingByEquipementId(UUID equipementId) {
            return List.of();
        }

        @Override
        public void delete(UUID id) {
        }

        @Override
        public long countByCentreId(UUID centreId) {
            return 0;
        }

        @Override
        public long countByCentreIdAndStatutEnCours(UUID centreId) {
            return 0;
        }

        @Override
        public long countByCentreIdAndStatut(UUID centreId, String statut) {
            return 0;
        }

        @Override
        public PagedResult<Intervention> findPaged(UUID centreId, String statut, int page, int size) {
            this.lastCentreId = centreId;
            return paged;
        }

        @Override
        public PagedResult<Intervention> findPagedByEquipementId(UUID equipementId, int page, int size) {
            return paged;
        }

        @Override
        public long countByEquipementId(UUID equipementId) {
            return 0;
        }

        @Override
        public Optional<Intervention> findLatestByEquipementId(UUID equipementId) {
            return Optional.empty();
        }

        @Override
        public BigDecimal sumCoutByEquipementIdAndDateRange(UUID equipementId, LocalDateTime from, LocalDateTime to) {
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal sumCoutByCentreIdAndDateRange(UUID centreId, LocalDateTime from, LocalDateTime to) {
            return BigDecimal.ZERO;
        }
    }
}
