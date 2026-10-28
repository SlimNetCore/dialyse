package com.hemodialyse.backend.contexts.planification.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLaSemaine;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Semaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.TypeDeConflit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP du planning réel d'une semaine (slice P6) : qui dialyse où, jours fermés et conflits. Lecture seule, bornée au
 * centre, jamais mise en cache. Mêmes URL, droits et JSON que le legacy ; une date ou un centre illisible répond 400 (KB-PLN-67) et la
 * date par défaut est celle du centre (KB-PLN-69). Les méthodes et chemins inconnus restent du ressort du traitement global.
 */
@RestController
@RequestMapping("/api/v1/planning/semaine")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class SemaineControleur {

    private final SessionCourante session;
    private final ConsulterLaSemaine consulter;

    public SemaineControleur(SessionCourante session, ConsulterLaSemaine consulter) {
        this.session = session;
        this.consulter = consulter;
    }

    /** @param date un jour de la semaine voulue (défaut : aujourd'hui au fuseau du centre) */
    @GetMapping
    public ResponseEntity<SemaineReponse> semaine(@RequestParam(required = false) UUID centerId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SemaineReponse.depuis(consulter.executer(centre, date)));
    }

    public record JourReponse(JourDeLaSemaine jour, LocalDate date, boolean ouvertHebdomadaire, String fermetureMotif) {
    }

    public record SalleReponse(UUID id, String nom) {
    }

    public record CreneauReponse(UUID id, String libelle, int ordre) {
    }

    public record OccupantReponse(UUID patientId, String nom, String generateurCode, boolean aRisque, LocalDate libereLe,
                                  boolean temporaire, boolean realiseeHorsPlanning, LocalDate dejaRealiseeLe) {
    }

    public record CelluleReponse(UUID salleId, UUID creneauId, JourDeLaSemaine jour, int capacite, List<OccupantReponse> occupants) {
    }

    public record ConflitReponse(TypeDeConflit type, UUID salleId, UUID creneauId, JourDeLaSemaine jour, List<String> patients) {
    }

    public record SeanceSansCaseReponse(UUID patientId, String nom, LocalDate date, JourDeLaSemaine jour) {
    }

    public record SemaineReponse(LocalDate debut, LocalDate fin, List<JourReponse> jours, List<SalleReponse> salles,
                                 List<CreneauReponse> creneaux, List<CelluleReponse> cellules, List<ConflitReponse> conflits,
                                 int patientsAReplanifier, List<SeanceSansCaseReponse> seancesSansCase) {
        static SemaineReponse depuis(Semaine s) {
            return new SemaineReponse(s.debut(), s.fin(),
                    s.jours().stream().map(j -> new JourReponse(j.jour(), j.date(), j.ouvertHebdomadaire(), j.fermeture())).toList(),
                    s.salles().stream().map(x -> new SalleReponse(x.id().valeur(), x.nom())).toList(),
                    s.creneaux().stream().map(x -> new CreneauReponse(x.id().valeur(), x.libelle(), x.ordre())).toList(),
                    s.cases().stream().map(c -> new CelluleReponse(c.salle().valeur(), c.creneau().valeur(), c.jour(), c.capacite(),
                            c.occupants().stream().map(o -> new OccupantReponse(o.patient().valeur(), o.nom(), o.generateur(), o.aRisque(),
                                    o.libereLe(), o.temporaire(), o.realiseeHorsPlanning(), o.dejaRealiseeLe())).toList())).toList(),
                    s.conflits().stream().map(c -> new ConflitReponse(c.type(), c.salle() == null ? null : c.salle().valeur(),
                            c.creneau() == null ? null : c.creneau().valeur(), c.jour(), c.patients())).toList(),
                    s.patientsAReplanifier(),
                    s.seancesSansCase().stream().map(x -> new SeanceSansCaseReponse(x.patient().valeur(), x.nom(), x.date(), x.jour())).toList());
        }
    }
}
