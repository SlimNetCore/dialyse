package com.hemodialyse.backend.infrastructure.web.dto;

import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervention;
import com.hemodialyse.backend.infrastructure.web.dto.request.gmao.TerminerInterventionRequest;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.InterventionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convention : les dates GMAO sont des instants UTC ({@code ...Z}) en sortie ; en entrée, tout décalage
 * horaire envoyé par le navigateur est ramené à UTC.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class GmaoUtcSerializationIntegrationTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    void responses_should_expose_dates_as_utc_instants() {
        OffsetDateTime debut = OffsetDateTime.of(2026, 3, 10, 8, 30, 0, 0, ZoneOffset.UTC);
        Intervention intervention = Intervention.creer(UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                debut, "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());

        String json = mapper.writeValueAsString(new InterventionResponse(intervention));

        assertTrue(json.contains("\"dateDebut\":\"2026-03-10T08:30:00Z\""), json);
        assertEquals(ZoneOffset.UTC, intervention.getDateCreation().getOffset());
    }

    @Test
    void requests_should_normalise_any_browser_offset_to_utc() {
        String json = "{\"actions\":\"Fait\",\"etatEquipementApres\":\"EN_SERVICE\","
                + "\"dateFin\":\"2026-03-10T10:30:00+01:00\"}";

        TerminerInterventionRequest request = mapper.readValue(json, TerminerInterventionRequest.class);

        assertEquals(OffsetDateTime.of(2026, 3, 10, 9, 30, 0, 0, ZoneOffset.UTC).toInstant(),
                request.dateFin().toInstant());
        assertEquals(ZoneOffset.UTC, request.dateFin().getOffset());
    }
}
