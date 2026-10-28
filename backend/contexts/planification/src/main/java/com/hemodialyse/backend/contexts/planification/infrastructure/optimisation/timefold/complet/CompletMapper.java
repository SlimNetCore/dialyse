package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.complet;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.PoidsOptimisation;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.infirmiers.InfirmierPlan;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.infirmiers.InfirmiersMapper;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.patients.PatientsMapper;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.patients.PlanPatients;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduit les données du centre en problème conjoint et la solution en proposition. Le calcul porte sur la semaine type
 * (sans aléa) : chaque case ouverte reçoit autant de vacations potentielles que sa salle peut exiger d'infirmiers
 * (générateurs de la salle rapportés au ratio). Le solveur part de l'existant : places actuelles des patients et
 * roulement actuel des infirmiers.
 */
public final class CompletMapper {

    private CompletMapper() {
    }

    public static PlanComplet versProbleme(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        DonneesDOptimisation ref = donnees.sansAleas();
        PlanPatients patients = PatientsMapper.versProbleme(ref, parametres);
        Map<UUID, InfirmierPlan> infirmiers = InfirmiersMapper.infirmiers(ref, parametres);
        List<InfirmierPlan> tous = new ArrayList<>(infirmiers.values());
        RessourcesDuCentre ressources = ref.ressources();
        Set<JourDeLaSemaine> ouverts = ref.joursOuverts();
        int ratio = ref.personnel().ratio().patientsParInfirmier();

        Map<UUID, Integer> generateursParSalle = new HashMap<>();
        for (RessourcesDuCentre.Generateur g : ressources.generateurs()) {
            generateursParSalle.merge(g.salle().valeur(), 1, Integer::sum);
        }
        Map<String, Deque<InfirmierPlan>> roulement = roulementActuel(ref, infirmiers);

        List<Vacation> vacations = new ArrayList<>();
        for (int i = 0; i < JourDeLaSemaine.NOMBRE_DE_JOURS; i++) {
            LocalDate date = parametres.debutSemaine().plusDays(i);
            JourDeLaSemaine jour = JourDeLaSemaine.de(date.getDayOfWeek());
            if (!ouverts.contains(jour)) continue;
            for (RessourcesDuCentre.Salle salle : ressources.salles()) {
                UUID salleId = salle.id().valeur();
                int maxRang = PresenceDeLaSemaine.requis(generateursParSalle.getOrDefault(salleId, 0), ratio);
                if (maxRang == 0) continue;
                boolean iso = salle.isolement();
                List<InfirmierPlan> candidats = InfirmiersMapper.candidats(tous, ref, date, iso, Set.of());
                for (RessourcesDuCentre.Creneau creneau : ressources.creneaux()) {
                    UUID creneauId = creneau.id().valeur();
                    Deque<InfirmierPlan> actuels = roulement.getOrDefault(salleId + "|" + creneauId + "|" + jour,
                            new ArrayDeque<>());
                    for (int rang = 0; rang < maxRang; rang++) {
                        InfirmierPlan initial = null;
                        while (initial == null && !actuels.isEmpty()) {
                            InfirmierPlan suivant = actuels.poll();
                            if (candidats.contains(suivant)) initial = suivant;
                        }
                        vacations.add(new Vacation(date + "|" + salleId + "|" + creneauId + "|C" + rang, date,
                                jour, 0, salleId, creneauId, rang == 0, false, candidats, initial));
                    }
                }
            }
        }
        return new PlanComplet(PatientsMapper.contexte(ref), tous, patients.getPatients(), vacations,
                PoidsOptimisation.complet(parametres));
    }

