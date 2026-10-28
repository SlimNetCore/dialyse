package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de domaine pur (P24, RG-PLN-089) : les indicateurs avant / après d'une proposition, recalculés INDÉPENDAMMENT du solveur
 * à partir des données et des placements. Les cases et l'effectif viennent de la règle de présence de la lecture (P4), la même.
 */
public final class IndicateursDOptimisation {

    private IndicateursDOptimisation() {
    }

    /** Les données de référence : datées pour les périmètres datés, sans aléas (semaine type) pour les autres. */
    public static DonneesDOptimisation reference(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        return parametres.perimetre().datee() ? donnees : donnees.sansAleas();
    }

    /** Indicateurs de la situation actuelle. */
    public static Indicateurs avant(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        return calculer(reference(donnees, parametres), donnees.placementsActuels(), parametres, null, null);
    }

    public static Indicateurs apres(DonneesDOptimisation donnees, Map<PatientId, Poste> placements, ParametresDOptimisation parametres,
                                    List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques) {
        return apres(donnees, placements, Map.of(), parametres, vacations, manques);
    }

    /** Indicateurs de la situation proposée ; {@code vacations} nul : le personnel reste celui des données. */
    public static Indicateurs apres(DonneesDOptimisation donnees, Map<PatientId, Poste> placements,
                                    Map<PatientId, Set<JourDeLaSemaine>> joursChoisis, ParametresDOptimisation parametres,
                                    List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques) {
        DonneesDOptimisation ref = reference(donnees, parametres).avecPlacements(placements, joursChoisis);
        return calculer(ref, placements, parametres, vacations, manques);
    }

    private static Indicateurs calculer(DonneesDOptimisation donnees, Map<PatientId, Poste> placements,
                                        ParametresDOptimisation parametres, List<VacationPlanifiee> vacations,
                                        List<VacationNonPourvue> manques) {
        Set<GenerateurId> operationnels = new HashSet<>();
        donnees.ressources().generateurs().forEach(g -> operationnels.add(g.id()));
        Set<GenerateurId> generateurs = new HashSet<>();
        Map<String, Integer> parCase = new HashMap<>();
        Set<JourDeLaSemaine> ouverts = donnees.joursOuverts();
        for (PatientAPlacer p : donnees.patients()) {
            Poste poste = p.actuelle();
            if (poste == null || poste.salle() == null || poste.creneau() == null || p.jours().isEmpty()) continue;
            if (poste.generateur() != null && operationnels.contains(poste.generateur())) generateurs.add(poste.generateur());
            for (JourDeLaSemaine jour : p.jours()) {
                if (ouverts.contains(jour)) parCase.merge(poste.salle().valeur() + "|" + poste.creneau().valeur() + "|" + jour, 1, Integer::sum);
            }
        }
        int ratio = donnees.personnel().ratio().patientsParInfirmier();
        int requises = 0;
        int inutilisees = 0;
        for (int patients : parCase.values()) {
            int requis = PresenceDeLaSemaine.requis(patients, ratio);
            requises += requis;
            inutilisees += requis * ratio - patients;
        }
        int nonPlaces = 0;
        for (PatientAPlacer p : donnees.patients()) {
            Poste poste = placements.get(p.patient());
            if (poste == null || !poste.estComplet()) nonPlaces++;
        }
        Personnel personnel = vacations != null ? depuisVacations(vacations, manques, parametres) : depuisPresence(donnees, parametres);
        return new Indicateurs(generateurs.size(), parCase.size(), requises, inutilisees, nonPlaces, personnel.manques(),
                personnel.mobilises(), personnel.ecart(), personnel.depassements());
    }

    private record Personnel(int manques, int mobilises, int ecart, int depassements) {
    }

    private static Personnel depuisPresence(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        int manques = 0;
        Map<UUID, Integer> parInfirmier = new HashMap<>();
        Map<String, Integer> parInfirmierEtSemaine = new HashMap<>();
        for (int semaine = 0; semaine < parametres.semainesCalcul(); semaine++) {
            SemainePresence presence = PresenceDeLaSemaine.construire(donnees.presence(), parametres.debutSemaine().plusWeeks(semaine));
            for (CasePresence c : presence.cases()) {
                if (c.statut() == Presence.StatutDeCase.FERME) continue;
                manques += c.manque();
                for (Presence.Present p : c.presents()) {
                    parInfirmier.merge(p.infirmierId(), 1, Integer::sum);
                    parInfirmierEtSemaine.merge(p.infirmierId() + "|" + semaine, 1, Integer::sum);
                }
            }
        }
        return personnel(manques, parInfirmier, parInfirmierEtSemaine, parametres);
    }

    private static Personnel depuisVacations(List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques,
                                             ParametresDOptimisation parametres) {
        Map<UUID, Integer> parInfirmier = new HashMap<>();
        Map<String, Integer> parInfirmierEtSemaine = new HashMap<>();
        for (VacationPlanifiee v : vacations) {
            long semaine = ChronoUnit.DAYS.between(parametres.debutSemaine(), v.date()) / 7;
            parInfirmier.merge(v.infirmier().valeur(), 1, Integer::sum);
            parInfirmierEtSemaine.merge(v.infirmier().valeur() + "|" + semaine, 1, Integer::sum);
        }
        int totalManques = manques == null ? 0 : manques.stream().mapToInt(VacationNonPourvue::manque).sum();
        return personnel(totalManques, parInfirmier, parInfirmierEtSemaine, parametres);
    }

    private static Personnel personnel(int manques, Map<UUID, Integer> parInfirmier, Map<String, Integer> parInfirmierEtSemaine,
                                       ParametresDOptimisation parametres) {
        int max = parInfirmier.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = parInfirmier.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int depassements = parInfirmierEtSemaine.values().stream()
                .mapToInt(n -> Math.max(0, n - parametres.maxVacationsParSemaine())).sum();
        return new Personnel(manques, parInfirmier.size(), max - min, depassements);
    }
}
