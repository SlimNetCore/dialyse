package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.application.gmao.InterventionEquipementStatutService;
import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.EquipementStatutHistoriqueRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.IntervenantRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.AjouterLigneCoutRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.CreateInterventionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.TerminerInterventionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.InterventionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterventionRestControllerTest {

    private final FakeIntervenantRepository intervenants = new FakeIntervenantRepository();
    private final EquipementRepositoryPort equipements = mock(EquipementRepositoryPort.class);
    private final EquipementStatutHistoriqueRepositoryPort historique = mock(EquipementStatutHistoriqueRepositoryPort.class);
    private final InterventionEquipementStatutService statutService =
            new InterventionEquipementStatutService(equipements, historique);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creerIntervention_should_use_the_authenticated_center_and_record_the_state_before() {
        UUID centreId = authenticate();
        UUID intervenantId = UUID.randomUUID();
        Equipement equipement = equipement(centreId);
        FakeInterventionRepository repo = new FakeInterventionRepository();
        var controller = controller(repo);

        var request = new CreateInterventionRequest(
                equipement.getId(), "CURATIVE", OffsetDateTime.now(ZoneOffset.UTC), "Panne pompe", intervenantId, "EN_MAINTENANCE");
        ResponseEntity<InterventionResponse> response = controller.creerIntervention(request, authentication());

        assertEquals(201, response.getStatusCode().value());
        assertEquals(centreId, response.getBody().centreId());
        assertEquals(intervenantId, response.getBody().intervenantId());
        assertEquals(StatutEquipement.EN_MAINTENANCE, response.getBody().etatEquipementAvant());
    }

    @Test
    void creerIntervention_should_reject_an_equipment_of_another_center() {
        authenticate();
        Equipement autreCentre = equipement(UUID.randomUUID());
        var controller = controller(new FakeInterventionRepository());

        var request = new CreateInterventionRequest(
                autreCentre.getId(), "CURATIVE", OffsetDateTime.now(ZoneOffset.UTC), "Panne", null, "EN_SERVICE");

        assertThrows(IllegalArgumentException.class, () -> controller.creerIntervention(request, authentication()));
    }

    @Test
    void demarrerIntervention_should_apply_the_state_before_to_the_equipment() {
        UUID centreId = authenticate();
        Equipement equipement = equipement(centreId);
        Intervention planifiee = Intervention.creer(equipement.getId(), centreId, TypeIntervention.CURATIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.byId = Optional.of(planifiee);

        controller(repo).demarrerIntervention(planifiee.getId().toString(), authentication());

        assertEquals(StatutEquipement.EN_MAINTENANCE, equipement.getStatut());
        verify(historique).save(any());
    }

    @Test
    void demarrerIntervention_should_keep_an_in_service_equipment_available() {
        UUID centreId = authenticate();
        Equipement equipement = equipement(centreId);
        Intervention planifiee = Intervention.creer(equipement.getId(), centreId, TypeIntervention.PREVENTIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Contrôle", null, StatutEquipement.EN_SERVICE, UUID.randomUUID());
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.byId = Optional.of(planifiee);

        controller(repo).demarrerIntervention(planifiee.getId().toString(), authentication());

        assertEquals(StatutEquipement.EN_SERVICE, equipement.getStatut());
        verify(historique, never()).save(any());
    }

    @Test
    void terminerIntervention_should_apply_the_state_after_and_allow_a_reform_proposal() {
        UUID centreId = authenticate();
        Equipement equipement = equipement(centreId);
        Intervention enCours = enCours(equipement.getId(), centreId, null, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.byId = Optional.of(enCours);

        ResponseEntity<InterventionResponse> response = controller(repo).terminerIntervention(
                enCours.getId().toString(), new TerminerInterventionRequest("Irréparable", "A_REFORMER", OffsetDateTime.now(ZoneOffset.UTC)), authentication());

        assertEquals(StatutEquipement.A_REFORMER, response.getBody().etatEquipementApres());
        assertEquals(StatutEquipement.A_REFORMER, equipement.getStatut());
    }

    @Test
    void terminerIntervention_should_never_let_an_intervention_reform_an_equipment() {
        UUID centreId = authenticate();
        Equipement equipement = equipement(centreId);
        Intervention enCours = enCours(equipement.getId(), centreId, null, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.byId = Optional.of(enCours);
        var controller = controller(repo);
        var request = new TerminerInterventionRequest("Fait", "REFORME", OffsetDateTime.now(ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
                () -> controller.terminerIntervention(enCours.getId().toString(), request, authentication()));
        assertEquals(StatutEquipement.EN_SERVICE, equipement.getStatut());
    }

    @Test
    void terminerIntervention_should_value_the_intervenant_time_from_his_hourly_rate() {
        UUID centreId = authenticate();
        Equipement equipement = equipement(centreId);
        Intervenant intervenant = Intervenant.creer(
                centreId, "Tech Interne", TypeIntervenant.INTERNE, null, null, new BigDecimal("1000"));
        intervenants.byId = Optional.of(intervenant);
        Intervention enCours = enCours(equipement.getId(), centreId, intervenant.id(), OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.byId = Optional.of(enCours);

        ResponseEntity<InterventionResponse> response = controller(repo).terminerIntervention(
                enCours.getId().toString(), new TerminerInterventionRequest("Réparé", "EN_SERVICE", OffsetDateTime.now(ZoneOffset.UTC)), authentication());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().lignesCout().size());
        assertEquals(0, new BigDecimal("2000").compareTo(response.getBody().coutTotal()));
    }

    @Test
    void terminerIntervention_should_ignore_an_intervenant_of_another_center() {
        UUID centreId = authenticate();
        Equipement equipement = equipement(centreId);
        intervenants.byId = Optional.of(Intervenant.creer(
                UUID.randomUUID(), "Autre centre", TypeIntervenant.EXTERNE, null, null, new BigDecimal("1000")));
        Intervention enCours = enCours(equipement.getId(), centreId, UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.byId = Optional.of(enCours);

        ResponseEntity<InterventionResponse> response = controller(repo).terminerIntervention(
                enCours.getId().toString(), new TerminerInterventionRequest("Réparé", "EN_SERVICE", OffsetDateTime.now(ZoneOffset.UTC)), authentication());

        assertTrue(response.getBody().lignesCout().isEmpty());
    }

    @Test
    void ajouterLigneCout_should_accumulate_and_return_the_updated_total() {
        UUID centreId = authenticate();
        FakeInterventionRepository repo = new FakeInterventionRepository();
        Intervention intervention = Intervention.creer(UUID.randomUUID(), centreId, TypeIntervention.CURATIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
        repo.byId = Optional.of(intervention);

        var request = new AjouterLigneCoutRequest("PIECE", "Filtre RO", new BigDecimal("1"), new BigDecimal("1500.00"), null);
        ResponseEntity<InterventionResponse> response = controller(repo).ajouterLigneCout(
                intervention.getId().toString(), request, authentication());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().lignesCout().size());
        assertEquals(new BigDecimal("1500.00"), response.getBody().coutTotal());
        assertNotNull(repo.saved);
    }

    @Test
    void actions_should_not_reach_an_intervention_of_another_center() {
        authenticate();
        FakeInterventionRepository repo = new FakeInterventionRepository();
        Intervention autreCentre = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
        repo.byId = Optional.of(autreCentre);
        var controller = controller(repo);
        var id = autreCentre.getId().toString();
        var ligne = new AjouterLigneCoutRequest("PIECE", "Filtre", BigDecimal.ONE, BigDecimal.TEN, null);

        assertThrows(IllegalArgumentException.class, () -> controller.demarrerIntervention(id, authentication()));
        assertThrows(IllegalArgumentException.class, () -> controller.ajouterLigneCout(id, ligne, authentication()));
        assertEquals(404, controller.obtenirIntervention(id, authentication()).getStatusCode().value());
        assertNull(repo.saved);
    }

    @Test
    void listerInterventions_should_be_paginated_and_scoped_to_the_authenticated_center() {
        UUID centreId = authenticate();
        FakeInterventionRepository repo = new FakeInterventionRepository();
        repo.paged = PagedResult.of(List.of(), 0, 0, 20);

        controller(repo).listerInterventions(null, null, 0, 20, authentication());

        assertEquals(centreId, repo.lastCentreId);
    }

    private InterventionRestController controller(InterventionRepositoryPort repo) {
        return new InterventionRestController(repo, intervenants, statutService);
    }

    /**
     * Équipement en service du centre donné, résolu par le mock du port.
     */
    private Equipement equipement(UUID centreId) {
        Equipement equipement = Equipement.creer(
                "EQ-" + UUID.randomUUID().toString().substring(0, 4), "Générateur", TypeEquipement.GENERATEUR_DIALYSE,
                null, null, null, OffsetDateTime.now(ZoneOffset.UTC), centreId, null, UUID.randomUUID(), null, null);
        when(equipements.findById(equipement.getId())).thenReturn(Optional.of(equipement));
        return equipement;
    }

    private Intervention enCours(UUID equipementId, UUID centreId, UUID intervenantId, OffsetDateTime debut) {
        return Intervention.reconstruct(
                UUID.randomUUID(), equipementId, centreId, TypeIntervention.CURATIVE,
                StatutIntervention.EN_COURS, debut, null, intervenantId, "Panne", null, null, null,
                debut, debut, UUID.randomUUID(), UUID.randomUUID(), null,
                StatutEquipement.EN_MAINTENANCE, null);
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
        public List<Intervention> findByCentreIdAndDateRange(UUID centreId, OffsetDateTime debut, OffsetDateTime fin) {
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
        public BigDecimal sumCoutByEquipementIdAndDateRange(UUID equipementId, OffsetDateTime from, OffsetDateTime to) {
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal sumCoutByCentreIdAndDateRange(UUID centreId, OffsetDateTime from, OffsetDateTime to) {
            return BigDecimal.ZERO;
        }

        @Override
        public long countEnRetardByCentreId(UUID centreId, OffsetDateTime maintenant) {
            return 0;
        }

        @Override
        public java.util.Map<UUID, BigDecimal> sumCoutParEquipement(UUID centreId, OffsetDateTime from, OffsetDateTime to) {
            return java.util.Map.of();
        }
    }

    private static final class FakeIntervenantRepository implements IntervenantRepositoryPort {
        private Optional<Intervenant> byId = Optional.empty();

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
            return PagedResult.of(List.of(), 0, page, size);
        }
    }
}
