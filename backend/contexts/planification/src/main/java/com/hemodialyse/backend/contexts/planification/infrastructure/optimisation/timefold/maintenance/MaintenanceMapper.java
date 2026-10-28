package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.maintenance;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.MaintenanceDesGenerateurs;
import com.hemodialyse.backend.contexts.planification.domain.MaintenanceDesGenerateurs.SeanceImpactee;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.SeanceSansSolution;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.PoidsOptimisation;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.patients.PosteSerie;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduit les séances touchées par une maintenance en problème de déplacements temporaires, et la solution en
 * déplacements proposés ou séances sans solution. Places possibles d'une séance : générateurs disponibles ce jour-là,
 * de la bonne catégorie (isolement ou non), qu'aucun autre patient n'occupe à ce créneau.
 */
public final class MaintenanceMapper {

    private MaintenanceMapper() {
    }

    public static PlanMaintenance versProbleme(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        List<SeanceImpactee> impactees = MaintenanceDesGenerateurs.seancesImpactees(donnees,
                parametres.debutSemaine(), parametres.finHorizon());
        Map<LocalDate, List<SeanceImpactee>> parDate = new LinkedHashMap<>();
        impactees.forEach(s -> parDate.computeIfAbsent(s.date(), k -> new ArrayList<>()).add(s));
        RessourcesDuCentre ressources = donnees.ressources();

        List<SeanceTemporaire> seances = new ArrayList<>();
        parDate.forEach((date, duJour) -> {
            Set<PatientId> deplaces = new HashSet<>();
            duJour.forEach(s -> deplaces.add(s.patient()));
            Set<String> occupes = MaintenanceDesGenerateurs.postesOccupes(donnees, date, deplaces);
            List<PosteSerie> normaux = new ArrayList<>();
            List<PosteSerie> postesIsolement = new ArrayList<>();
            for (RessourcesDuCentre.Generateur g : ressources.generateurs()) {
                if (MaintenanceDesGenerateurs.indisponibilite(donnees, g.id(), date).isPresent()) continue;
                boolean iso = ressources.estIsolement(g.salle());
                for (RessourcesDuCentre.Creneau creneau : ressources.creneaux()) {
                    if (occupes.contains(MaintenanceDesGenerateurs.cle(g.id(), creneau.id()))) continue;
                    (iso ? postesIsolement : normaux).add(new PosteSerie(g.id().valeur(), g.code(), g.salle().valeur(),
                            creneau.id().valeur(), iso));
                }
            }
            for (SeanceImpactee s : duJour) {
                seances.add(new SeanceTemporaire(s.patient().valeur(), date, s.jour(), s.poste(), s.motif(),
                        s.aRisque() ? postesIsolement : normaux));
            }
        });
        return new PlanMaintenance(seances, PoidsOptimisation.maintenance());
    }

    public static Resultat versResultat(PlanMaintenance solution, DonneesDOptimisation donnees) {
        Map<PatientId, String> noms = new HashMap<>();
        for (PatientAPlacer p : donnees.patients()) noms.put(p.patient(), p.nom());
        List<DeplacementTemporairePropose> deplacements = new ArrayList<>();
        List<SeanceSansSolution> sansSolution = new ArrayList<>();
        for (SeanceTemporaire s : solution.getSeances()) {
            PatientId patient = new PatientId(s.getPatientId());
            String nom = noms.getOrDefault(patient, "");
            if (s.getPoste() != null) {
                deplacements.add(new DeplacementTemporairePropose(patient, nom, s.getDate(), s.getJour(),
                        s.getHabituel(), s.getPoste().versDomaine(), s.getMotif()));
            } else {
                sansSolution.add(new SeanceSansSolution(patient, nom, s.getDate(), s.getJour(),
                        s.getHabituel(), s.getMotif()));
            }
        }
        deplacements.sort(Comparator.comparing(DeplacementTemporairePropose::date)
                .thenComparing(DeplacementTemporairePropose::nom));
        sansSolution.sort(Comparator.comparing(SeanceSansSolution::date).thenComparing(SeanceSansSolution::nom));
        return new Resultat(deplacements, sansSolution);
    }

    public record Resultat(List<DeplacementTemporairePropose> deplacements, List<SeanceSansSolution> sansSolution) {
    }
}
