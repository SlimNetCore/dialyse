package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.CloturerUnePeriode;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.EcritureVue;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.ExporterUnJournal;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.ListerLesEcritures;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.ValiderUneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
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
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur HTTP entrant des écritures, exports et clôtures (slice C3).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class EcrituresControleur {
    private final SessionCourante session;
    private final ListerLesEcritures lister;
    private final ValiderUneEcriture valider;
    private final ExporterUnJournal exporter;
    private final CloturerUnePeriode cloturer;

    public EcrituresControleur(SessionCourante session, ListerLesEcritures lister, ValiderUneEcriture valider,
                               ExporterUnJournal exporter, CloturerUnePeriode cloturer) {
        this.session = session;
        this.lister = lister;
        this.valider = valider;
        this.exporter = exporter;
        this.cloturer = cloturer;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/ecritures")
    public ResponseEntity<PageReponse<EcritureVue>> rechercher(
            @RequestParam(required = false) UUID centerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String journalCode,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CodeJournal journal = journalCode != null && !journalCode.isBlank() ? CodeJournal.de(journalCode) : null;
        StatutEcriture statutEcriture = statut != null ? StatutEcriture.depuis(statut) : null;
        return ResponseEntity.ok(PageReponse.depuis(lister.executer(session.centre(centerId), LocalDate.parse(from),
                LocalDate.parse(to), journal, statutEcriture, page, size), valeur -> valeur));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/ecritures/{id}/valider")
    public ResponseEntity<EcritureVue> valider(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(valider.executer(session.centre(centerId), id));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/ecritures/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) UUID centerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "VE") String journalCode,
            @RequestParam(defaultValue = "SAGE100") String format) {
        CodeJournal journal = CodeJournal.de(journalCode);
        byte[] contenu = exporter.executer(session.centre(centerId), LocalDate.parse(from), LocalDate.parse(to), journal);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("journal-" + journal + "-" + from + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(contenu);
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/periodes/cloturer")
    public ResponseEntity<Map<String, Object>> cloturer(@RequestBody @Valid ClotureRequete requete) {
        cloturer.executer(session.centre(requete.centerId()), YearMonth.of(requete.annee(), requete.mois()), requete.userId());
        return ResponseEntity.ok(Map.of("closed", true, "periode", requete.annee() + "-" + requete.mois()));
    }

    public record ClotureRequete(UUID centerId, @Min(2000) @Max(3000) int annee,
                                 @Min(1) @Max(12) int mois, @NotBlank String userId) {
    }
}


