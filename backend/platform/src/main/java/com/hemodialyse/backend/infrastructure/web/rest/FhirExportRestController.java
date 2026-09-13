package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.fhir.FhirExportService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Export FHIR (R4) en lecture seule du dossier médical patient — réservé au médecin, à sa
 * discrétion (partage vers un autre établissement, interopérabilité).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class FhirExportRestController {

    private final FhirExportService exportService;
    private final CenterAccessGuard centerAccessGuard;

    public FhirExportRestController(FhirExportService exportService, CenterAccessGuard centerAccessGuard) {
        this.exportService = exportService;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @GetMapping("/{patientId}/dossier-medical/export-fhir")
    public ResponseEntity<Map<String, Object>> exportFhir(@PathVariable UUID patientId,
                                                          @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        return ResponseEntity.ok(exportService.exportBundle(center, patientId));
    }
}
