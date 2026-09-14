package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.ObservanceAnemieQueryService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Observance en cours de la prescription EPO/fer d'un patient : doses attendues, administrées et
 * restantes sur la période prescrite en cours. Consulté par l'infirmier pendant la séance pour
 * savoir exactement ce qu'il reste à administrer avant l'échéance.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ObservanceAnemieRestController {

    private final ObservanceAnemieQueryService queryService;
    private final CenterAccessGuard centerAccessGuard;

    public ObservanceAnemieRestController(ObservanceAnemieQueryService queryService,
                                          CenterAccessGuard centerAccessGuard) {
        this.queryService = queryService;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN','INFIRMIER')")
    @GetMapping("/{patientId}/observance-anemie")
    public ResponseEntity<ObservanceAnemieQueryService.ObservanceAnemie> get(
            @PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(queryService.getObservanceActuelle(center.value(), patientId));
    }
}
