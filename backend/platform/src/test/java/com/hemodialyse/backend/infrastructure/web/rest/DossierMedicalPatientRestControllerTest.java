package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertDossierMedicalPatientRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.DossierMedicalPatientResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EntityWriteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DossierMedicalPatientRestControllerTest {

    private final CenterAccessGuard centerAccessGuard = new CenterAccessGuard();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_should_return_404_when_missing() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new DossierMedicalPatientRestController(useCase, centerAccessGuard);

        ResponseEntity<DossierMedicalPatientResponse> response = controller.get(UUID.randomUUID(), centerId);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void create_should_delegate_to_use_case() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new DossierMedicalPatientRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        UpsertDossierMedicalPatientRequest request = new UpsertDossierMedicalPatientRequest(
                centerId, "GNMP", LocalDate.of(2020, 1, 1), "NEGATIF", "INCONNU", "RAS");

        ResponseEntity<EntityWriteResponse> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
    }

    @Test
    void get_should_return_body_when_found() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new DossierMedicalPatientRestController(useCase, centerAccessGuard);

        UUID patientId = UUID.randomUUID();
        DossierMedicalPatient d = new DossierMedicalPatient();
        d.setId(UUID.randomUUID());
        d.setCenterId(centerId);
        d.setPatientId(patientId);
        d.setNephropathieInitiale("GNMP");
        useCase.current = Optional.of(d);

        ResponseEntity<DossierMedicalPatientResponse> response = controller.get(patientId, centerId);

        assertEquals(200, response.getStatusCode().value());
        DossierMedicalPatientResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(d.getId(), body.id());
        assertEquals("GNMP", body.nephropathieInitiale());
    }

    @Test
    void get_should_be_forbidden_when_requesting_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new DossierMedicalPatientRestController(useCase, centerAccessGuard);

        UUID otherCenterId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> controller.get(UUID.randomUUID(), otherCenterId));
    }

    @Test
    void create_should_be_forbidden_when_body_targets_another_center() {
        authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new DossierMedicalPatientRestController(useCase, centerAccessGuard);

        UpsertDossierMedicalPatientRequest request = new UpsertDossierMedicalPatientRequest(
                UUID.randomUUID(), "GNMP", LocalDate.of(2020, 1, 1), "NEGATIF", "INCONNU", "RAS");

        assertThrows(AccessDeniedException.class, () -> controller.create(UUID.randomUUID(), request));
    }

    @Test
    void get_should_fall_back_to_session_center_when_not_provided() {
        UUID centerId = authenticateMedecin();
        FakeUseCase useCase = new FakeUseCase();
        var controller = new DossierMedicalPatientRestController(useCase, centerAccessGuard);

        controller.get(UUID.randomUUID(), null);

        assertEquals(centerId, useCase.lastCenterId.value());
    }

    /**
     * Place un MEDECIN authentifié dans le contexte de sécurité et retourne son centre.
     */
    private UUID authenticateMedecin() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "medecin", "", List.of("MEDECIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private static final class FakeUseCase implements DossierMedicalPatientUseCase {
        private Optional<DossierMedicalPatient> current = Optional.empty();
        private CenterId lastCenterId;
        private UUID lastPatientId;

        @Override
        public Optional<DossierMedicalPatient> getByPatient(CenterId centerId, UUID patientId) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return current;
        }

        @Override
        public DossierMedicalPatient upsert(CenterId centerId, UUID patientId, String nephropathieInitiale,
                                            LocalDate dateMiseEnDialyse, String hepatiteBStatut,
                                            String hepatiteCStatut, String observationGlobale) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            DossierMedicalPatient d = new DossierMedicalPatient();
            d.setId(UUID.randomUUID());
            d.setPatientId(patientId);
            d.setCenterId(centerId.value());
            d.setUpdatedAt(java.time.OffsetDateTime.now());
            return d;
        }
    }
}
