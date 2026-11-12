package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLeDossierDeGreffe;
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
 * Adapter entrant HTTP de l'export PDF du dossier de préparation à la greffe rénale (PL2, ex {@code GreffeExportRestController}) :
 * MÊME URL, mêmes droits (MEDECIN seul), mêmes en-têtes, même contenu. Patient inconnu ou d'un autre centre : 404
 * {@code PATIENT_INTROUVABLE} (DM7 KB-MED-86 / 97).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class GreffeExportControleur {

    private final ExporterLeDossierDeGreffe exporter;

    public GreffeExportControleur(ExporterLeDossierDeGreffe exporter) {
        this.exporter = exporter;
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @GetMapping("/{patientId}/greffe/export-pdf")
    public ResponseEntity<byte[]> exporterEnPdf(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        byte[] pdf = exporter.executer(centerId, patientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dossier-greffe-" + patientId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
