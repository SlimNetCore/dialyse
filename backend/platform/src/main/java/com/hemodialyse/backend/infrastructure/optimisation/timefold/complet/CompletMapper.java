package com.hemodialyse.backend.infrastructure.optimisation.timefold.complet;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmierPlan;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.InfirmiersMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers.Vacation;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PatientsMapper;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.patients.PlanPatients;

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

    public static PlanComplet versProbleme(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        DonneesOptimisation ref = donnees.sansAleas();
        PlanPatients patients = PatientsMapper.versProbleme(ref, parametres);
        Map<UUID, InfirmierPlan> infirmiers = InfirmiersMapper.infirmiers(ref, parametres);
        List<InfirmierPlan> tous = new ArrayList<>(infirmiers.values());
        DonneesPlanning planning = ref.planning();
        Set<UUID> isolement = planning.sallesIsolement() == null ? Set.of() : planning.sallesIsolement();
        Set<JourSemaine> ouverts = ref.joursOuverts();
        int ratio = ref.presence().patientsParInfirmier();

        Map<UUID, Integer> generateursParSalle = new HashMap<>();
        for (GenerateurRef g : planning.generateurs()) generateursParSalle.merge(g.salleId(), 1, Integer::sum);
        Map<String, Deque<InfirmierPlan>> roulement = roulementActuel(ref, infirmiers);

        List<Vacation> vacations = new ArrayList<>();
        for (int i = 0; i < JourSemaine.NB_JOURS; i++) {
            LocalDate date = parametres.debutSemaine().plusDays(i);
            JourSemaine jour = JourSemaine.de(date.getDayOfWeek());
            if (!ouverts.contains(jour)) continue;
            for (SalleRef salle : planning.salles()) {
                int maxRang = PresenceInfirmierService.requis(generateursParSalle.getOrDefault(salle.id(), 0), ratio);
                if (maxRang == 0) continue;
                boolean iso = isolement.contains(salle.id());
                List<InfirmierPlan> candidats = InfirmiersMapper.candidats(tous, ref, date, iso, Set.of());
                for (CreneauRef creneau : planning.creneaux()) {
                    Deque<InfirmierPlan> actuels = roulement.getOrDefault(salle.id() + "|" + creneau.id() + "|" + jour,
                            new ArrayDeque<>());
                    for (int rang = 0; rang < maxRang; rang++) {
                        InfirmierPlan initial = null;
                        while (initial == null && !actuels.isEmpty()) {
                            InfirmierPlan suivant = actuels.poll();
                            if (candidats.contains(suivant)) initial = suivant;
                        }
                        vacations.add(new Vacation(date + "|" + salle.id() + "|" + creneau.id() + "|C" + rang, date,
                                jour, 0, salle.id(), creneau.id(), rang == 0, false, candidats, initial));
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
    public static Resultat versResultat(PlanComplet solution, DonneesOptimisation donnees,
                                        ParametresOptimisation parametres) {
        PatientsMapper.Resultat patients = PatientsMapper.versResultat(solution.getPatients(), donnees);
        DonneesOptimisation finale = donnees.sansAleas().avecPlacements(patients.placements(), patients.joursChoisis());
        int ratio = finale.presence().patientsParInfirmier();
        Set<JourSemaine> ouverts = finale.joursOuverts();
        Map<String, Integer> patientsParCase = new HashMap<>();
        for (Occupation o : finale.planning().occupations()) {
            for (JourSemaine jour : o.jours()) {
                if (ouverts.contains(jour)) patientsParCase.merge(o.salleId() + "|" + o.creneauId() + "|" + jour, 1,
                        Integer::sum);
            }
        }
        Map<UUID, String> noms = new HashMap<>();
        donnees.presence().infirmiers().forEach(i -> noms.put(i.id(), i.nom()));

        Map<String, Integer> tenues = new HashMap<>();
        List<VacationPlanifiee> planifiees = new ArrayList<>();
        List<Vacation> triees = new ArrayList<>(solution.getVacations());
        triees.sort(Comparator.comparing(Vacation::getId));
        for (Vacation v : triees) {
            if (v.getInfirmier() == null) continue;
            String cle = v.cleExacte();
            int besoin = PresenceInfirmierService.requis(patientsParCase.getOrDefault(cle, 0), ratio);
            if (tenues.getOrDefault(cle, 0) >= besoin) continue;
            tenues.merge(cle, 1, Integer::sum);
            InfirmierPlan i = v.getInfirmier();
            planifiees.add(new VacationPlanifiee(v.getDate(), v.getJour(), v.getSalleId(), v.getCreneauId(), i.id(),
                    noms.getOrDefault(i.id(), i.nom()), i.placesExactes().contains(cle)));
        }
        planifiees.sort(Comparator.comparing(VacationPlanifiee::date).thenComparing(v -> v.creneauId().toString())
                .thenComparing(v -> v.salleId().toString()).thenComparing(VacationPlanifiee::nom));

        List<VacationNonPourvue> manques = new ArrayList<>();
        for (int i = 0; i < JourSemaine.NB_JOURS; i++) {
            LocalDate date = parametres.debutSemaine().plusDays(i);
            JourSemaine jour = JourSemaine.de(date.getDayOfWeek());
            for (SalleRef salle : finale.planning().salles()) {
                for (CreneauRef creneau : finale.planning().creneaux()) {
                    String cle = salle.id() + "|" + creneau.id() + "|" + jour;
                    int manque = PresenceInfirmierService.requis(patientsParCase.getOrDefault(cle, 0), ratio)
                            - tenues.getOrDefault(cle, 0);
                    if (manque > 0) manques.add(new VacationNonPourvue(date, jour, salle.id(), creneau.id(), manque));
                }
            }
        }
        return new Resultat(patients, planifiees, manques);
    }

    public record Resultat(PatientsMapper.Resultat patients, List<VacationPlanifiee> vacations,
                           List<VacationNonPourvue> manques) {
    }

    /**
     * Infirmiers du roulement actuel par case « salle|créneau|jour », pour démarrer le calcul depuis l'existant.
     */
    private static Map<String, Deque<InfirmierPlan>> roulementActuel(DonneesOptimisation donnees,
                                                                     Map<UUID, InfirmierPlan> infirmiers) {
        Map<String, Deque<InfirmierPlan>> parCase = new HashMap<>();
        for (AffectationInfirmier a : donnees.presence().affectations()) {
            InfirmierPlan infirmier = infirmiers.get(a.infirmierId());
            if (infirmier == null) continue;
            for (JourSemaine jour : a.jours()) {
                parCase.computeIfAbsent(a.salleId() + "|" + a.creneauId() + "|" + jour, k -> new ArrayDeque<>())
                        .add(infirmier);
            }
        }
        return parCase;
    }
}
