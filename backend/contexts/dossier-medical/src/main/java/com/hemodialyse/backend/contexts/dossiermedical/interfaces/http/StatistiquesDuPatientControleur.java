package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesStatistiquesMedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesStatistiquesParamedicales;
import com.hemodialyse.backend.contexts.dossiermedical.application.ExportDesStatistiques;
import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLesStatistiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.RechercheDeStatistiquesDemande;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.StatistiquesReponses;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP des statistiques d'un patient (PL4, ex {@code PatientStatsRestController}) : MEMES six URL, MEMES droits (volets :
 * ADMIN, INFIRMIER, MEDECIN, SECRETAIRE ; exports : ADMIN seul), MEMES champs JSON et MEMES codes d'erreur. Ecarts decides (ADR 0139) :
 * entree illisible 400 {@code BAD_REQUEST} et non 500 (KB-PL4-1), garde de centre 403 AVANT la validation de la periode (KB-PL4-5), patient
 * inconnu ou d'un autre centre 404 {@code PATIENT_INTROUVABLE} (KB-PL4-2). Reproduit : tension illisible rendue nulle (KB-PL4-3). La
 * consultation par corps ({@code POST ... /search}) est journalisee sous une action de lecture propre (KB-PL4-4, resolue par la plateforme).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class StatistiquesDuPatientControleur {

    private static final String MESSAGE_DE_VALIDATION = "Invalid request content.";

    private final ConsulterLesStatistiquesParamedicales paramedicales;
    private final ConsulterLesStatistiquesMedicales medicales;
    private final ExporterLesStatistiquesDuPatient exporter;

    public StatistiquesDuPatientControleur(ConsulterLesStatistiquesParamedicales paramedicales, ConsulterLesStatistiquesMedicales medicales,
                                           ExporterLesStatistiquesDuPatient exporter) {
        this.paramedicales = paramedicales;
        this.medicales = medicales;
        this.exporter = exporter;
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/stats/paramedical")
    public ResponseEntity<?> paramedical(@PathVariable UUID patientId, @RequestParam UUID centerId, @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(StatistiquesReponses.paramedicales(paramedicales.executer(centerId, patientId, from, to)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @PostMapping("/stats/paramedical/search")
    public ResponseEntity<?> rechercherParamedical(@RequestBody RechercheDeStatistiquesDemande d) {
        exiger(d);
        return ResponseEntity.ok(StatistiquesReponses.paramedicales(paramedicales.executer(d.centerId(), d.patientId(), d.dateFrom(), d.dateTo())));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @GetMapping("/{patientId}/stats/medical")
    public ResponseEntity<?> medical(@PathVariable UUID patientId, @RequestParam UUID centerId, @RequestParam(required = false) LocalDate from,
                                     @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(StatistiquesReponses.medicales(medicales.executer(centerId, patientId, from, to)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','INFIRMIER','MEDECIN','SECRETAIRE')")
    @PostMapping("/stats/medical/search")
    public ResponseEntity<?> rechercherMedical(@RequestBody RechercheDeStatistiquesDemande d) {
        exiger(d);
        return ResponseEntity.ok(StatistiquesReponses.medicales(medicales.executer(d.centerId(), d.patientId(), d.dateFrom(), d.dateTo())));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{patientId}/stats/export")
    public ResponseEntity<?> exporter(@PathVariable UUID patientId, @RequestParam UUID centerId, @RequestParam String format,
                                      @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return telechargement(patientId, exporter.executer(centerId, patientId, from, to, format));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/stats/export/search")
    public ResponseEntity<?> rechercherPourExporter(@RequestBody RechercheDeStatistiquesDemande d) {
        exiger(d);
        return telechargement(d.patientId(), exporter.executer(d.centerId(), d.patientId(), d.dateFrom(), d.dateTo(), d.format()));
    }

    /** Centre et patient du corps obligatoires : 400 {@code VALIDATION_ERROR}, comme la validation de formulaire du legacy (code reproduit). */
    private static void exiger(RechercheDeStatistiquesDemande d) {
        if (d == null || d.centerId() == null || d.patientId() == null) throw new ValeurHorsBorne(MESSAGE_DE_VALIDATION);
    }

    private static ResponseEntity<?> telechargement(UUID patient, ExportDesStatistiques export) {
        return switch (export.format()) {
            case CSV -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient-stats-" + patient + ".csv")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(export.csv());
            case PDF -> ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=patient-stats-" + patient + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(export.pdf());
        };
    }
}
