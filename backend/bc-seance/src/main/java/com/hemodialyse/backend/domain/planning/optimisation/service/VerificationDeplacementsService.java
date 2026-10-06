package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService.Violation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service de domaine pur : filet de sécurité avant d'appliquer des déplacements. Chaque patient déplacé est revérifié
 * avec les règles de la planification ({@link PlanificationAffectationService#verifier}) face à l'état final de tous
 * les autres patients : la planification reste la seule source de cohérence, quel que soit le moteur qui a calculé la
 * proposition.
 */
public final class VerificationDeplacementsService {

    private VerificationDeplacementsService() {
    }

    /**
     * @param donnees      état actuel du centre
     * @param deplacements déplacements proposés
     * @return pour chaque patient déplacé dont la nouvelle place enfreint une règle, ses violations ; vide si valides
     */
    public static Map<UUID, List<Violation>> verifier(DonneesOptimisation donnees, List<DeplacementPatient> deplacements) {
        Map<UUID, Poste> finaux = new HashMap<>(donnees.placementsActuels());
        deplacements.forEach(d -> finaux.put(d.patientId(), d.vers()));
        Map<UUID, PatientAPlacer> parId = new HashMap<>();
        donnees.patients().forEach(p -> parId.put(p.patientId(), p));
        // jours choisis par l'optimisation : ils remplacent les jours prescrits dans l'état final
        for (DeplacementPatient d : deplacements) {
            PatientAPlacer p = parId.get(d.patientId());
            if (p != null && d.jours() != null) parId.put(p.patientId(), p.avecJours(java.util.EnumSet.copyOf(d.jours())));
        }
        DonneesPlanning base = donnees.planning();

        Map<UUID, List<Violation>> refus = new HashMap<>();
        for (DeplacementPatient d : deplacements) {
            PatientAPlacer patient = parId.get(d.patientId());
            if (patient == null) {
                refus.put(d.patientId(), List.of(Violation.INCOMPLET));
                continue;
            }
            List<Occupation> autres = new ArrayList<>();
            for (PatientAPlacer initial : donnees.patients()) {
                PatientAPlacer p = parId.get(initial.patientId());
                Poste poste = finaux.get(p.patientId());
                if (!p.patientId().equals(d.patientId()) && poste != null && poste.salleId() != null
                        && poste.creneauId() != null && !p.jours().isEmpty()) {
                    autres.add(p.versOccupation(poste));
                }
            }
            DonneesPlanning sansLui = new DonneesPlanning(base.salles(), base.creneaux(), base.generateurs(), autres,
                    base.joursOuverts(), base.sallesIsolement(), base.fermetures());
            List<Violation> violations = PlanificationAffectationService.verifier(sansLui, patient.aRisque(),
                    d.vers().salleId(), d.vers().creneauId(), d.vers().generateurId(), patient.jours());
            if (!violations.isEmpty()) refus.put(d.patientId(), violations);
        }
        return refus;
    }

    /**
     * Retire les déplacements qui violent une règle de planification, jusqu'à stabilité : un patient dont le
     * déplacement est retiré reste à sa place actuelle, ce qui peut invalider un autre déplacement (chaîne).
     */
    public static List<DeplacementPatient> retirerInvalides(DonneesOptimisation donnees,
                                                            List<DeplacementPatient> deplacements) {
        List<DeplacementPatient> retenus = new ArrayList<>(deplacements);
        while (!retenus.isEmpty()) {
            Map<UUID, List<Violation>> refus = verifier(donnees, retenus);
            if (refus.isEmpty()) break;
            retenus.removeIf(d -> refus.containsKey(d.patientId()));
        }
        return List.copyOf(retenus);
    }
}
