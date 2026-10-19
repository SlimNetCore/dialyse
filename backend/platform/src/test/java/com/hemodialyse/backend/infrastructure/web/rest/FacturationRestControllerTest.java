package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.infrastructure.reporting.FacturationSyntheseMensuelleReportService;
import com.hemodialyse.backend.infrastructure.web.dto.request.FacturationPreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Routes de facturation restées au legacy. Les paramètres ({@code /settings}) sont servis par le contexte
 * {@code facturation} depuis la slice F1 (filet {@code ParametresFacturationCharacterizationIntegrationTest}).
 */
class FacturationRestControllerTest {

    @Test
    void preview_should_delegate_with_center_scope() {
        FacturationUseCase useCase = mock(FacturationUseCase.class);
        FacturationRestController controller = new FacturationRestController(useCase, mock(FacturationSyntheseMensuelleReportService.class));
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
}
