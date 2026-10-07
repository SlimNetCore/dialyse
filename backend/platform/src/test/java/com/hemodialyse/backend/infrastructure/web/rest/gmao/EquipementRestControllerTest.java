package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeEquipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.ReformerEquipementRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.UpdateEquipementRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.EquipementResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vérifie la pagination obligatoire (AGENTS.md §9) et l'isolation multi-centre (AGENTS.md §2)
 * du contrôleur Équipement GMAO.
 */
class EquipementRestControllerTest {

    private UUID centerIdHolder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_should_be_paginated_and_scoped_to_the_authenticated_center() {
        UUID centerId = authenticate(UUID.randomUUID());
        FakeEquipementRepository repo = new FakeEquipementRepository();
        Equipement eq = equipement(centerId);
        repo.paged = PagedResult.of(List.of(eq), 1, 0, 20);
        var controller = new EquipementRestController(repo, new FakeHistoriqueRepository(),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.SalleGenerateursService.class),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.GenerateurIndisponibleService.class));

        ResponseEntity<PagedResult<EquipementResponse>> response =
                controller.listerEquipements(null, 0, 20, authentication());

        assertEquals(200, response.getStatusCode().value());
        PagedResult<EquipementResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.items().size());
        assertEquals(eq.getId(), body.items().get(0).id());
        // Le centre transmis au port est toujours celui du principal authentifié, jamais un paramètre client.
        assertEquals(centerId, repo.lastCentreId);
        assertEquals(0, repo.lastPage);
        assertEquals(20, repo.lastSize);
    }

    @Test
    void list_should_forward_statut_filter() {
        UUID centerId = authenticate(UUID.randomUUID());
        FakeEquipementRepository repo = new FakeEquipementRepository();
        repo.paged = PagedResult.of(List.of(), 0, 1, 10);
        var controller = new EquipementRestController(repo, new FakeHistoriqueRepository(),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.SalleGenerateursService.class),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.GenerateurIndisponibleService.class));

        controller.listerEquipements("HORS_SERVICE", 1, 10, authentication());

        assertEquals("HORS_SERVICE", repo.lastStatut);
        assertEquals(1, repo.lastPage);
        assertEquals(10, repo.lastSize);
    }

    @Test
    void get_should_return_404_when_equipement_belongs_to_another_center() {
        authenticate(UUID.randomUUID());
        FakeEquipementRepository repo = new FakeEquipementRepository();
        Equipement fromOtherCenter = equipement(UUID.randomUUID());
        repo.byId = Optional.of(fromOtherCenter);
        var controller = new EquipementRestController(repo, new FakeHistoriqueRepository(),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.SalleGenerateursService.class),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.GenerateurIndisponibleService.class));

        ResponseEntity<EquipementResponse> response =
                controller.obtenirEquipement(fromOtherCenter.getId().toString(), authentication());

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void update_should_apply_changes_and_persist() {
        UUID centerId = authenticate(UUID.randomUUID());
        FakeEquipementRepository repo = new FakeEquipementRepository();
        Equipement eq = equipement(centerId);
        repo.byId = Optional.of(eq);
        var controller = new EquipementRestController(repo, new FakeHistoriqueRepository(),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.SalleGenerateursService.class),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.GenerateurIndisponibleService.class));

        var request = new UpdateEquipementRequest(
                "Générateur révisé", "Fresenius", "4008S", "SN-42", "Salle 2", null, null);
        ResponseEntity<EquipementResponse> response =
                controller.modifierEquipement(eq.getId().toString(), request, authentication());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Générateur révisé", response.getBody().designation());
        assertEquals("Salle 2", response.getBody().localisation());
        assertNotNull(repo.saved);
    }

    @Test
    void reformer_should_update_statut_and_record_history() {
        UUID centerId = authenticate(UUID.randomUUID());
        FakeEquipementRepository repo = new FakeEquipementRepository();
        Equipement eq = equipement(centerId);
        repo.byId = Optional.of(eq);
        FakeHistoriqueRepository historique = new FakeHistoriqueRepository();
        var controller = new EquipementRestController(repo, historique,
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.SalleGenerateursService.class),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.GenerateurIndisponibleService.class));

        ResponseEntity<EquipementResponse> response = controller.reformerEquipement(
                eq.getId().toString(), new ReformerEquipementRequest("Fin de vie"), authentication());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(StatutEquipement.REFORME, response.getBody().statut());
        assertEquals(1, historique.saved.size());
        assertEquals(StatutEquipement.EN_SERVICE, historique.saved.get(0).getStatutPrecedent());
        assertEquals(StatutEquipement.REFORME, historique.saved.get(0).getStatutNouveau());
    }

    @Test
    void creerEquipement_should_reject_duplicate_code_within_the_same_center() {
        UUID centerId = authenticate(UUID.randomUUID());
        FakeEquipementRepository repo = new FakeEquipementRepository();
        repo.byCode = Optional.of(equipement(centerId));
        var controller = new EquipementRestController(repo, new FakeHistoriqueRepository(),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.SalleGenerateursService.class),
                org.mockito.Mockito.mock(com.hemodialyse.backend.application.planning.GenerateurIndisponibleService.class));

        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.gmao.CreateEquipementRequest(
                "EQ-DUP", "Générateur", "GENERATEUR_DIALYSE", null, null, null,
                OffsetDateTime.now(ZoneOffset.UTC), null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> controller.creerEquipement(request, authentication()));
    }

    private Equipement equipement(UUID centerId) {
        return Equipement.creer(
                "EQ-" + UUID.randomUUID().toString().substring(0, 8),
                "Générateur de dialyse",
                TypeEquipement.GENERATEUR_DIALYSE,
                "Fresenius", "4008S", "SN-1",
                OffsetDateTime.now(ZoneOffset.UTC), centerId, "Salle 1", UUID.randomUUID(), null, null);
    }

    private UUID authenticate(UUID centerId) {
        this.centerIdHolder = centerId;
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "admin", "", List.of("ADMIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private org.springframework.security.core.Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static final class FakeEquipementRepository implements EquipementRepositoryPort {
        private PagedResult<Equipement> paged = PagedResult.of(List.of(), 0, 0, 20);
        private Optional<Equipement> byId = Optional.empty();
        private Optional<Equipement> byCode = Optional.empty();
        private Equipement saved;
        private UUID lastCentreId;
        private String lastStatut;
        private int lastPage;
        private int lastSize;

        @Override
        public void save(Equipement equipement) {
            this.saved = equipement;
        }

        @Override
        public Optional<Equipement> findById(UUID id) {
            return byId;
        }

        @Override
        public List<Equipement> findByCentreId(UUID centreId) {
            return List.of();
        }

        @Override
        public List<Equipement> findByCentreIdAndStatut(UUID centreId, String statut) {
            return List.of();
        }

        @Override
        public PagedResult<Equipement> findPaged(UUID centreId, String statut, int page, int size) {
            this.lastCentreId = centreId;
            this.lastStatut = statut;
            this.lastPage = page;
            this.lastSize = size;
            return paged;
        }

        @Override
        public long countByCentreIdAndStatut(UUID centreId, String statut) {
            return 0;
        }

        @Override
        public Optional<Equipement> findByCentreIdAndCode(UUID centreId, String code) {
            return byCode;
        }

        @Override
        public List<Equipement> findByCentreIdAndType(UUID centreId, String type) {
            return List.of();
        }

        @Override
        public List<Equipement> findByCentreIdAndTypeAndSalleId(UUID centreId, String type, UUID salleId) {
            return List.of();
        }

        @Override
        public boolean existsById(UUID id) {
            return false;
        }

        @Override
        public void delete(UUID id) {
        }

        @Override
        public long countByCentreId(UUID centreId) {
            return 0;
        }
    }

    private static final class FakeHistoriqueRepository implements EquipementStatutHistoriqueRepositoryPort {
        private final List<EquipementStatutHistorique> saved = new ArrayList<>();

        @Override
        public void save(EquipementStatutHistorique entree) {
            saved.add(entree);
        }

        @Override
        public List<EquipementStatutHistorique> findByEquipementIdOrderByChangedAtAsc(UUID equipementId) {
            return saved;
        }
    }
}
