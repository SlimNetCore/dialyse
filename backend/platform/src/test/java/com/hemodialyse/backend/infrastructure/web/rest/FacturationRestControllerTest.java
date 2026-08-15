package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import com.hemodialyse.backend.infrastructure.web.dto.request.FacturationPreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FacturationRestControllerTest {

    @Test
    void preview_should_delegate_with_center_scope() {
        FacturationUseCase useCase = mock(FacturationUseCase.class);
        FacturationRestController controller = new FacturationRestController(useCase);
        UUID centerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(useCase.preview(any())).thenReturn(new FacturationPreviewResult(
                centerId,
                OffsetDateTime.now(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of()
        ));

        ResponseEntity<?> response = controller.preview(new FacturationPreviewRequest(centerId, "2026-08", null, null, true));

        assertEquals(200, response.getStatusCode().value());
        verify(useCase).preview(argThat(q -> q.centerId().value().equals(centerId) && YearMonth.of(2026, 8).equals(q.month())));
    }

    @Test
    void settings_should_return_saved_payload() {
        FacturationUseCase useCase = mock(FacturationUseCase.class);
        FacturationRestController controller = new FacturationRestController(useCase);
        UUID centerId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(useCase.updateSettings(any())).thenReturn(new ParametresFacturation(
                "FAC-{YEAR}-{SEQ}",
                false,
                OffsetDateTime.now()
        ));

        var request = new com.hemodialyse.backend.infrastructure.web.dto.request.FacturationSettingsUpdateRequest(
                centerId,
                "admin",
                "FAC-{YEAR}-{SEQ}",
                false
        );
        ResponseEntity<?> response = controller.updateSettings(request);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(false, body.get("regroupementMultiForfait"));
    }
}
