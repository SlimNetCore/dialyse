package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Present;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.StatutCase;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de domaine pur : mesure la consommation de ressources d'un planning (générateurs, salles ouvertes, vacations
 * d'infirmiers, équité). Les mêmes mesures servent à comparer l'état actuel et la proposition du solveur, sans dépendre
 * du solveur : elles sont recalculées à partir des placements et des vacations eux-mêmes.
 * <p>
 * Hors couverture, le planning est évalué sur la semaine type (sans fermeture datée, absence ni remplacement) : on
 * compare des roulements, pas des aléas.
 */
public final class IndicateursOptimisationService {

    private IndicateursOptimisationService() {
    }

    /**
     * Données de référence de l'évaluation : réelles pour la couverture, semaine type sinon.
     */
    public static DonneesOptimisation reference(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        return parametres.perimetre().datee() ? donnees : donnees.sansAleas();
    }

    /**
     * Indicateurs de l'état actuel du centre.
     */
    public static Indicateurs avant(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        DonneesOptimisation ref = reference(donnees, parametres);
        return calculer(ref, donnees.placementsActuels(), parametres, null, null);
    }

    /**
     * Indicateurs après application de la proposition.
     *
     * @param placements places proposées ({@code donnees.placementsActuels()} si les patients ne bougent pas)
     * @param vacations  vacations proposées, ou nul pour évaluer le roulement en place face aux nouvelles places
     * @param manques    vacations non pourvues de la proposition (ignorées si {@code vacations} est nul)
     */
    public static Indicateurs apres(DonneesOptimisation donnees, Map<UUID, Poste> placements,
                                    ParametresOptimisation parametres, List<VacationPlanifiee> vacations,
                                    List<VacationNonPourvue> manques) {
        return apres(donnees, placements, Map.of(), parametres, vacations, manques);
    }

    /**
     * Comme {@link #apres(DonneesOptimisation, Map, ParametresOptimisation, List, List)}, avec les jours de dialyse
     * choisis par l'optimisation pour certains patients.
     */
    public static Indicateurs apres(DonneesOptimisation donnees, Map<UUID, Poste> placements,
                                    Map<UUID, Set<JourSemaine>> joursChoisis, ParametresOptimisation parametres,
                                    List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques) {
        DonneesOptimisation ref = reference(donnees, parametres).avecPlacements(placements, joursChoisis);
        return calculer(ref, placements, parametres, vacations, manques);
    }

    private static Indicateurs calculer(DonneesOptimisation donnees, Map<UUID, Poste> placements,
                                        ParametresOptimisation parametres, List<VacationPlanifiee> vacations,
                                        List<VacationNonPourvue> manques) {
        Set<UUID> generateurs = new HashSet<>();
        Map<String, Integer> parCase = new HashMap<>();
        Set<UUID> operationnels = new HashSet<>();
        for (GenerateurRef g : donnees.planning().generateurs()) operationnels.add(g.id());
        Set<JourSemaine> ouverts = donnees.planning().joursOuverts() == null || donnees.planning().joursOuverts().isEmpty()
                ? Set.of(JourSemaine.values()) : donnees.planning().joursOuverts();
        for (Occupation o : donnees.planning().occupations()) {
            if (o.generateurId() != null && operationnels.contains(o.generateurId())) generateurs.add(o.generateurId());
            for (JourSemaine jour : o.jours()) {
                if (ouverts.contains(jour)) parCase.merge(o.salleId() + "|" + o.creneauId() + "|" + jour, 1, Integer::sum);
            }
        }
        int ratio = donnees.presence().patientsParInfirmier();
        int requises = 0;
        int inutilisees = 0;
        for (int patients : parCase.values()) {
            int requis = PresenceInfirmierService.requis(patients, ratio);
            requises += requis;
            inutilisees += requis * ratio - patients;
        }
        int nonPlaces = 0;
        for (PatientAPlacer p : donnees.patients()) {
            if (!complet(placements.get(p.patientId()))) nonPlaces++;
        }

        Personnel personnel = vacations != null
                ? depuisVacations(vacations, manques, parametres)
                : depuisPresence(donnees, parametres);
        return new Indicateurs(generateurs.size(), parCase.size(), requises, inutilisees, nonPlaces,
                personnel.manques(), personnel.mobilises(), personnel.ecart(), personnel.depassements());
    }

    private static boolean complet(Poste poste) {
        return poste != null && poste.salleId() != null && poste.creneauId() != null && poste.generateurId() != null;
    }

    private record Personnel(int manques, int mobilises, int ecart, int depassements) {
    }

    private static Personnel depuisPresence(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        int manques = 0;
        Map<UUID, Integer> parInfirmier = new HashMap<>();
        Map<String, Integer> parInfirmierEtSemaine = new HashMap<>();
        for (int semaine = 0; semaine < parametres.semainesCalcul(); semaine++) {
            SemainePresence presence = PresenceInfirmierService.construire(donnees.presence(),
                    parametres.debutSemaine().plusWeeks(semaine));
            for (CasePresence c : presence.cases()) {
                if (c.statut() == StatutCase.FERME) continue;
                manques += c.manque();
                for (Present p : c.presents()) {
                    parInfirmier.merge(p.infirmierId(), 1, Integer::sum);
                    parInfirmierEtSemaine.merge(p.infirmierId() + "|" + semaine, 1, Integer::sum);
                }
            }
        }
        return personnel(manques, parInfirmier, parInfirmierEtSemaine, parametres);
    }

    private static Personnel depuisVacations(List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques,
                                             ParametresOptimisation parametres) {
        Map<UUID, Integer> parInfirmier = new HashMap<>();
        Map<String, Integer> parInfirmierEtSemaine = new HashMap<>();
        for (VacationPlanifiee v : vacations) {
            long semaine = ChronoUnit.DAYS.between(parametres.debutSemaine(), v.date()) / 7;
            parInfirmier.merge(v.infirmierId(), 1, Integer::sum);
            parInfirmierEtSemaine.merge(v.infirmierId() + "|" + semaine, 1, Integer::sum);
        }
        int totalManques = manques == null ? 0 : manques.stream().mapToInt(VacationNonPourvue::manque).sum();
        return personnel(totalManques, parInfirmier, parInfirmierEtSemaine, parametres);
    }

    private static Personnel personnel(int manques, Map<UUID, Integer> parInfirmier,
                                       Map<String, Integer> parInfirmierEtSemaine, ParametresOptimisation parametres) {
        int max = parInfirmier.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = parInfirmier.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int depassements = parInfirmierEtSemaine.values().stream()
                .mapToInt(n -> Math.max(0, n - parametres.maxVacationsParSemaine())).sum();
        return new Personnel(manques, parInfirmier.size(), max - min, depassements);
    }
}
