package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.query.PatientListQueryService;
import com.hemodialyse.backend.application.query.PatientSummaryQueryService;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.patient.port.PatientUseCase;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PatientRestControllerSummaryTest {

    @Test
    void summary_should_delegate_to_summary_service() {
        PatientUseCase useCase = mock(PatientUseCase.class);
        NotificationService notificationService = mock(NotificationService.class);
        AssureRepositoryPort assureRepo = mock(AssureRepositoryPort.class);
        AssurePatientRepositoryPort assurePatientRepo = mock(AssurePatientRepositoryPort.class);
        PatientListQueryService listQueryService = mock(PatientListQueryService.class);
        PatientSummaryQueryService summaryQueryService = mock(PatientSummaryQueryService.class);

        PatientRestController controller = new PatientRestController(
                useCase,
                notificationService,
                assureRepo,
                assurePatientRepo,
                listQueryService,
                summaryQueryService
        );

        UUID centerId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 6);
        var expected = new PatientSummaryQueryService.PatientSummaryResponse(1,
                java.util.List.of(new PatientSummaryQueryService.SummaryBucket("M", "Masculin", 1)),
                java.util.List.of(),
                java.util.List.of());
        when(summaryQueryService.getSummary(centerId, month)).thenReturn(expected);

        var response = controller.summary(centerId, month.toString());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        verify(summaryQueryService).getSummary(centerId, month);
    }
}




