package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertPrescriptionMedicaleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PrescriptionMedicaleRestControllerTest {

    @Test
    void list_should_return_items_from_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        PrescriptionMedicaleRestController controller = new PrescriptionMedicaleRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        PrescriptionMedicale p = new PrescriptionMedicale();
        p.setId(UUID.randomUUID());
        p.setCenterId(centerId);
        p.setPatientId(patientId);
        p.setDatePrescription(LocalDate.of(2026, 5, 1));
        useCase.list = List.of(p);

        ResponseEntity<?> response = controller.list(patientId, centerId, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, body.size());
        assertEquals(p.getId(), body.get(0).get("id"));
    }

    @Test
    void create_should_delegate_to_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        PrescriptionMedicaleRestController controller = new PrescriptionMedicaleRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID medecinId = UUID.randomUUID();

        UpsertPrescriptionMedicaleRequest request = new UpsertPrescriptionMedicaleRequest(
                centerId,
                LocalDate.of(2026, 5, 1),
                medecinId,
                300,
                500,
                2500,
                240,
                "FX-80",
                "HNF",
                "Darbepoetine",
                60,
                "SC",
                "1x/sem",
                "Fer saccharose",
                100,
                "IV",
                "1x/sem"
        );

        ResponseEntity<?> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(medecinId, useCase.lastMedecinId);
    }

    @Test
    void list_should_return_items_from_use_case_with_filters() {
        FakeUseCase useCase = new FakeUseCase();
        PrescriptionMedicaleRestController controller = new PrescriptionMedicaleRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        PrescriptionMedicale p = new PrescriptionMedicale();
        p.setId(UUID.randomUUID());
        p.setCenterId(centerId);
        p.setPatientId(patientId);
        p.setDatePrescription(LocalDate.of(2026, 5, 1));
        useCase.list = List.of(p);

        ResponseEntity<?> response = controller.list(patientId, centerId, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, body.size());
        assertEquals(p.getId(), body.get(0).get("id"));
    }

    @Test
    void delete_should_delegate_to_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        PrescriptionMedicaleRestController controller = new PrescriptionMedicaleRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        ResponseEntity<?> response = controller.delete(patientId, prescriptionId, centerId);

        assertEquals(204, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals(prescriptionId, useCase.lastDeletedId);
    }

    private static final class FakeUseCase implements PrescriptionMedicaleUseCase {
        private List<PrescriptionMedicale> list = List.of();
        private CenterId lastCenterId;
        private UUID lastPatientId;
        private UUID lastMedecinId;
        private UUID lastDeletedId;

        @Override
        public List<PrescriptionMedicale> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return list;
        }

        @Override
        public PrescriptionMedicale save(CenterId centerId, UUID patientId, UUID prescriptionId,
                                         LocalDate datePrescription, UUID medecinId, Integer qbCible,
                                         Integer qdCible, Integer ufMaxMl, Integer dureeCibleMin,
                                         String typeDialyseurPrescrit, String anticoagTypePrescrit,
                                         String epoMolecule, Integer epoDoseUi, String epoVoie,
                                         String epoFrequence, String ferMolecule, Integer ferDoseMg,
                                         String ferVoie, String ferFrequence) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            this.lastMedecinId = medecinId;
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
}

