package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.AbsenceLue;
import com.hemodialyse.backend.contexts.planification.application.ConsulterMonPlanning;
import com.hemodialyse.backend.contexts.planification.application.ConsulterMonPlanning.MonPlanning;
import com.hemodialyse.backend.contexts.planification.application.DeclarerMonAbsence;
import com.hemodialyse.backend.contexts.planification.application.ListerMesAbsences;
import com.hemodialyse.backend.contexts.planification.application.RetirerMonAbsence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.CaseDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.Creneau;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.CreneauPersonnel;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.JourDuPlanning;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier.Salle;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;
import com.hemodialyse.backend.contexts.planification.interfaces.http.AbsencesDesInfirmiersControleur.AbsenceReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.InfirmiersControleur.InfirmierReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.InfirmiersControleur.PageReponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP de « Mon planning » (slice P3) : planning personnel et absences de l'infirmier dont la fiche est liée au compte
 * connecté. Le compte vient toujours de l'authentification (jamais d'un paramètre). Mêmes URLs, mêmes droits et même JSON que le
 * legacy ; le médecin seul reste refusé par le filtre de rôles (RG-INF-031/032 corrigées). Écarts décidés (26/10/2026) : entrées
 * illisibles 400, « aujourd'hui » au fuseau du centre, chevauchement 422, motif borné, retrait des seules absences déclarées par
 * l'infirmier, retrait notifié (KB-PLN-26, 30, 32, 34, 36, 37, 39).
 */
@RestController
@RequestMapping("/api/v1/infirmiers/moi")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class MonPlanningControleur {

    private final SessionCourante session;
    private final ObjectMapper lecteur;
    private final ConsulterMonPlanning consulter;
    private final ListerMesAbsences lister;
    private final DeclarerMonAbsence declarer;
    private final RetirerMonAbsence retirer;

    public MonPlanningControleur(SessionCourante session, ObjectMapper lecteur, ConsulterMonPlanning consulter,
                                 ListerMesAbsences lister, DeclarerMonAbsence declarer, RetirerMonAbsence retirer) {
        this.session = session;
        this.lecteur = lecteur;
        this.consulter = consulter;
        this.lister = lister;
        this.declarer = declarer;
        this.retirer = retirer;
    }

    @GetMapping("/planning")
    public ResponseEntity<MonPlanningReponse> planning(@RequestParam(required = false) UUID centerId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(MonPlanningReponse.depuis(consulter.executer(centre, session.utilisateur(), date)));
    }

    @GetMapping("/absences")
    public ResponseEntity<PageReponse<AbsenceReponse>> absences(@RequestParam(required = false) UUID centerId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        PageDeResultats<AbsenceLue> lues = lister.executer(centre, session.utilisateur(), new Pagination(page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageReponse.depuis(lues, AbsenceReponse::depuis));
    }

    @PostMapping("/absences")
    public ResponseEntity<AbsenceReponse> declarer(@RequestParam(required = false) UUID centerId,
                                                   @RequestBody(required = false) byte[] corps, HttpServletRequest requete)
            throws HttpMediaTypeNotSupportedException {
        CentreId centre = session.centre(centerId);
        MonAbsenceDemande demande = CorpsJson.lire(lecteur, requete, corps, MonAbsenceDemande.class);
        AbsenceLue creee = declarer.executer(centre, session.utilisateur(), demande.periode(),
                TypeAbsence.depuisLaSaisie(demande.type()), demande.motif());
        requete.setAttribute(InfirmiersControleur.ATTRIBUT_AUDIT_IDENTIFIANT, creee.id().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(AbsenceReponse.depuis(creee));
    }

    @DeleteMapping("/absences/{id}")
    public ResponseEntity<Void> retirer(@RequestParam(required = false) UUID centerId, @PathVariable UUID id) {
        CentreId centre = session.centre(centerId);
        retirer.executer(centre, session.utilisateur(), new AbsenceId(id));
        return ResponseEntity.noContent().build();
    }

    public record MonAbsenceDemande(LocalDate debut, LocalDate fin, String type, String motif) {
        PeriodeDAbsence periode() {
            return new PeriodeDAbsence(debut, fin);
        }
    }

    public record MonPlanningReponse(InfirmierReponse infirmier, LocalDate debut, LocalDate fin, List<Salle> salles,
                                     List<Creneau> creneaux, List<CreneauPersonnel> mesCreneaux,
                                     List<JourDuPlanning> jours,
                                     List<CaseDeLInfirmier> mesCases) {
        static MonPlanningReponse depuis(MonPlanning p) {
            var s = p.semaine();
            return new MonPlanningReponse(InfirmierReponse.depuis(p.infirmier()), s.debut(), s.fin(), s.salles(), s.creneaux(),
                    s.mesCreneaux(), s.jours(), s.mesCases());
        }
    }
}
