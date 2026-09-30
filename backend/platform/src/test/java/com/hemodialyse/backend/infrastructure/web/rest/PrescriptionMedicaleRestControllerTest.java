package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.model.UniteFrequence;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.PrescriptionMedicaleSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertPrescriptionMedicaleRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PrescriptionMedicaleResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrescriptionMedicaleRestControllerTest {

    private final CenterAccessGuard centerAccessGuard = new CenterAccessGuard();
    private final ArticleRepositoryPort articleRepository = new FakeArticleRepositoryPort();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_should_return_paged_items_from_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new PrescriptionMedicaleRestController(useCase, centerAccessGuard, articleRepository);

        UUID patientId = UUID.randomUUID();
        PrescriptionMedicale p = prescription(centerId, patientId);
        useCase.paged = PagedResult.of(List.of(p), 1, 0, 20);

        ResponseEntity<PagedResponse<PrescriptionMedicaleResponse>> response =
                controller.list(patientId, centerId, null, null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        PagedResponse<PrescriptionMedicaleResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.items().size());
        assertEquals(p.getId(), body.items().get(0).id());
    }

    @Test
    void search_should_honour_pagination_criteria() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new PrescriptionMedicaleRestController(useCase, centerAccessGuard, articleRepository);

        UUID patientId = UUID.randomUUID();
        useCase.paged = PagedResult.of(List.of(prescription(centerId, patientId)), 42, 2, 10);

        var criteria = new PrescriptionMedicaleSearchRequest(centerId, patientId, 2, 10, null, null);
        ResponseEntity<PagedResponse<PrescriptionMedicaleResponse>> response =
                controller.searchPrescriptions(criteria);

        assertEquals(200, response.getStatusCode().value());
        PagedResponse<PrescriptionMedicaleResponse> body = response.getBody();
        assertNotNull(body);
        // La pagination était auparavant ignorée : on vérifie qu'elle est bien transmise et restituée.
        assertEquals(42, body.total());
        assertEquals(2, body.page());
        assertEquals(10, body.size());
        assertEquals(2, useCase.lastPage);
        assertEquals(10, useCase.lastSize);
    }

    @Test
    void create_should_delegate_to_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new PrescriptionMedicaleRestController(useCase, centerAccessGuard, articleRepository);

        UUID patientId = UUID.randomUUID();
        UUID medecinId = UUID.randomUUID();

        UpsertPrescriptionMedicaleRequest request = new UpsertPrescriptionMedicaleRequest(
                centerId, LocalDate.of(2026, 5, 1), medecinId, 300, 500, 2500, 240, new BigDecimal("68.50"),
                "FX-80", "HNF",
                UUID.randomUUID(), 60, "SC", 1, UniteFrequence.SEMAINE,
                UUID.randomUUID(), 100, "IV", 1, UniteFrequence.SEMAINE);

        ResponseEntity<EntityWriteResponse> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(medecinId, useCase.lastMedecinId);
        assertEquals(new BigDecimal("68.50"), useCase.lastPoidsSec);
    }

    @Test
    void delete_should_delegate_to_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new PrescriptionMedicaleRestController(useCase, centerAccessGuard, articleRepository);

        UUID patientId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.delete(patientId, prescriptionId, centerId);

        assertEquals(204, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(prescriptionId, useCase.lastDeletedId);
    }

    @Test
    void search_should_be_forbidden_when_criteria_target_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new PrescriptionMedicaleRestController(useCase, centerAccessGuard, articleRepository);

        var criteria = new PrescriptionMedicaleSearchRequest(
                UUID.randomUUID(), UUID.randomUUID(), 0, 20, null, null);

        assertThrows(AccessDeniedException.class, () -> controller.searchPrescriptions(criteria));
    }

    private PrescriptionMedicale prescription(UUID centerId, UUID patientId) {
        PrescriptionMedicale p = new PrescriptionMedicale();
        p.setId(UUID.randomUUID());
        p.setCenterId(centerId);
        p.setPatientId(patientId);
        p.setDatePrescription(LocalDate.of(2026, 5, 1));
        return p;
    }

    private UUID authenticateMedecin() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "medecin", "", List.of("MEDECIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private static final class FakeUseCase implements PrescriptionMedicaleUseCase {
        private PagedResult<PrescriptionMedicale> paged = PagedResult.of(List.of(), 0, 0, 20);
        private CenterId lastCenterId;
        private UUID lastPatientId;
        private UUID lastMedecinId;
        private BigDecimal lastPoidsSec;
        private UUID lastDeletedId;
        private int lastPage = -1;
        private int lastSize = -1;

        @Override
        public List<PrescriptionMedicale> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return paged.items();
        }

        @Override
        public PagedResult<PrescriptionMedicale> listPagedByPatient(CenterId centerId, UUID patientId,
                                                                    LocalDate from, LocalDate to,
                                                                    int page, int size) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastPage = page;
            this.lastSize = size;
            return paged;
        }

        @Override
        public PrescriptionMedicale save(CenterId centerId, UUID patientId, UUID prescriptionId,
                                         LocalDate datePrescription, UUID medecinId, Integer qbCible,
                                         Integer qdCible, Integer ufMaxMl, Integer dureeCibleMin,
                                         BigDecimal poidsSecCibleKg,
                                         String typeDialyseurPrescrit, String anticoagTypePrescrit,
                                         UUID epoArticleId, Integer epoDoseUi, String epoVoie,
                                         Integer epoFrequenceValeur, UniteFrequence epoFrequenceUnite,
                                         UUID ferArticleId, Integer ferDoseMg, String ferVoie,
                                         Integer ferFrequenceValeur, UniteFrequence ferFrequenceUnite) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastMedecinId = medecinId;
            this.lastPoidsSec = poidsSecCibleKg;
            PrescriptionMedicale p = new PrescriptionMedicale();
            p.setId(prescriptionId != null ? prescriptionId : UUID.randomUUID());
            p.setPatientId(patientId);
            p.setCenterId(centerId.value());
            p.setUpdatedAt(java.time.OffsetDateTime.now());
            return p;
        }

        @Override
        public void delete(CenterId centerId, UUID patientId, UUID prescriptionId) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastDeletedId = prescriptionId;
        }
    }

    private static final class FakeArticleRepositoryPort implements ArticleRepositoryPort {
        @Override
        public Optional<Article> findById(UUID articleId, CenterId centerId) {
            return Optional.empty();
        }

        @Override
        public Article save(Article article) {
            return article;
        }

        @Override
        public List<Article> findAllByCenter(CenterId centerId) {
            return List.of();
        }

        @Override
        public List<Article> findAllByCenterAndTypeTraitementAnemie(
                CenterId centerId, com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie type) {
            return List.of();
        }
    }
}
