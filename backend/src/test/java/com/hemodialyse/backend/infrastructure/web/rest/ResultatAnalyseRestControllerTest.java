package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpsertResultatAnalyseRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ResultatAnalyseRestControllerTest {

    @Test
    void list_should_return_items_from_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        ResultatAnalyseRestController controller = new ResultatAnalyseRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        ResultatAnalyse r = new ResultatAnalyse();
        r.setId(UUID.randomUUID());
        r.setCenterId(centerId);
        r.setPatientId(patientId);
        r.setDatePrelevement(LocalDate.of(2026, 5, 1));
        useCase.list = List.of(r);

        ResponseEntity<?> response = controller.list(patientId, centerId, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertInstanceOf(List.class, response.getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, body.size());
        assertEquals(r.getId(), body.get(0).get("id"));
    }

    @Test
    void create_should_delegate_to_use_case() {
        FakeUseCase useCase = new FakeUseCase();
        ResultatAnalyseRestController controller = new ResultatAnalyseRestController(useCase);

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        UpsertResultatAnalyseRequest request = new UpsertResultatAnalyseRequest(
                centerId,
                LocalDate.of(2026, 5, 1),
                new BigDecimal("10.5"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        ResponseEntity<?> response = controller.create(patientId, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(centerId, useCase.lastCenterId.value());
        assertEquals(patientId, useCase.lastPatientId);
    }

    private static final class FakeUseCase implements ResultatAnalyseUseCase {
        private List<ResultatAnalyse> list = List.of();
        private CenterId lastCenterId;
        private UUID lastPatientId;

        @Override
        public List<ResultatAnalyse> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
            this.lastCenterId = centerId;
            this.lastPatientId = patientId;
            return list;
        }

        @Override
        public ResultatAnalyse save(CenterId centerId, UUID patientId, UUID analyseId, LocalDate datePrelevement,
                                    BigDecimal hbGDl, BigDecimal htPct, Integer plaquettes,
                                    BigDecimal ferritineNgMl, BigDecimal cstfPct, BigDecimal epoEndogeneMuiMl,
                                    BigDecimal ureePreMgDl, BigDecimal ureePostMgDl, BigDecimal creatinineMgDl,
                                    BigDecimal ktVMensuel, BigDecimal phosphoreMgDl, BigDecimal calciumMgDl,
                                    BigDecimal pthPgMl, BigDecimal albumineGDl, BigDecimal proteinesGDl,
                                    BigDecimal crpMgL) {
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

