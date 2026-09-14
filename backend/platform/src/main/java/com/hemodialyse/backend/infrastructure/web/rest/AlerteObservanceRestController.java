package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.AlerteObservanceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Alertes d'observance : non-respect de la fréquence d'administration prescrite (EPO, fer
 * injectable) détecté par {@code ObservancePrescriptionScheduler}. Consultation et acquittement
 * réservés au corps médical.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class AlerteObservanceRestController {

    private final AlerteObservanceUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public AlerteObservanceRestController(AlerteObservanceUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @GetMapping("/{patientId}/alertes-observance")
    public ResponseEntity<List<AlerteObservanceResponse>> list(
            @PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        var alertes = useCase.listByPatient(center, patientId).stream()
                .map(AlerteObservanceResponse::from).toList();
        return ResponseEntity.ok(alertes);
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    @PutMapping("/{patientId}/alertes-observance/{alerteId}/resoudre")
    public ResponseEntity<AlerteObservanceResponse> resoudre(
            @PathVariable UUID patientId, @PathVariable UUID alerteId,
            @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(AlerteObservanceResponse.from(useCase.resoudre(center, patientId, alerteId)));
    }
}
