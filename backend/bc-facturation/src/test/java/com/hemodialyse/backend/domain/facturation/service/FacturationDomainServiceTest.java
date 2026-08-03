package com.hemodialyse.backend.domain.facturation.service;

import com.hemodialyse.backend.domain.facturation.aggregate.FactureAggregate;
import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FacturationDomainServiceTest {

    @Test
    void preview_should_group_by_patient_when_switch_on() {
        SeanceFacturationPort seancePort = mock(SeanceFacturationPort.class);
        FactureRepositoryPort factureRepository = mock(FactureRepositoryPort.class);
        FacturationSettingsRepositoryPort settingsRepository = mock(FacturationSettingsRepositoryPort.class);
        FacturationDomainService service = new FacturationDomainService(seancePort, factureRepository, settingsRepository);

        CenterId centerId = CenterId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(settingsRepository.findByCenterId(centerId)).thenReturn(ParametresFacturation.defaults());
        when(seancePort.findEligibleSeances(any(), any())).thenReturn(List.of(
                candidate(centerId.value(), UUID.fromString("30000000-0000-0000-0000-000000000001"), UUID.fromString("40000000-0000-0000-0000-000000000001"), "F1", new BigDecimal("3500")),
                candidate(centerId.value(), UUID.fromString("30000000-0000-0000-0000-000000000002"), UUID.fromString("40000000-0000-0000-0000-000000000001"), "F2", new BigDecimal("4200"))
        ));

        FacturationPreviewResult result = service.preview(new FacturationPreviewQuery(centerId, YearMonth.of(2026, 8), null, null, true));

        assertEquals(1, result.totalFactures());
        assertEquals(new BigDecimal("7700.00"), result.totalHt());
    }

    @Test
    void validate_should_mark_seances_with_same_center_only() {
        SeanceFacturationPort seancePort = mock(SeanceFacturationPort.class);
        FactureRepositoryPort factureRepository = mock(FactureRepositoryPort.class);
        FacturationSettingsRepositoryPort settingsRepository = mock(FacturationSettingsRepositoryPort.class);
        FacturationDomainService service = new FacturationDomainService(seancePort, factureRepository, settingsRepository);

        CenterId centerId = CenterId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(settingsRepository.findByCenterId(centerId)).thenReturn(ParametresFacturation.defaults());
        when(seancePort.findEligibleSeances(any(), any())).thenReturn(List.of(
                candidate(centerId.value(), UUID.fromString("30000000-0000-0000-0000-000000000010"), UUID.fromString("40000000-0000-0000-0000-000000000010"), "F1", new BigDecimal("3500"))
        ));
        when(factureRepository.nextInvoiceNumber(any(), any(), any())).thenReturn("FAC-2026-0001");

        FacturationValidationResult result = service.validate(new FacturationValidateCommand(
                centerId,
                "admin",
                YearMonth.of(2026, 8),
                null,
                null,
                true,
                "2026-08-03T00:00:00Z"
        ));

        assertEquals(1, result.createdInvoices());
        assertEquals(1, result.billedSeances());
        verify(factureRepository).saveAll(anyList());
        verify(seancePort).markAsBilled(eq(centerId), anyMap());
    }

    private SeanceFacturationCandidate candidate(UUID centerId, UUID seanceId, UUID patientId, String forfait, BigDecimal price) {
        return new SeanceFacturationCandidate(
                seanceId,
                centerId,
                patientId,
                LocalDate.of(2026, 8, 2),
                "SIGNEE",
                null,
                "PAT-001",
                "Dupont",
                "Nadia",
                "VACANCIER",
                "IMM-1001",
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                UUID.fromString("60000000-0000-0000-0000-000000000001"),
                "CNAS",
                UUID.nameUUIDFromBytes(forfait.getBytes()),
                forfait,
                price
        );
    }
}

