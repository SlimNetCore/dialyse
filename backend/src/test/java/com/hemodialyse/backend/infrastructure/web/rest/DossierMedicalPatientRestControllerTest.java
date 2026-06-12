package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertDossierMedicalPatientRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DossierMedicalPatientRestControllerTest {

    @Test
    void get_should_return_404_when_missing() {
        FakeUseCase useCase = new FakeUseCase();
        DossierMedicalPatientRestController controller = new DossierMedicalPatientRestController(useCase);

        ResponseEntity<?> response = controller.get(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void create_should_delegate_to_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        DossierMedicalPatientRestController controller = new DossierMedicalPatientRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        UpsertDossierMedicalPatientRequest request = new UpsertDossierMedicalPatientRequest(
                centerId,
                "GNMP",
                LocalDate.of(2020, 1, 1),
                "NEGATIF",
                "INCONNU",
                "RAS"
        );

        ResponseEntity<?> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void get_should_return_body_when_found() {
        FakeUseCase useCase = new FakeUseCase();
        DossierMedicalPatientRestController controller = new DossierMedicalPatientRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        DossierMedicalPatient d = new DossierMedicalPatient();
        d.setId(UUID.randomUUID());
        d.setCenterId(centerId);
        d.setPatientId(patientId);
        d.setNephropathieInitiale("GNMP");
        useCase.current = Optional.of(d);

        ResponseEntity<?> response = controller.get(patientId, centerId);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(d.getId(), body.get("id"));
        assertEquals("GNMP", body.get("nephropathieInitiale"));
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

