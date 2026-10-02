package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.DemandePlacement;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Proposition;
import com.hemodialyse.backend.domain.planning.port.PlacementPatientPort;
import com.hemodialyse.backend.domain.planning.port.PlacementPatientPort.Placement;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService.Violation;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cohérence du placement des patients : tout placement enregistré sur une fiche respecte les règles de la
 * planification (jours ouverts, générateur en service et libre, isolement), et un patient devenu à risque infectieux
 * est replacé automatiquement en salle d'isolement — ou signalé s'il n'y a plus de place.
 */
@Service
public class PlacementPatientService {

    private static final int NB_PROPOSITIONS = 5;

    private final PlanningDonneesPort donnees;
    private final PlacementPatientPort placements;
    private final NotificationService notifications;

    public PlacementPatientService(PlanningDonneesPort donnees, PlacementPatientPort placements,
                                   NotificationService notifications) {
        this.donnees = donnees;
        this.placements = placements;
        this.notifications = notifications;
    }

    /**
     * Refuse un placement manuel qui enfreint les règles de la planification. Une fiche sans aucun placement est
     * acceptée, de même qu'une fiche dont le placement n'a pas changé (une anomalie héritée ne bloque pas la
     * modification d'une autre donnée).
     *
     * @param patientId patient modifié, {@code null} à la création
     * @throws BusinessException code {@code PLACEMENT_<règle>} pour la première règle enfreinte
     */
    public void verifier(UUID centerId, UUID patientId, UUID salleId, UUID creneauId, UUID generateurId,
                         Set<JourSemaine> jours) {
        Set<JourSemaine> joursDemandes = jours == null ? Set.of() : jours;
        if (salleId == null && creneauId == null && generateurId == null && joursDemandes.isEmpty()) return;
        if (patientId != null && placements.placementActuel(centerId, patientId)
                .equals(new Placement(salleId, creneauId, generateurId, joursDemandes))) {
            return;
        }
        boolean aRisque = patientId != null && donnees.patientARisque(centerId, patientId);
        List<Violation> violations = PlanificationAffectationService.verifier(
                donnees.charger(centerId, patientId), aRisque, salleId, creneauId, generateurId, joursDemandes);
        if (!violations.isEmpty()) {
            throw new BusinessException("PLACEMENT_" + violations.getFirst().name(),
                    "Placement refusé par la planification : " + violations);
        }
    }

    /**
     * Replace en salle d'isolement un patient à risque infectieux dont le placement ne convient plus (sérologie
     * devenue positive après son placement) : mêmes jours, créneau conservé si possible. Sans place d'isolement, le
     * patient reste en place et l'administration est alertée.
     */
    @CacheEvict(cacheNames = {"patient.byId", "patient.byCenter", "patient.list.summary",
            "patient.list.summary.details"}, allEntries = true)
    public Issue reaffecterSiRisque(UUID centerId, UUID patientId) {
        if (!donnees.patientARisque(centerId, patientId)) return Issue.AUCUN_RISQUE;
        Placement actuel = placements.placementActuel(centerId, patientId);
        if (actuel.estVide() || actuel.salleId() == null || actuel.jours().isEmpty()) return Issue.NON_PLACE;

        DonneesPlanning autres = donnees.charger(centerId, patientId);
        if (PlanificationAffectationService.verifier(autres, true, actuel.salleId(), actuel.creneauId(),
                actuel.generateurId(), actuel.jours()).isEmpty()) {
            return Issue.DEJA_CONFORME;
        }
        List<Proposition> propositions = PlanificationAffectationService.proposer(autres,
                new DemandePlacement(actuel.jours().size(), actuel.jours(), actuel.creneauId(), null, true),
                NB_PROPOSITIONS);
        String nom = placements.nomPatient(centerId, patientId);
        if (propositions.isEmpty()) {
            notifications.notifyIsolementImpossible(centerId, patientId, nom);
            return Issue.A_REPLANIFIER;
        }
        Proposition p = propositions.getFirst();
        placements.deplacer(centerId, patientId, p.salle().id(), p.creneau().id(), p.generateur().id());
        notifications.notifyPatientReplaceIsolement(centerId, patientId, nom, p.salle().nom());
        return Issue.DEPLACE;
    }

    public enum Issue {AUCUN_RISQUE, NON_PLACE, DEJA_CONFORME, DEPLACE, A_REPLANIFIER}
}
