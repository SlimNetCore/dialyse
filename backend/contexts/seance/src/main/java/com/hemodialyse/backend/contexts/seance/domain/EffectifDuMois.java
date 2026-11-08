package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Effectif du mois et ses répartitions par sexe et par tranche d'âge (calculée à la fin du mois). L'effectif réunit les patients pris en
 * charge sur le mois (hors sommeil, présents au moins un jour avant la libération de leur place), qu'ils aient déjà dialysé ou non, et
 * ceux qui ont une séance réalisée dans le mois. Chaque patient est compté une fois.
 * <p>
 * KB-SEA-65 (reproduit, décision métier requise avant tout usage de pilotage) : les « présents » viennent d'une jointure des séances du
 * centre avec TOUS les patients, sans filtre de centre sur le patient ; un patient d'un autre centre ayant une séance dans ce centre est
 * compté dans l'effectif et les répartitions.
 *
 * @param patients  nombre de patients distincts
 * @param parSexe   {@code M}, {@code F}, {@code AUTRE}, toujours présents, dans cet ordre
 * @param parTranche {@code 0-17}, {@code 18-39}, {@code 40-59}, {@code 60+}, {@code INCONNU}, toujours présents, dans cet ordre
 */
public record EffectifDuMois(int patients, Map<String, Long> parSexe, Map<String, Long> parTranche) {

    public static EffectifDuMois composer(List<PatientDuMois> patientsDuCentre, List<Demographie> presents, LocalDate debut, LocalDate fin) {
        Map<PatientId, Demographie> effectif = new LinkedHashMap<>();
        for (PatientDuMois patient : patientsDuCentre) {
            if (patient.enSommeil()) continue;
            if (!patient.premierJourAttendu(debut).isAfter(patient.dernierJourAttendu(fin))) {
                effectif.put(patient.id(), patient.demographie());
            }
        }
        for (Demographie present : presents) effectif.putIfAbsent(present.patient(), present);

        Map<String, Long> parSexe = new LinkedHashMap<>();
        for (Sexe sexe : Sexe.values()) parSexe.put(sexe.name(), 0L);
        Map<String, Long> parTranche = new LinkedHashMap<>();
        for (TrancheDAge tranche : TrancheDAge.values()) parTranche.put(tranche.libelle(), 0L);
        for (Demographie d : effectif.values()) {
            parSexe.merge(Sexe.depuis(d.sexe()).name(), 1L, Long::sum);
            parTranche.merge(TrancheDAge.pour(d.naissance(), fin).libelle(), 1L, Long::sum);
        }
        return new EffectifDuMois(effectif.size(), parSexe, parTranche);
    }
}
