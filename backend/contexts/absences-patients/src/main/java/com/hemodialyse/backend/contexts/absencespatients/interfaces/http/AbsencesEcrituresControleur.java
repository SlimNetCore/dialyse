package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.AnnulerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.DeclarerUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.Intervenant;
import com.hemodialyse.backend.contexts.absencespatients.application.QualifierUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.RattraperUneAbsence;
import com.hemodialyse.backend.contexts.absencespatients.application.SessionCourante;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto.AnnulationDemande;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto.DeclarationDemande;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto.QualificationDemande;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto.RattrapageDemande;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP du cycle de vie des absences (slice A2) : mêmes URLs et même JSON que le legacy. Déclaration pour
 * l'administrateur, le secrétariat et l'infirmier (le médecin consulte et corrige, mais ne déclare pas) ; qualification,
 * rattrapage et annulation pour les quatre profils d'accès, le droit de correction (administrateur, médecin) étant calculé par
 * la session et jugé par le domaine.
 * <p>
 * Écarts décidés (22/10/2026) : entrées illisibles 400 (415 pour un corps non JSON) au lieu de 500, absence inconnue 404, absence
 * rattrapée non annulable, rattrapage réservé au droit de correction, commentaire sans motif conservé, déclaration simultanée
 * perdante 409.
 */
@RestController
@RequestMapping("/api/v1/absences-patients")
public class AbsencesEcrituresControleur {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";
    private static final String DECLARATION = "hasAnyRole('ADMIN','SECRETAIRE','INFIRMIER')";

    private final SessionCourante session;
    private final DeclarerUneAbsence declarer;
    private final QualifierUneAbsence qualifier;
    private final RattraperUneAbsence rattraper;
    private final AnnulerUneAbsence annuler;

    public AbsencesEcrituresControleur(SessionCourante session, DeclarerUneAbsence declarer, QualifierUneAbsence qualifier,
                                       RattraperUneAbsence rattraper, AnnulerUneAbsence annuler) {
        this.session = session;
        this.declarer = declarer;
        this.qualifier = qualifier;
        this.rattraper = rattraper;
        this.annuler = annuler;
    }

    private static MotifAbsence motif(String nom) {
        try {
            return MotifAbsence.valueOf(nom);
        } catch (IllegalArgumentException e) {
            throw new SaisieInvalide("Motif inconnu : " + nom);
        }
    }

    @PostMapping
    @PreAuthorize(DECLARATION)
    public ResponseEntity<AbsenceReponse> declarer(@RequestParam(required = false) UUID centerId,
                                                   @Valid @RequestBody DeclarationDemande demande) {
        CentreId centre = session.centre(centerId);
        Intervenant intervenant = session.intervenant();
        var ligne = declarer.executer(centre, new DeclarerUneAbsence.Commande(new PatientId(demande.patientId()),
                demande.dateSeance(), demande.motif() == null ? null : motif(demande.motif()), demande.commentaire()),
                intervenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceReponse.depuis(ligne));
    }

    @PutMapping("/{id}/qualification")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceReponse> qualifier(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                    @Valid @RequestBody QualificationDemande demande) {
        CentreId centre = session.centre(centerId);
        var ligne = qualifier.executer(centre, new AbsenceId(id), motif(demande.motif()), demande.commentaire(),
                session.intervenant());
        return ResponseEntity.ok(AbsenceReponse.depuis(ligne));
    }

    @PutMapping("/{id}/rattrapage")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceReponse> rattraper(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                    @Valid @RequestBody RattrapageDemande demande) {
        CentreId centre = session.centre(centerId);
        var ligne = rattraper.executer(centre, new AbsenceId(id), demande.dateRattrapage(), session.intervenant());
        return ResponseEntity.ok(AbsenceReponse.depuis(ligne));
    }

    @PutMapping("/{id}/annulation")
    @PreAuthorize(ACCES)
    public ResponseEntity<AbsenceReponse> annuler(@RequestParam(required = false) UUID centerId, @PathVariable UUID id,
                                                  @Valid @RequestBody AnnulationDemande demande) {
        CentreId centre = session.centre(centerId);
        var ligne = annuler.executer(centre, new AbsenceId(id), demande.commentaire(), session.intervenant());
        return ResponseEntity.ok(AbsenceReponse.depuis(ligne));
    }
}
