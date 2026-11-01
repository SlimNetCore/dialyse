package com.hemodialyse.backend.contexts.planification.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ProposerDesPlaces;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.CaseReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.CreneauReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.FermetureReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.GenerateurReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.PropositionReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.PropositionsReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.SalleReponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter entrant HTTP de l'aide au placement (slice P5) : places libres (salle, créneau, générateur, jours) et grille de
 * disponibilité du centre. Lecture seule, bornée au centre, jamais mise en cache. Mêmes URL et JSON que le legacy ; paramètres
 * illisibles : 400 au lieu de 500 (KB-PLN-56) ; {@code limite} hors 1-30 ramenée dans les bornes (KB-PLN-57 reproduit).
 */
@RestController
@RequestMapping("/api/v1/planning/affectations")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class PropositionsControleur {

    private static final int LIMITE_PAR_DEFAUT = 12;

    private final SessionCourante session;
    private final ProposerDesPlaces proposer;

    public PropositionsControleur(SessionCourante session, ProposerDesPlaces proposer) {
        this.session = session;
        this.proposer = proposer;
    }

    /**
     * @param seances   nombre de séances par semaine (défaut 3) ; ignoré si des jours sont imposés
     * @param jours     jours imposés (ex. {@code LUNDI,MERCREDI,VENDREDI}), facultatif
     * @param patientId patient dont on modifie la place (libérée dans le calcul), facultatif
     * @param isolement patient à risque infectieux : absent = déduit des sérologies du patient
     */
    @GetMapping
    public ResponseEntity<PropositionsReponse> proposer(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "3") int seances,
            @RequestParam(required = false) List<JourDeLaSemaine> jours,
            @RequestParam(required = false) UUID creneauId,
            @RequestParam(required = false) UUID salleId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Boolean isolement,
            @RequestParam(defaultValue = "" + LIMITE_PAR_DEFAUT) int limite) {
        CentreId centre = session.centre(centerId);
        Set<JourDeLaSemaine> joursImposes = jours == null || jours.isEmpty() ? Set.of() : EnumSet.copyOf(jours);
        ProposerDesPlaces.Reponse reponse = proposer.executer(new ProposerDesPlaces.Commande(centre,
                patientId == null ? null : new PatientId(patientId), seances, joursImposes,
                creneauId == null ? null : new CreneauId(creneauId), salleId == null ? null : new SalleId(salleId), limite,
                isolement));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PropositionsReponse.depuis(reponse));
    }

}

