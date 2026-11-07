package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLeDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.BundleFhir;
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
 * Adapter entrant HTTP de l'export FHIR (R4) du dossier médical : MÊME URL, MÊME contenu, MÊME droit que le legacy (MEDECIN seul, à sa
 * discrétion : partage vers un autre établissement). Écarts décidés (ADR 0087) : patient inconnu ou d'un autre centre 404
 * {@code PATIENT_INTROUVABLE} ; une fiche illisible est ignorée au lieu de faire échouer l'export (KB-MED-102). Reproduit : export non
 * journalisé (KB-MED-100), plafond silencieux de 500 observations et ordonnances (KB-MED-101, risque clinique).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class ExportFhirControleur {
    private final ExporterLeDossierMedical exporter;

    public ExportFhirControleur(ExporterLeDossierMedical exporter) {
        this.exporter = exporter;
    }

    @PreAuthorize("hasRole('MEDECIN')")
    @GetMapping("/{patientId}/dossier-medical/export-fhir")
    public ResponseEntity<Map<String, Object>> exporter(@PathVariable UUID patientId, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(BundleFhir.de(exporter.executer(centerId, patientId)));
    }
}
