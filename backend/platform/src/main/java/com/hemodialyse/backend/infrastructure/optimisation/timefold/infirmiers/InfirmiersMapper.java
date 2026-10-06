package com.hemodialyse.backend.infrastructure.optimisation.timefold.infirmiers;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Present;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.StatutCase;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.CleCase;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.PoidsOptimisation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduit le planning de présence en problème de vacations et la solution en vacations planifiées.
 * <ul>
 *   <li><b>Roulement</b> : les données sont celles d'une semaine type ; chaque case exige {@code requis} infirmiers, tous
 *   à désigner (le roulement actuel sert seulement de référence de stabilité) ;</li>
 *   <li><b>Couverture</b> : les infirmiers déjà prévus (roulement moins absences, remplacements en place) sont épinglés ;
 *   seules les vacations manquantes sont à pourvoir, par des infirmiers disponibles sur le créneau.</li>
 * </ul>
 */
public final class InfirmiersMapper {

    private InfirmiersMapper() {
    }

    /**
     * @param donnees données de référence (semaine type pour le roulement, données réelles pour la couverture), avec les
     *                placements à servir
     */
    public static PlanInfirmiers versProbleme(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        boolean couverture = parametres.perimetre() == PerimetreOptimisation.COUVERTURE;
        Map<UUID, InfirmierPlan> infirmiers = infirmiers(donnees, parametres);
        List<InfirmierPlan> tous = new ArrayList<>(infirmiers.values());
        Map<String, List<InfirmierPlan>> candidatsParCle = new HashMap<>();

        Map<String, Set<CompetenceInfirmier>> competencesParCase = competencesParCase(donnees, parametres);
        Set<ExigenceCompetence> exigences = new LinkedHashSet<>();
        List<Vacation> vacations = new ArrayList<>();
        for (int semaine = 0; semaine < parametres.nbSemaines(); semaine++) {
            SemainePresence presence = PresenceInfirmierService.construire(donnees.presence(),
                    parametres.debutSemaine().plusWeeks(semaine));
            Map<String, Set<UUID>> pris = new HashMap<>();
            if (couverture) {
                for (CasePresence c : presence.cases()) {
                    for (Present p : c.presents()) {
                        pris.computeIfAbsent(c.date() + "|" + c.creneauId(), k -> new HashSet<>()).add(p.infirmierId());
                    }
                }
            }
            for (CasePresence c : presence.cases()) {
                if (c.statut() == StatutCase.FERME) continue;
                for (CompetenceInfirmier competence : competencesParCase.getOrDefault(
                        c.salleId() + "|" + c.creneauId() + "|" + c.date(), Set.of())) {
                    exigences.add(new ExigenceCompetence(new CleCase(c.salleId(), c.creneauId(), semaine, c.jour()),
                            competence));
                }
                boolean qualifiePresent = false;
                if (couverture) {
                    for (Present p : c.presents()) {
                        InfirmierPlan infirmier = infirmiers.get(p.infirmierId());
                        if (infirmier == null) continue;
                        qualifiePresent |= !infirmier.aideSoignant();
                        vacations.add(new Vacation(id(c, "P" + p.infirmierId()), c.date(), c.jour(), semaine,
                                c.salleId(), c.creneauId(), false, true, List.of(infirmier), infirmier));
                    }
                }
                int aPourvoir = couverture ? c.manque() : c.requis();
                if (aPourvoir <= 0) continue;
                String cle = c.date() + "|" + c.creneauId() + "|" + c.salleIsolement();
                List<InfirmierPlan> candidats = candidatsParCle.computeIfAbsent(cle, k ->
                        candidats(tous, donnees, c.date(), c.salleIsolement(),
                                pris.getOrDefault(c.date() + "|" + c.creneauId(), Set.of())));
                for (int rang = 0; rang < aPourvoir; rang++) {
                    boolean qualifie = rang == 0 && !qualifiePresent;
                    vacations.add(new Vacation(id(c, "V" + rang), c.date(), c.jour(), semaine, c.salleId(),
                            c.creneauId(), qualifie, false, candidats, null));
                }
            }
        }
        return new PlanInfirmiers(tous, List.copyOf(exigences), vacations, PoidsOptimisation.infirmiers(parametres));
    }

    /**
     * Compétences demandées, par case datée « salle|créneau|date », par les patients qui y dialysent.
     */
    private static Map<String, Set<CompetenceInfirmier>> competencesParCase(DonneesOptimisation donnees,
                                                                            ParametresOptimisation parametres) {
        Map<UUID, Set<CompetenceInfirmier>> parPatient = new HashMap<>();
        for (PatientAPlacer p : donnees.patients()) {
            if (!p.competencesRequises().isEmpty()) parPatient.put(p.patientId(), p.competencesRequises());
        }
        Map<String, Set<CompetenceInfirmier>> parCase = new HashMap<>();
        if (parPatient.isEmpty()) return parCase;
        for (Occupation o : donnees.planning().occupations()) {
            Set<CompetenceInfirmier> competences = parPatient.get(o.patientId());
            if (competences == null) continue;
            for (LocalDate date = parametres.debutSemaine(); !date.isAfter(parametres.finHorizon());
                 date = date.plusDays(1)) {
                if (!o.jours().contains(JourSemaine.de(date.getDayOfWeek())) || !o.occupeLe(date)) continue;
                parCase.computeIfAbsent(o.salleId() + "|" + o.creneauId() + "|" + date,
                        k -> EnumSet.noneOf(CompetenceInfirmier.class)).addAll(competences);
            }
        }
        return parCase;
    }

