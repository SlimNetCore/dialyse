package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.AbsenceLue;
import com.hemodialyse.backend.contexts.planification.application.DeclarerUneAbsenceDInfirmier;
import com.hemodialyse.backend.contexts.planification.application.ListerLesAbsences;
import com.hemodialyse.backend.contexts.planification.application.RetirerUneAbsence;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import com.hemodialyse.backend.contexts.planification.interfaces.http.InfirmiersControleur.PageReponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP des absences des infirmiers saisies par l'administration (slice P3) : mêmes URLs et mêmes droits que le legacy
 * (lecture : administrateur, secrétariat, médecin + infirmier ; écriture : administrateur et secrétariat), bornées au centre de la
 * session. Écarts décidés (26/10/2026) : entrées illisibles et motif de plus de 255 caractères 400, 415 hors JSON, droits avant le corps,
 * pagination négative 400 et plafond 100, chevauchement 422 {@code ABSENCE_CHEVAUCHEMENT}, infirmier désactivé 422, audit métier avec
 * l'identifiant créé, retrait notifié (KB-PLN-26, 30, 33, 34, 35, 36, 37).
 */
@RestController
@RequestMapping("/api/v1/infirmiers/absences")
public class AbsencesDesInfirmiersControleur {

    private static final String LECTURE = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";
    private static final String ECRITURE = "hasAnyRole('ADMIN','SECRETAIRE')";

    private final SessionCourante session;
    private final ObjectMapper lecteur;
    private final ListerLesAbsences lister;
    private final DeclarerUneAbsenceDInfirmier declarer;
    private final RetirerUneAbsence retirer;

    public AbsencesDesInfirmiersControleur(SessionCourante session, ObjectMapper lecteur, ListerLesAbsences lister,
                                           DeclarerUneAbsenceDInfirmier declarer, RetirerUneAbsence retirer) {
        this.session = session;
        this.lecteur = lecteur;
        this.lister = lister;
        this.declarer = declarer;
        this.retirer = retirer;
    }

    @GetMapping
    @PreAuthorize(LECTURE)
    public ResponseEntity<PageReponse<AbsenceReponse>> absences(@RequestParam(required = false) UUID centerId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<AbsenceLue> lues = lister.executer(centre, new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageReponse.depuis(lues, AbsenceReponse::depuis));
    }

    @PostMapping
    @PreAuthorize(ECRITURE)
    public ResponseEntity<AbsenceReponse> declarer(@RequestParam(required = false) UUID centerId,
                                                   @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        AbsenceDemande demande = CorpsJson.lire(lecteur, requete, corps, AbsenceDemande.class);
        AbsenceLue creee = declarer.executer(centre, new InfirmierId(demande.infirmierId()), demande.periode(),
                TypeAbsence.depuisLaSaisie(demande.type()), demande.motif(), session.utilisateur(), session.administrateur());
        requete.setAttribute(InfirmiersControleur.ATTRIBUT_AUDIT_IDENTIFIANT, creee.id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceReponse.depuis(creee));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ECRITURE)
    public ResponseEntity<Void> retirer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        retirer.executer(centre, new AbsenceId(id), session.administrateur());
        return ResponseEntity.noContent().build();
    }

    /**
     * Absence saisie : identifiants, dates et type lus tels quels, jugés par le domaine (400 si inutilisables).
     */
    public record AbsenceDemande(UUID infirmierId, LocalDate debut, LocalDate fin, String type, String motif) {
        PeriodeDAbsence periode() {
            return new PeriodeDAbsence(debut, fin);
        }
    }

    public record AbsenceReponse(UUID id, UUID infirmierId, LocalDate debut, LocalDate fin, TypeAbsence type,
                                 String motif) {
        static AbsenceReponse depuis(AbsenceLue a) {
            return new AbsenceReponse(a.id(), a.infirmier(), a.debut(), a.fin(), a.type(), a.motif());
        }
    }
}
