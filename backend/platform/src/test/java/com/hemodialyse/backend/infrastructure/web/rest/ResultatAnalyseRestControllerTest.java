package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.ResultatAnalyseSearchRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertResultatAnalyseRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.ResultatAnalyseResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultatAnalyseRestControllerTest {

    private final CenterAccessGuard centerAccessGuard = new CenterAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_should_return_paged_items_from_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new ResultatAnalyseRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        ResultatAnalyse r = analyse(centerId, patientId);
        useCase.paged = PagedResult.of(List.of(r), 1, 0, 20);

        ResponseEntity<PagedResponse<ResultatAnalyseResponse>> response =
                controller.list(patientId, centerId, null, null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        PagedResponse<ResultatAnalyseResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.items().size());
        assertEquals(r.getId(), body.items().get(0).id());
    }

    @Test
    void search_should_honour_pagination_criteria() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new ResultatAnalyseRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        useCase.paged = PagedResult.of(List.of(analyse(centerId, patientId)), 137, 3, 50);

        var criteria = new ResultatAnalyseSearchRequest(centerId, patientId, 3, 50, null, null);
        ResponseEntity<PagedResponse<ResultatAnalyseResponse>> response = controller.searchAnalyses(criteria);

        assertEquals(200, response.getStatusCode().value());
        PagedResponse<ResultatAnalyseResponse> body = response.getBody();
        assertNotNull(body);
        // La pagination était auparavant ignorée : on vérifie qu'elle est bien transmise et restituée.
        assertEquals(137, body.total());
        assertEquals(3, useCase.lastPage);
        assertEquals(50, useCase.lastSize);
    }

    @Test
    void create_should_delegate_to_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new ResultatAnalyseRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        UpsertResultatAnalyseRequest request = new UpsertResultatAnalyseRequest(
                centerId, LocalDate.of(2026, 5, 1), new BigDecimal("10.5"),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<EntityWriteResponse> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
    }

    @Test
    void list_should_be_forbidden_when_requesting_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new ResultatAnalyseRestController(useCase, centerAccessGuard);

        UUID otherCenterId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> controller.list(UUID.randomUUID(), otherCenterId, null, null, 0, 20));
    }

    private ResultatAnalyse analyse(UUID centerId, UUID patientId) {
        ResultatAnalyse r = new ResultatAnalyse();
        r.setId(UUID.randomUUID());
        r.setCenterId(centerId);
        r.setPatientId(patientId);
        r.setDatePrelevement(LocalDate.of(2026, 5, 1));
        return r;
    }

    private UUID authenticateMedecin() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "medecin", "", List.of("MEDECIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private static final class FakeUseCase implements ResultatAnalyseUseCase {
        private PagedResult<ResultatAnalyse> paged = PagedResult.of(List.of(), 0, 0, 20);
        private CenterId lastCenterId;
        private UUID lastPatientId;
        private int lastPage = -1;
        private int lastSize = -1;

        @Override
        public List<ResultatAnalyse> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return paged.items();
        }

        @Override
        public PagedResult<ResultatAnalyse> listPagedByPatient(CenterId centerId, UUID patientId,
                                                               LocalDate from, LocalDate to,
                                                               int page, int size) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastPage = page;
            this.lastSize = size;
            return paged;
        }

        @Override
        public ResultatAnalyse save(CenterId centerId, UUID patientId, UUID analyseId, LocalDate datePrelevement,
                                    BigDecimal hbGDl, BigDecimal htPct, Integer plaquettes, BigDecimal ferritineNgMl,
                                    BigDecimal cstfPct, BigDecimal epoEndogeneMuiMl, BigDecimal ureePreMgDl,
                                    BigDecimal ureePostMgDl, BigDecimal creatinineMgDl, BigDecimal ktVMensuel,
                                    BigDecimal phosphoreMgDl, BigDecimal calciumMgDl, BigDecimal pthPgMl,
                                    BigDecimal albumineGDl, BigDecimal proteinesGDl, BigDecimal crpMgL) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            ResultatAnalyse r = new ResultatAnalyse();
            r.setId(analyseId != null ? analyseId : UUID.randomUUID());
            r.setPatientId(patientId);
            r.setCenterId(centerId.value());
            r.setUpdatedAt(java.time.OffsetDateTime.now());
            return r;
        }

        @Override
        public void delete(CenterId centerId, UUID patientId, UUID analyseId) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
        }
    }
}
