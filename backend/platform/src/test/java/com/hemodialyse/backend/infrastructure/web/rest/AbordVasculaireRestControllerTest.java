package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertAbordVasculaireRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AbordVasculaireRestControllerTest {

    @Test
    void list_should_return_items_from_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        AbordVasculaireRestController controller = new AbordVasculaireRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        AbordVasculaire abord = new AbordVasculaire();
        abord.setId(UUID.randomUUID());
        abord.setCenterId(centerId);
        abord.setPatientId(patientId);
        abord.setTypeAbord("FAV");
        useCase.list = List.of(abord);

        ResponseEntity<?> response = controller.list(patientId, centerId);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, body.size());
        assertEquals(abord.getId(), body.get(0).get("id"));
    }

    @Test
    void create_should_delegate_to_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        AbordVasculaireRestController controller = new AbordVasculaireRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UpsertAbordVasculaireRequest request = new UpsertAbordVasculaireRequest(
                centerId,
                "FAV",
                "GAUCHE",
                "Avant-bras",
                LocalDate.of(2026, 5, 1),
                null,
                true,
                null
        );

        ResponseEntity<?> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
        assertEquals("FAV", useCase.lastTypeAbord);
    }

    private static final class FakeUseCase implements AbordVasculaireUseCase {
        private List<AbordVasculaire> list = List.of();
        private CenterId lastCenterId;
        private UUID lastPatientId;
        private String lastTypeAbord;

        @Override
        public List<AbordVasculaire> listByPatient(CenterId centerId, UUID patientId) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return list;
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
    }
}

