package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.query.GreffePdfService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Export PDF du dossier de préparation à la greffe rénale (receveur + donneurs candidats),
 * destiné au transfert vers le centre de transplantation. Réservé au médecin.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class GreffeExportRestController {

    private final GreffePdfService pdfService;
    private final CenterAccessGuard centerAccessGuard;

    public GreffeExportRestController(GreffePdfService pdfService, CenterAccessGuard centerAccessGuard) {
        this.pdfService = pdfService;
        this.centerAccessGuard = centerAccessGuard;
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @GetMapping("/{patientId}/greffe/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID patientId,
                                            @RequestParam(required = false) UUID centerId) {
        CenterId center = centerAccessGuard.requireCenter(centerId);
        byte[] pdf = pdfService.exportPdf(center, patientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dossier-greffe-" + patientId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
