package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieUseCase;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.acl.planification.ReaffectationEnIsolementDeLaSerologie;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateSerologieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateSerologieRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.SerologieResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du SerologieRestController (mock des dépendances, pas de Spring Context).
 * Verrouille l'isolation multi-centre (centerId) sur toutes les opérations : list, create, synthese, update, delete.
 * Suit le pattern de {@link DossierMedicalPatientRestControllerTest}.
 */
class SerologieRestControllerTest {

    private final CenterAccessGuard centerAccessGuard = new CenterAccessGuard();
    private final ReaffectationEnIsolementDeLaSerologie reaffectation = mock(ReaffectationEnIsolementDeLaSerologie.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_should_return_paged_serologies_for_center() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UUID patientId = UUID.randomUUID();
        Serologie s = serologie(centerId, patientId);
        useCase.paged = PagedResult.of(List.of(s), 1, 0, 20);

        ResponseEntity<PagedResponse<SerologieResponse>> response =
                controller.list(patientId, centerId, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        PagedResponse<SerologieResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.items().size());
        assertEquals(s.getId(), body.items().get(0).id());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(0, useCase.lastPage);
        assertEquals(20, useCase.lastSize);
    }

    @Test
    void list_should_be_forbidden_when_requesting_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UUID otherCenterId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> controller.list(UUID.randomUUID(), otherCenterId, 0, 20));
    }

    @Test
    void list_should_fall_back_to_session_center_when_not_provided() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        controller.list(UUID.randomUUID(), null, 0, 20);

        assertEquals(centerId, useCase.lastCenterId.value());
    }

    @Test
    void create_should_delegate_to_use_case_with_center_from_body() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        whenReaffectationPassesThrough(useCase);
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UUID patientId = UUID.randomUUID();
        CreateSerologieRequest request = new CreateSerologieRequest(
                centerId, "AG_HBS", "POSITIF", new BigDecimal("12.5"), "IU/ml",
                LocalDate.of(2026, 9, 1), "Labo central", LocalDate.of(2027, 9, 1),
                "Isolement machine + antibiose");

        ResponseEntity<SerologieResponse> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(MarqueurSerologique.AG_HBS, useCase.lastMarqueur);
        assertEquals(ResultatSerologique.POSITIF, useCase.lastResultat);
        assertEquals("Isolement machine + antibiose", useCase.lastConduiteATenir);
    }

    @Test
    void create_should_be_forbidden_when_body_targets_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        CreateSerologieRequest request = new CreateSerologieRequest(
                UUID.randomUUID(), "AG_HBS", "NEGATIF", null, null,
                LocalDate.of(2026, 9, 1), null, null, null);

        assertThrows(AccessDeniedException.class, () -> controller.create(UUID.randomUUID(), request));
    }

    @Test
    void synthese_should_return_last_result_per_marker() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UUID patientId = UUID.randomUUID();
        Serologie s1 = serologie(centerId, patientId);
        Serologie s2 = serologie(centerId, patientId);
        useCase.derniers = List.of(s1, s2);

        ResponseEntity<List<SerologieResponse>> response = controller.synthese(patientId, centerId);

        assertEquals(200, response.getStatusCode().value());
        List<SerologieResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
    }

    @Test
    void synthese_should_be_forbidden_when_requesting_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        assertThrows(AccessDeniedException.class,
                () -> controller.synthese(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void update_should_delegate_to_use_case_with_center_from_body() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        whenReaffectationPassesThrough(useCase);
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UUID patientId = UUID.randomUUID();
        UUID serologieId = UUID.randomUUID();
        UpdateSerologieRequest request = new UpdateSerologieRequest(centerId, "POSITIF", "Conduite mise à jour");

        ResponseEntity<SerologieResponse> response = controller.update(patientId, serologieId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(serologieId, useCase.lastSerologieId);
        assertEquals(ResultatSerologique.POSITIF, useCase.lastResultat);
        assertEquals("Conduite mise à jour", useCase.lastConduiteATenir);
    }

    @Test
    void update_should_be_forbidden_when_body_targets_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UpdateSerologieRequest request = new UpdateSerologieRequest(UUID.randomUUID(), "NEGATIF", "RAS");

        assertThrows(AccessDeniedException.class,
                () -> controller.update(UUID.randomUUID(), UUID.randomUUID(), request));
    }

    @Test
    void delete_should_delegate_to_use_case_with_center_from_param() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        UUID patientId = UUID.randomUUID();
        UUID serologieId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.delete(patientId, serologieId, centerId);

        assertEquals(204, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastDeletedCenterId.value());
        assertEquals(patientId, useCase.lastDeletedPatientId);
        assertEquals(serologieId, useCase.lastDeletedSerologieId);
    }

    @Test
    void delete_should_be_forbidden_when_param_targets_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        assertThrows(AccessDeniedException.class,
                () -> controller.delete(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void delete_should_fall_back_to_session_center_when_not_provided() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new SerologieRestController(useCase, centerAccessGuard, reaffectation);

        controller.delete(UUID.randomUUID(), UUID.randomUUID(), null);

        assertEquals(centerId, useCase.lastDeletedCenterId.value());
    }

    /* ── helpers ── */

    private UUID authenticateMedecin() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "medecin", "", List.of("MEDECIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private Serologie serologie(UUID centerId, UUID patientId) {
        return Serologie.enregistrer(
                patientId, centerId, MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF,
                new BigDecimal("1.2"), "IU/ml", LocalDate.of(2026, 9, 1), "Labo central",
                LocalDate.of(2027, 9, 1), "Conduite standard");
    }

    private void whenReaffectationPassesThrough(FakeUseCase useCase) {
        when(reaffectation.enregistrerPuisReaffecter(any(UUID.class), any(UUID.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(2, Supplier.class).get());
    }

    /* ── fake use case ── */

    private static final class FakeUseCase implements SerologieUseCase {
        private PagedResult<Serologie> paged = PagedResult.of(List.of(), 0, 0, 20);
        private List<Serologie> derniers = List.of();
        private CenterId lastCenterId;
        private UUID lastPatientId;
        private MarqueurSerologique lastMarqueur;
        private ResultatSerologique lastResultat;
        private String lastConduiteATenir;
        private UUID lastSerologieId;
        private int lastPage = -1;
        private int lastSize = -1;
        private CenterId lastDeletedCenterId;
        private UUID lastDeletedPatientId;
        private UUID lastDeletedSerologieId;

        @Override
        public PagedResult<Serologie> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastPage = page;
            this.lastSize = size;
            return paged;
        }

        @Override
        public List<Serologie> listDerniersResultatsByPatient(CenterId centerId, UUID patientId) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return derniers;
        }

        @Override
        public Serologie create(CenterId centerId, UUID patientId, MarqueurSerologique marqueur,
                                ResultatSerologique resultat, BigDecimal titre, String unite,
                                LocalDate datePrelevement, String laboratoire, LocalDate dateProchainControle,
                                String conduiteATenir) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastMarqueur = marqueur;
            this.lastResultat = resultat;
            this.lastConduiteATenir = conduiteATenir;
            return Serologie.enregistrer(patientId, centerId.value(), marqueur, resultat, titre, unite,
                    datePrelevement, laboratoire, dateProchainControle, conduiteATenir);
        }

        @Override
        public Serologie update(CenterId centerId, UUID patientId, UUID serologieId,
                                ResultatSerologique resultat, String conduiteATenir) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastSerologieId = serologieId;
            this.lastResultat = resultat;
            this.lastConduiteATenir = conduiteATenir;
            return Serologie.reconstituer(serologieId, patientId, centerId.value(), MarqueurSerologique.AG_HBS,
                    resultat, null, null, LocalDate.now(), null, null, conduiteATenir, null, null);
        }

        @Override
        public void delete(CenterId centerId, UUID patientId, UUID serologieId) {
            this.lastDeletedCenterId = centerId;
            this.lastDeletedPatientId = patientId;
            this.lastDeletedSerologieId = serologieId;
        }
    }
}
