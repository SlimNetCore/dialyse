package com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.infirmiers;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Present;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.StatutDeCase;
import com.hemodialyse.backend.contexts.planification.domain.PresenceDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.CleCase;
import com.hemodialyse.backend.contexts.planification.infrastructure.optimisation.timefold.PoidsOptimisation;

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
    public static PlanInfirmiers versProbleme(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        boolean couverture = parametres.perimetre() == Perimetre.COUVERTURE;
        Map<UUID, InfirmierPlan> infirmiers = infirmiers(donnees, parametres);
        List<InfirmierPlan> tous = new ArrayList<>(infirmiers.values());
        Map<String, List<InfirmierPlan>> candidatsParCle = new HashMap<>();

        Map<String, Set<Competence>> competencesParCase = competencesParCase(donnees, parametres);
        Set<ExigenceCompetence> exigences = new LinkedHashSet<>();
        List<Vacation> vacations = new ArrayList<>();
        for (int semaine = 0; semaine < parametres.semainesCalcul(); semaine++) {
            SemainePresence presence = PresenceDeLaSemaine.construire(donnees.presence(),
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
                if (c.statut() == StatutDeCase.FERME) continue;
                for (Competence competence : competencesParCase.getOrDefault(
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
    private static Map<String, Set<Competence>> competencesParCase(DonneesDOptimisation donnees,
                                                                   ParametresDOptimisation parametres) {
        Map<String, Set<Competence>> parCase = new HashMap<>();
        for (PatientAPlacer p : donnees.patients()) {
            Poste poste = p.actuelle();
            if (p.competencesRequises().isEmpty() || poste == null || poste.salle() == null || poste.creneau() == null
                    || p.jours().isEmpty()) {
                continue;
            }
            for (LocalDate date = parametres.debutSemaine(); !date.isAfter(parametres.finCalcul());
                 date = date.plusDays(1)) {
                if (!p.jours().contains(JourDeLaSemaine.de(date.getDayOfWeek())) || !occupeLe(p, date)) continue;
                parCase.computeIfAbsent(poste.salle().valeur() + "|" + poste.creneau().valeur() + "|" + date,
                        k -> EnumSet.noneOf(Competence.class)).addAll(p.competencesRequises());
            }
        }
        return parCase;
    }

    private static boolean occupeLe(PatientAPlacer p, LocalDate date) {
        return (p.premierJour() == null || !date.isBefore(p.premierJour()))
                && (p.dernierJour() == null || !date.isAfter(p.dernierJour()));
    }

    /**
     * Vacations tenues et vacations non pourvues d'une solution. {@code existante} : épinglée (couverture) ou déjà au
     * roulement actuel de l'infirmier (roulement).
     */
    public static Resultat versResultat(PlanInfirmiers solution, DonneesDOptimisation donnees) {
        Map<UUID, String> noms = new HashMap<>();
        donnees.personnel().infirmiers().forEach(i -> noms.put(i.id(), i.nomAffiche()));
        List<VacationPlanifiee> planifiees = new ArrayList<>();
        Map<String, int[]> manques = new LinkedHashMap<>();
        Map<String, Vacation> echantillons = new HashMap<>();
        for (Vacation v : solution.getVacations()) {
            if (v.getInfirmier() != null) {
                InfirmierPlan i = v.getInfirmier();
                boolean existante = v.isEpinglee() || i.placesExactes().contains(v.cleExacte());
                planifiees.add(new VacationPlanifiee(v.getDate(), v.getJour(), new SalleId(v.getSalleId()),
                        new CreneauId(v.getCreneauId()), new InfirmierId(i.id()), noms.getOrDefault(i.id(), i.nom()),
                        existante));
            } else {
                String cle = v.getDate() + "|" + v.getSalleId() + "|" + v.getCreneauId();
                manques.computeIfAbsent(cle, k -> new int[1])[0]++;
                echantillons.putIfAbsent(cle, v);
            }
        }
        planifiees.sort(Comparator.comparing(VacationPlanifiee::date).thenComparing(v -> v.creneau().valeur().toString())
                .thenComparing(v -> v.salle().valeur().toString()).thenComparing(VacationPlanifiee::nom));
        List<VacationNonPourvue> sansInfirmier = new ArrayList<>();
        manques.forEach((cle, n) -> {
            Vacation v = echantillons.get(cle);
            sansInfirmier.add(new VacationNonPourvue(v.getDate(), v.getJour(), new SalleId(v.getSalleId()),
                    new CreneauId(v.getCreneauId()), n[0]));
        });
        sansInfirmier.sort(Comparator.comparing(VacationNonPourvue::date)
                .thenComparing(m -> m.creneau().valeur().toString()));
        return new Resultat(planifiees, sansInfirmier);
    }

    private static String id(CasePresence c, String suffixe) {
        return c.date() + "|" + c.salleId() + "|" + c.creneauId() + "|" + suffixe;
    }

    /**
     * Infirmiers qui peuvent tenir une vacation de la case : pas absents ce jour, habilités si la salle est en
     * isolement, pas déjà prévus sur ce créneau ce jour-là.
     */
    public static List<InfirmierPlan> candidats(List<InfirmierPlan> tous, DonneesDOptimisation donnees, LocalDate date,
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

    static boolean absentLe(DonneesDOptimisation donnees, UUID infirmierId, LocalDate date) {
        for (AbsenceConnue a : donnees.personnel().absences()) {
            if (a.infirmier().equals(infirmierId) && a.couvre(date)) return true;
        }
        return false;
    }

    public static Map<UUID, InfirmierPlan> infirmiers(DonneesDOptimisation donnees, ParametresDOptimisation parametres) {
        Map<UUID, Set<UUID>> salles = new HashMap<>();
        Map<UUID, Set<UUID>> creneaux = new HashMap<>();
        Map<UUID, Set<String>> exactes = new HashMap<>();
        for (AffectationPrevue a : donnees.personnel().affectations()) {
            salles.computeIfAbsent(a.infirmier(), k -> new HashSet<>()).add(a.salle());
            creneaux.computeIfAbsent(a.infirmier(), k -> new HashSet<>()).add(a.creneau());
            for (JourDeLaSemaine jour : a.jours()) {
                exactes.computeIfAbsent(a.infirmier(), k -> new HashSet<>())
                        .add(a.salle() + "|" + a.creneau() + "|" + jour);
            }
        }
        Map<UUID, InfirmierPlan> infirmiers = new LinkedHashMap<>();
        for (InfirmierDeLaPresence ref : donnees.personnel().infirmiers()) {
            ProfilDePlanification profil = donnees.profil(ref.id());
            infirmiers.put(ref.id(), new InfirmierPlan(ref.id(), ref.nomAffiche(),
                    ref.qualification() == Qualification.AIDE_SOIGNANT, ref.habiliteIsolement(),
                    parametres.maxVacationsParJour(), parametres.maxVacationsParSemaine(),
                    salles.get(ref.id()), creneaux.get(ref.id()), exactes.get(ref.id()),
                    JourDeLaSemaine.NOMBRE_DE_JOURS - parametres.reposHebdoMin(), parametres.heuresParVacation(),
                    quotaHeures(profil, parametres.heuresHebdoTempsPlein()), profil.competences()));
        }
        return infirmiers;
    }

    /**
     * Heures hebdomadaires autorisées : celles d'un temps plein au prorata du taux d'activité.
     */
    static int quotaHeures(ProfilDePlanification profil, int heuresHebdoTempsPlein) {
        return (int) Math.round(heuresHebdoTempsPlein * profil.tauxActivite() / 100.0);
    }

    public record Resultat(List<VacationPlanifiee> vacations, List<VacationNonPourvue> manques) {
    }
}
