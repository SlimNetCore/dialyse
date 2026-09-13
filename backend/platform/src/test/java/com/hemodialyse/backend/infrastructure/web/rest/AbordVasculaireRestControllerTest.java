package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertAbordVasculaireRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.AbordVasculaireResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbordVasculaireRestControllerTest {

    private final CenterAccessGuard centerAccessGuard = new CenterAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_should_return_paged_items_from_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new AbordVasculaireRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        AbordVasculaire abord = new AbordVasculaire();
        abord.setId(UUID.randomUUID());
        abord.setCenterId(centerId);
        abord.setPatientId(patientId);
        abord.setTypeAbord("FAV");
        useCase.paged = PagedResult.of(List.of(abord), 1, 0, 20);

        ResponseEntity<PagedResponse<AbordVasculaireResponse>> response =
                controller.list(patientId, centerId, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        PagedResponse<AbordVasculaireResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.items().size());
        assertEquals(1, body.total());
        assertEquals(abord.getId(), body.items().get(0).id());
    }

    @Test
    void create_should_delegate_to_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new AbordVasculaireRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        UpsertAbordVasculaireRequest request = new UpsertAbordVasculaireRequest(
                centerId, "FAV", "GAUCHE", "Avant-bras", LocalDate.of(2026, 5, 1), null, true, null);

        ResponseEntity<EntityWriteResponse> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals("FAV", useCase.lastTypeAbord);
    }

    @Test
    void delete_should_delegate_to_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new AbordVasculaireRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        UUID abordId = UUID.randomUUID();

        ResponseEntity<Void> response = controller.delete(patientId, abordId, centerId);

        assertEquals(204, response.getStatusCode().value());
        assertTrue(useCase.deleted);
        assertEquals(abordId, useCase.lastDeletedAbordId);
    }

    @Test
    void list_should_be_forbidden_when_requesting_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new AbordVasculaireRestController(useCase, centerAccessGuard);

        UUID otherCenterId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class,
                () -> controller.list(UUID.randomUUID(), otherCenterId, 0, 20));
    }

    private UUID authenticateMedecin() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "medecin", "", List.of("MEDECIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private static final class FakeUseCase implements AbordVasculaireUseCase {
        private PagedResult<AbordVasculaire> paged = PagedResult.of(List.of(), 0, 0, 20);
        private CenterId lastCenterId;
        private UUID lastPatientId;
        private String lastTypeAbord;
        private boolean deleted;
        private UUID lastDeletedAbordId;

        @Override
        public List<AbordVasculaire> listByPatient(CenterId centerId, UUID patientId) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return paged.items();
        }

        @Override
        public PagedResult<AbordVasculaire> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return paged;
        }

        @Override
        public AbordVasculaire save(CenterId centerId, UUID patientId, UUID abordId, String typeAbord, String cote,
                                    String localisation, LocalDate dateCreation, LocalDate dateFin, Boolean actif,
                                    String complications) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastTypeAbord = typeAbord;
            AbordVasculaire abord = new AbordVasculaire();
            abord.setId(abordId != null ? abordId : UUID.randomUUID());
            abord.setPatientId(patientId);
            abord.setCenterId(centerId.value());
            return abord;
        }

        @Override
        public void delete(CenterId centerId, UUID patientId, UUID abordId) {
            this.deleted = true;
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastDeletedAbordId = abordId;
        }
    }
}
