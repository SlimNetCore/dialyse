package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service de domaine pur (RG-PLN-094) : face à l'état FINAL de tous les patients (placements actuels, remplacés par les
 * déplacements proposés, jours choisis compris), quelles nouvelles places enfreignent les règles du placement ? Même juge que
 * {@link PlacesDuCentre}. Sert à écarter les déplacements invalides d'une proposition et à reverrouiller l'application.
 */
public final class VerificationDesDeplacements {

    /** Code de refus d'une place incomplète ou d'un patient inconnu des données. */
    public static final String INCOMPLET = "INCOMPLET";

    private VerificationDesDeplacements() {
    }

    /** Pour chaque déplacement refusé (ordre des déplacements), les noms des règles enfreintes, la première en tête. */
    public static Map<PatientId, List<String>> verifier(DonneesDOptimisation donnees, List<DeplacementDePatient> deplacements) {
        Map<PatientId, Poste> finaux = new LinkedHashMap<>(donnees.placementsActuels());
        deplacements.forEach(d -> finaux.put(d.patient(), d.vers()));
        Map<PatientId, PatientAPlacer> parPatient = new LinkedHashMap<>();
        donnees.patients().forEach(p -> parPatient.put(p.patient(), p));
        for (DeplacementDePatient d : deplacements) {
            PatientAPlacer p = parPatient.get(d.patient());
            if (p != null && d.jours() != null) parPatient.put(p.patient(), p.avecJours(joursFinaux(p, d)));
        }

        Map<PatientId, List<String>> refus = new LinkedHashMap<>();
        for (DeplacementDePatient d : deplacements) {
            PatientAPlacer patient = parPatient.get(d.patient());
            if (patient == null || d.vers() == null || !d.vers().estComplet() || patient.jours().isEmpty()) {
                refus.put(d.patient(), List.of(INCOMPLET));
                continue;
            }
            List<Occupation> autres = new ArrayList<>();
            for (PatientAPlacer p : parPatient.values()) {
                Poste poste = finaux.get(p.patient());
                if (!p.patient().equals(d.patient()) && poste != null && poste.salle() != null && poste.creneau() != null
                        && !p.jours().isEmpty()) {
                    autres.add(p.occupationA(poste));
                }
            }
            Place place = new Place(d.vers().salle(), d.vers().creneau(), d.vers().generateur(), patient.jours());
            List<String> violations = PlacesDuCentre.de(donnees.centre(), donnees.ressources(), autres)
                    .violationsDe(place, patient.categorie()).stream().map(Enum::name).toList();
            if (!violations.isEmpty()) refus.put(d.patient(), violations);
        }
        return refus;
    }

    /** Les déplacements qui tiennent, une fois écartés (de proche en proche) ceux qui enfreignent une règle. */
    public static List<DeplacementDePatient> retirerInvalides(DonneesDOptimisation donnees, List<DeplacementDePatient> deplacements) {
        List<DeplacementDePatient> retenus = new ArrayList<>(deplacements);
        while (!retenus.isEmpty()) {
            Map<PatientId, List<String>> refus = verifier(donnees, retenus);
            if (refus.isEmpty()) break;
            retenus.removeIf(d -> refus.containsKey(d.patient()));
        }
        return List.copyOf(retenus);
    }

    /** Jours d'un patient une fois ceux de la proposition appliqués (les siens si la proposition n'en choisit pas). */
    public static Set<JourDeLaSemaine> joursFinaux(PatientAPlacer patient, DeplacementDePatient deplacement) {
        if (deplacement.jours() == null) return patient.jours();
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        jours.addAll(deplacement.jours());
        return jours;
    }
}
