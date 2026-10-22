package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.application.ListerLesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.ListerLesSeancesDeRattrapage;
import com.hemodialyse.backend.contexts.absencespatients.application.ObtenirLaSynthese;
import com.hemodialyse.backend.contexts.absencespatients.application.SessionCourante;
import com.hemodialyse.backend.contexts.absencespatients.application.SuivreLaSemaine;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP de la consultation du suivi des absences (slice A1) : mêmes URLs et même JSON que le legacy. Les
 * écritures (déclaration, qualification, rattrapage, annulation) restent sur les mêmes URLs, dans le legacy, jusqu'à A2.
 * <p>
 * Écarts décidés (22/10/2026) : un paramètre illisible donne 400 (était 500), une page ou une taille négative 400, une
 * taille au-delà de {@value Pagination#TAILLE_MAXIMALE} est plafonnée, une absence inconnue ou d'un autre centre 404 (était
 * 422), et les séances de rattrapage d'une absence rattrapée ou annulée 422 {@code ABSENCE_NON_MODIFIABLE}.
 */
@RestController
@RequestMapping("/api/v1/absences-patients")
public class AbsencesControleur {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";

    private final SessionCourante session;
    private final ListerLesAbsences lister;
    private final SuivreLaSemaine suivreLaSemaine;
    private final ObtenirLaSynthese obtenirLaSynthese;
    private final ListerLesSeancesDeRattrapage listerLesSeancesDeRattrapage;

    public AbsencesControleur(SessionCourante session, ListerLesAbsences lister, SuivreLaSemaine suivreLaSemaine,
                              ObtenirLaSynthese obtenirLaSynthese,
                              ListerLesSeancesDeRattrapage listerLesSeancesDeRattrapage) {
        this.session = session;
        this.lister = lister;
        this.suivreLaSemaine = suivreLaSemaine;
        this.obtenirLaSynthese = obtenirLaSynthese;
        this.listerLesSeancesDeRattrapage = listerLesSeancesDeRattrapage;
    }

    @GetMapping
    @PreAuthorize(ACCES)
    public ResponseEntity<PageDAbsencesReponse> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) StatutAbsence statut,
            @RequestParam(required = false) MotifAbsence motif,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        var filtre = new FiltreDesAbsences(statut, motif, patientId == null ? null : new PatientId(patientId), from, to);
        var resultat = lister.executer(centre, filtre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageDAbsencesReponse.depuis(resultat));
    }

    /**
     * Suivi de la semaine du planning (grille bornée à 7 jours, donc non paginée) : absences et séances réalisées, qui
     * colorent les séances du planning.
     */
    @GetMapping("/semaine")
    @PreAuthorize(ACCES)
    public ResponseEntity<SuiviDeLaSemaineReponse> semaine(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(SuiviDeLaSemaineReponse.depuis(suivreLaSemaine.executer(centre, date)));
    }

    @GetMapping("/synthese")
    @PreAuthorize(ACCES)
    public ResponseEntity<SyntheseReponse> synthese(@RequestParam(required = false) UUID centerId) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(SyntheseReponse.depuis(obtenirLaSynthese.executer(centre)));
    }

    /**
     * Dates des séances réalisées qui peuvent rattraper cette absence (après la séance manquée, pas déjà utilisées).
     */
    @GetMapping("/{id}/seances-rattrapage")
    @PreAuthorize(ACCES)
    public ResponseEntity<List<LocalDate>> seancesDeRattrapage(@RequestParam(required = false) UUID centerId,
                                                               @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(listerLesSeancesDeRattrapage.executer(centre, new AbsenceId(id)));
    }
}
