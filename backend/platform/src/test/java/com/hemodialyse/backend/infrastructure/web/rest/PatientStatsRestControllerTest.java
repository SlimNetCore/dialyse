package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.PatientStatsQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PatientStatsRestControllerTest {

    @Test
    void paramedical_should_return_payload() {
        FakeStatsQueryService query = new FakeStatsQueryService();
        PatientStatsRestController controller = new PatientStatsRestController(query);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        ResponseEntity<?> response = controller.paramedical(patientId, centerId, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(Map.class, response.getBody());
        assertEquals(centerId, query.lastCenterId);
        assertEquals(patientId, query.lastPatientId);
    }

    @Test
    void medical_should_return_400_on_invalid_range() {
        FakeStatsQueryService query = new FakeStatsQueryService();
        PatientStatsRestController controller = new PatientStatsRestController(query);

        ResponseEntity<?> response = controller.medical(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 5, 1)
        );

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void export_should_return_csv() {
        FakeStatsQueryService query = new FakeStatsQueryService();
        PatientStatsRestController controller = new PatientStatsRestController(query);

        ResponseEntity<?> response = controller.export(UUID.randomUUID(), UUID.randomUUID(), "csv", null, null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(String.valueOf(response.getBody()).contains("date_prelevement"));
    }

    @Test
    void export_should_return_pdf() {
        FakeStatsQueryService query = new FakeStatsQueryService();
        PatientStatsRestController controller = new PatientStatsRestController(query);

        ResponseEntity<?> response = controller.export(UUID.randomUUID(), UUID.randomUUID(), "pdf", null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertInstanceOf(byte[].class, response.getBody());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, (byte[]) response.getBody());
    }

    @Test
    void export_should_return_400_on_unknown_format() {
        FakeStatsQueryService query = new FakeStatsQueryService();
        PatientStatsRestController controller = new PatientStatsRestController(query);

        ResponseEntity<?> response = controller.export(UUID.randomUUID(), UUID.randomUUID(), "xml", null, null);

        assertEquals(400, response.getStatusCode().value());
    }

    private static final class FakeStatsQueryService extends PatientStatsQueryService {
        private UUID lastCenterId;
        private UUID lastPatientId;

        private FakeStatsQueryService() {
            super(null);
        }

        @Override
        public Map<String, Object> getParamedicalStats(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("seanceCount", 0);
            out.put("poidsEvolution", java.util.List.of());
            return out;
        }

        @Override
        public Map<String, Object> getMedicalStats(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("avgHbGDl", 0.0);
            out.put("hbTrend", java.util.List.of());
            return out;
        }

        @Override
        public String exportCsv(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return "date_prelevement,hb_g_dl\n";
        }

        @Override
        public byte[] exportPdf(UUID centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return new byte[]{1, 2, 3, 4};
        }
    }
}


