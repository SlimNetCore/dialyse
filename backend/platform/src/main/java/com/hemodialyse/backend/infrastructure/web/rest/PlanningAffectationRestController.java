package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.planning.PlanningAffectationQueryService;
import com.hemodialyse.backend.application.planning.PlanningAffectationQueryService.Resultat;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Aide au placement d'un nouveau patient : places libres (salle, créneau, générateur, jours) et grille de
 * disponibilité du centre. Lecture seule, bornée au centre ({@link CenterAccessGuard}).
 */
@RestController
@RequestMapping("/api/v1/planning/affectations")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class PlanningAffectationRestController {

    private static final int LIMITE_PAR_DEFAUT = 12;

    private final PlanningAffectationQueryService service;
    private final CenterAccessGuard centerAccessGuard;

    public PlanningAffectationRestController(PlanningAffectationQueryService service, CenterAccessGuard centerAccessGuard) {
        this.service = service;
        this.centerAccessGuard = centerAccessGuard;
    }

    /**
     * @param seances   nombre de séances par semaine (défaut 3) ; ignoré si des jours sont imposés
     * @param jours     jours imposés (ex. {@code LUNDI,MERCREDI,VENDREDI}), facultatif
     * @param creneauId créneau souhaité, facultatif
     * @param salleId   salle souhaitée, facultative
     * @param patientId patient dont on modifie le placement (libéré dans le calcul), facultatif
     * @param isolement patient à risque infectieux : absent = déduit des sérologies du patient
     */
    @GetMapping
    public ResponseEntity<Resultat> proposer(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "3") int seances,
            @RequestParam(required = false) List<JourSemaine> jours,
            @RequestParam(required = false) UUID creneauId,
            @RequestParam(required = false) UUID salleId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Boolean isolement,
            @RequestParam(defaultValue = "" + LIMITE_PAR_DEFAUT) int limite) {
        UUID centre = centerAccessGuard.requireCenter(centerId).value();
        Set<JourSemaine> joursImposes = jours == null || jours.isEmpty() ? Set.of() : EnumSet.copyOf(jours);
        Resultat resultat = service.proposer(centre, seances, joursImposes, creneauId, salleId, limite, patientId, isolement);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resultat);
    }
}