    /**
     * Patients et vacations de la solution. Les déplacements invalides une fois appliqués étant retirés, le besoin final
     * est recalculé sur les placements retenus : les vacations au-delà du besoin d'une case sont écartées, les vacations
     * manquantes signalées.
     */
    public static Resultat versResultat(PlanComplet solution, DonneesDOptimisation donnees,
                                        ParametresDOptimisation parametres) {
        PatientsMapper.Resultat patients = PatientsMapper.versResultat(solution.getPatients(), donnees);
        DonneesDOptimisation finale = donnees.sansAleas().avecPlacements(patients.placements(), patients.joursChoisis());
        int ratio = finale.personnel().ratio().patientsParInfirmier();
        Set<JourDeLaSemaine> ouverts = finale.joursOuverts();
        Map<String, Integer> patientsParCase = new HashMap<>();
        for (PatientAPlacer p : finale.patients()) {
            Poste poste = p.actuelle();
            if (poste == null || poste.salle() == null || poste.creneau() == null || p.jours().isEmpty()) continue;
            for (JourDeLaSemaine jour : p.jours()) {
                if (ouverts.contains(jour)) {
                    patientsParCase.merge(poste.salle().valeur() + "|" + poste.creneau().valeur() + "|" + jour, 1, Integer::sum);
                }
            }
        }
        Map<UUID, String> noms = new HashMap<>();
        donnees.personnel().infirmiers().forEach(i -> noms.put(i.id(), i.nomAffiche()));

        Map<String, Integer> tenues = new HashMap<>();
        List<VacationPlanifiee> planifiees = new ArrayList<>();
        List<Vacation> triees = new ArrayList<>(solution.getVacations());
        triees.sort(Comparator.comparing(Vacation::getId));
        for (Vacation v : triees) {
            if (v.getInfirmier() == null) continue;
            String cle = v.cleExacte();
            int besoin = PresenceDeLaSemaine.requis(patientsParCase.getOrDefault(cle, 0), ratio);
            if (tenues.getOrDefault(cle, 0) >= besoin) continue;
            tenues.merge(cle, 1, Integer::sum);
            InfirmierPlan i = v.getInfirmier();
            planifiees.add(new VacationPlanifiee(v.getDate(), v.getJour(), new SalleId(v.getSalleId()),
                    new CreneauId(v.getCreneauId()), new InfirmierId(i.id()), noms.getOrDefault(i.id(), i.nom()),
                    i.placesExactes().contains(cle)));
        }
        planifiees.sort(Comparator.comparing(VacationPlanifiee::date).thenComparing(v -> v.creneau().valeur().toString())
                .thenComparing(v -> v.salle().valeur().toString()).thenComparing(VacationPlanifiee::nom));

        List<VacationNonPourvue> manques = new ArrayList<>();
        for (int i = 0; i < JourDeLaSemaine.NOMBRE_DE_JOURS; i++) {
            LocalDate date = parametres.debutSemaine().plusDays(i);
            JourDeLaSemaine jour = JourDeLaSemaine.de(date.getDayOfWeek());
            for (RessourcesDuCentre.Salle salle : finale.ressources().salles()) {
                for (RessourcesDuCentre.Creneau creneau : finale.ressources().creneaux()) {
                    String cle = salle.id().valeur() + "|" + creneau.id().valeur() + "|" + jour;
                    int manque = PresenceDeLaSemaine.requis(patientsParCase.getOrDefault(cle, 0), ratio)
                            - tenues.getOrDefault(cle, 0);
                    if (manque > 0) manques.add(new VacationNonPourvue(date, jour, salle.id(), creneau.id(), manque));
                }
            }
        }
        return new Resultat(patients, planifiees, manques);
    }

    /**
     * Infirmiers du roulement actuel par case « salle|créneau|jour », pour démarrer le calcul depuis l'existant.
     */
    private static Map<String, Deque<InfirmierPlan>> roulementActuel(DonneesDOptimisation donnees,
                                                                     Map<UUID, InfirmierPlan> infirmiers) {
        Map<String, Deque<InfirmierPlan>> parCase = new HashMap<>();
        for (AffectationPrevue a : donnees.personnel().affectations()) {
            InfirmierPlan infirmier = infirmiers.get(a.infirmier());
            if (infirmier == null) continue;
            for (JourDeLaSemaine jour : a.jours()) {
                parCase.computeIfAbsent(a.salle() + "|" + a.creneau() + "|" + jour, k -> new ArrayDeque<>())
                        .add(infirmier);
            }
        }
        return parCase;
    }

    public record Resultat(PatientsMapper.Resultat patients, List<VacationPlanifiee> vacations,
                           List<VacationNonPourvue> manques) {
    }
}