    /**
     * Vacations tenues et vacations non pourvues d'une solution. {@code existante} : épinglée (couverture) ou déjà au
     * roulement actuel de l'infirmier (roulement).
     */
    public static Resultat versResultat(PlanInfirmiers solution, DonneesOptimisation donnees) {
        Map<UUID, String> noms = new HashMap<>();
        donnees.presence().infirmiers().forEach(i -> noms.put(i.id(), i.nom()));
        List<VacationPlanifiee> planifiees = new ArrayList<>();
        Map<String, int[]> manques = new LinkedHashMap<>();
        Map<String, Vacation> echantillons = new HashMap<>();
        for (Vacation v : solution.getVacations()) {
            if (v.getInfirmier() != null) {
                InfirmierPlan i = v.getInfirmier();
                boolean existante = v.isEpinglee() || i.placesExactes().contains(v.cleExacte());
                planifiees.add(new VacationPlanifiee(v.getDate(), v.getJour(), v.getSalleId(), v.getCreneauId(), i.id(),
                        noms.getOrDefault(i.id(), i.nom()), existante));
            } else {
                String cle = v.getDate() + "|" + v.getSalleId() + "|" + v.getCreneauId();
                manques.computeIfAbsent(cle, k -> new int[1])[0]++;
                echantillons.putIfAbsent(cle, v);
            }
        }
        planifiees.sort(Comparator.comparing(VacationPlanifiee::date).thenComparing(v -> v.creneauId().toString())
                .thenComparing(v -> v.salleId().toString()).thenComparing(VacationPlanifiee::nom));
        List<VacationNonPourvue> sansInfirmier = new ArrayList<>();
        manques.forEach((cle, n) -> {
            Vacation v = echantillons.get(cle);
            sansInfirmier.add(new VacationNonPourvue(v.getDate(), v.getJour(), v.getSalleId(), v.getCreneauId(), n[0]));
        });
        sansInfirmier.sort(Comparator.comparing(VacationNonPourvue::date).thenComparing(m -> m.creneauId().toString()));
        return new Resultat(planifiees, sansInfirmier);
    }

    public record Resultat(List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques) {
    }

    private static String id(CasePresence c, String suffixe) {
        return c.date() + "|" + c.salleId() + "|" + c.creneauId() + "|" + suffixe;
    }

    /**
     * Infirmiers qui peuvent tenir une vacation de la case : pas absents ce jour, habilités si la salle est en
     * isolement, pas déjà prévus sur ce créneau ce jour-là.
     */
    public static List<InfirmierPlan> candidats(List<InfirmierPlan> tous, DonneesOptimisation donnees, LocalDate date,
                                         boolean salleIsolement, Set<UUID> dejaPrevus) {
        List<InfirmierPlan> candidats = new ArrayList<>();
        for (InfirmierPlan i : tous) {
            if (salleIsolement && !i.habiliteIsolement()) continue;
            if (dejaPrevus.contains(i.id())) continue;
            if (absentLe(donnees, i.id(), date)) continue;
            candidats.add(i);
        }
        return candidats;
    }

    static boolean absentLe(DonneesOptimisation donnees, UUID infirmierId, LocalDate date) {
        for (AbsenceInfirmier a : donnees.presence().absences()) {
            if (a.infirmierId().equals(infirmierId) && a.couvre(date)) return true;
        }
        return false;
    }

    public static Map<UUID, InfirmierPlan> infirmiers(DonneesOptimisation donnees, ParametresOptimisation parametres) {
        Map<UUID, Set<UUID>> salles = new HashMap<>();
        Map<UUID, Set<UUID>> creneaux = new HashMap<>();
        Map<UUID, Set<String>> exactes = new HashMap<>();
        for (AffectationInfirmier a : donnees.presence().affectations()) {
            salles.computeIfAbsent(a.infirmierId(), k -> new HashSet<>()).add(a.salleId());
            creneaux.computeIfAbsent(a.infirmierId(), k -> new HashSet<>()).add(a.creneauId());
            for (JourSemaine jour : a.jours()) {
                exactes.computeIfAbsent(a.infirmierId(), k -> new HashSet<>())
                        .add(a.salleId() + "|" + a.creneauId() + "|" + jour);
            }
        }
        Map<UUID, InfirmierPlan> infirmiers = new LinkedHashMap<>();
        for (InfirmierRef ref : donnees.presence().infirmiers()) {
            ProfilInfirmier profil = donnees.profil(ref.id());
            infirmiers.put(ref.id(), new InfirmierPlan(ref.id(), ref.nom(),
                    ref.qualification() == QualificationInfirmier.AIDE_SOIGNANT, ref.habiliteIsolement(),
                    parametres.maxVacationsParJour(), parametres.maxVacationsParSemaine(),
                    salles.get(ref.id()), creneaux.get(ref.id()), exactes.get(ref.id()),
                    JourSemaine.NB_JOURS - parametres.reposHebdoMin(), parametres.heuresParVacation(),
                    profil.quotaHeures(parametres.heuresHebdoTempsPlein()), profil.competences()));
        }
        return infirmiers;
    }
}
