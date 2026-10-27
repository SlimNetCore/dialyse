package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Absent;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlerteDoubleAffectation;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlertePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AlerteSureffectif;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.ConflitPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.JourDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Presence.PlanningDesPlaces;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Present;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RemplacementPose;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Salle;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.StatutDeCase;
import com.hemodialyse.backend.contexts.planification.domain.Presence.TypeDeConflit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service de domaine pur (RG-INF-040 à 047) : la présence des infirmiers d'une semaine (dimanche → samedi). Chaque case compare les
 * infirmiers prévus (roulement, absences déduites, remplaçants ajoutés) à l'effectif requis par le ratio de sécurité ; en salle
 * d'isolement, seuls les infirmiers habilités comptent. Sert aussi les alertes de sous-effectif, de sur-effectif et de double
 * affectation, et les cases dont dépendent le classement des remplaçants et la charge.
 */
public final class PresenceDeLaSemaine {

    private PresenceDeLaSemaine() {
    }

    /** Le dimanche de la semaine qui contient {@code date}. */
    public static LocalDate debutDeSemaine(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() % 7L);
    }

    /** Infirmiers requis pour un nombre de patients : arrondi à l'entier supérieur, 0 sans patient. */
    public static int requis(int patients, int patientsParInfirmier) {
        return patients <= 0 ? 0 : (patients + patientsParInfirmier - 1) / patientsParInfirmier;
    }

    public static SemainePresence construire(DonneesDePresence donnees, LocalDate debut) {
        PlanningDesPlaces planning = donnees.planning();
        OuvertureDuCentre ouverture = OuvertureDuCentre.de(planning);
        Map<UUID, InfirmierDeLaPresence> infirmiers = new HashMap<>();
        donnees.personnel().infirmiers().forEach(i -> infirmiers.put(i.id(), i));
        Map<String, Integer> patients = patientsParCase(planning.occupations(), debut);

        List<JourDuPlanning> jours = new ArrayList<>();
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            LocalDate date = debut.plusDays(jour.ordinal());
            jours.add(new JourDuPlanning(jour, date, ouverture.ouvertHebdomadaire(jour), ouverture.motifDeFermeture(date)));
        }

        List<CasePresence> cases = new ArrayList<>();
        List<ConflitPresence> conflits = new ArrayList<>();
        for (Creneau creneau : planning.creneaux()) {
            for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                LocalDate date = debut.plusDays(jour.ordinal());
                Map<UUID, UUID> salleParInfirmier = new LinkedHashMap<>();
                for (Salle salle : planning.salles()) {
                    CasePresence c = calculerCase(donnees, infirmiers, ouverture, patients, salle.id(), creneau.id(), jour, date);
                    cases.add(c);
                    for (Present p : c.presents()) {
                        UUID deja = salleParInfirmier.putIfAbsent(p.infirmierId(), salle.id());
                        if (deja != null && !deja.equals(salle.id())) {
                            conflits.add(new ConflitPresence(TypeDeConflit.DOUBLE_AFFECTATION, date, jour, salle.id(), creneau.id(), p.nom()));
                        }
                        if (c.salleIsolement() && !p.habiliteIsolement()) {
                            conflits.add(new ConflitPresence(TypeDeConflit.NON_HABILITE, date, jour, salle.id(), creneau.id(), p.nom()));
                        }
                    }
                }
            }
        }
        int sousEffectif = (int) cases.stream().filter(c -> c.statut() == StatutDeCase.SOUS_EFFECTIF).count();
        return new SemainePresence(debut, debut.plusDays(6), jours, planning.salles(), planning.creneaux(), cases, conflits,
                donnees.patientsParInfirmier(), sousEffectif);
    }

    /** Cases en sous-effectif entre deux dates (bornes incluses), de la plus proche à la plus lointaine. */
    public static List<AlertePresence> alertes(DonneesDePresence donnees, LocalDate du, LocalDate au) {
        Map<UUID, Integer> ordre = ordreDesCreneaux(donnees);
        List<AlertePresence> alertes = new ArrayList<>();
        for (CasePresence c : casesEntre(donnees, du, au)) {
            if (c.statut() != StatutDeCase.SOUS_EFFECTIF) continue;
            alertes.add(new AlertePresence(c.date(), c.jour(), c.salleId(), c.creneauId(), c.patients(), c.requis(), c.manque(),
                    c.absents().stream().map(Absent::nom).toList()));
        }
        alertes.sort(Comparator.comparing(AlertePresence::date).thenComparing(a -> ordre.getOrDefault(a.creneauId(), 0)));
        return alertes;
    }

    /** Cases en sur-effectif entre deux dates (bornes incluses) : infirmiers prévus au-delà de l'effectif requis. */
    public static List<AlerteSureffectif> sureffectifs(DonneesDePresence donnees, LocalDate du, LocalDate au) {
        Map<UUID, Integer> ordre = ordreDesCreneaux(donnees);
        List<AlerteSureffectif> alertes = new ArrayList<>();
        for (CasePresence c : casesEntre(donnees, du, au)) {
            if (c.surplus() <= 0) continue;
            alertes.add(new AlerteSureffectif(c.date(), c.jour(), c.salleId(), c.creneauId(), c.patients(), c.requis(), c.surplus()));
        }
        alertes.sort(Comparator.comparing(AlerteSureffectif::date).thenComparing(a -> ordre.getOrDefault(a.creneauId(), 0)));
        return alertes;
    }

    /** Infirmiers prévus dans plusieurs salles au même créneau le même jour, entre deux dates (bornes incluses). */
    public static List<AlerteDoubleAffectation> doublesAffectations(DonneesDePresence donnees, LocalDate du, LocalDate au) {
        Map<UUID, Integer> ordre = ordreDesCreneaux(donnees);
        Map<String, List<UUID>> salles = new LinkedHashMap<>();
        Map<String, Present> infirmier = new HashMap<>();
        Map<String, CasePresence> reperes = new HashMap<>();
        for (CasePresence c : casesEntre(donnees, du, au)) {
            for (Present p : c.presents()) {
                String cle = c.date() + "|" + c.creneauId() + "|" + p.infirmierId();
                salles.computeIfAbsent(cle, k -> new ArrayList<>()).add(c.salleId());
                infirmier.putIfAbsent(cle, p);
                reperes.putIfAbsent(cle, c);
            }
        }
        List<AlerteDoubleAffectation> alertes = new ArrayList<>();
        salles.forEach((cle, liste) -> {
            if (liste.size() > 1) {
                CasePresence c = reperes.get(cle);
                Present p = infirmier.get(cle);
                alertes.add(new AlerteDoubleAffectation(c.date(), c.jour(), c.creneauId(), p.infirmierId(), p.nom(), List.copyOf(liste)));
            }
        });
        alertes.sort(Comparator.comparing(AlerteDoubleAffectation::date)
                .thenComparing(a -> ordre.getOrDefault(a.creneauId(), 0)).thenComparing(AlerteDoubleAffectation::infirmier));
        return alertes;
    }

    static Optional<AbsenceConnue> absenceLe(DonneesDePresence donnees, UUID infirmier, LocalDate date) {
        return donnees.personnel().absences().stream().filter(a -> a.infirmier().equals(infirmier) && a.couvre(date)).findFirst();
    }

    private static Map<UUID, Integer> ordreDesCreneaux(DonneesDePresence donnees) {
        Map<UUID, Integer> ordre = new HashMap<>();
        List<Creneau> creneaux = donnees.planning().creneaux();
        for (int i = 0; i < creneaux.size(); i++) ordre.put(creneaux.get(i).id(), i);
        return ordre;
    }

    private static List<CasePresence> casesEntre(DonneesDePresence donnees, LocalDate du, LocalDate au) {
        List<CasePresence> cases = new ArrayList<>();
        for (LocalDate debut = debutDeSemaine(du); !debut.isAfter(au); debut = debut.plusDays(7)) {
            for (CasePresence c : construire(donnees, debut).cases()) {
                if (!c.date().isBefore(du) && !c.date().isAfter(au)) cases.add(c);
            }
        }
        return cases;
    }

    private static CasePresence calculerCase(DonneesDePresence donnees, Map<UUID, InfirmierDeLaPresence> infirmiers,
                                             OuvertureDuCentre ouverture, Map<String, Integer> patients, UUID salleId, UUID creneauId,
                                             JourDeLaSemaine jour, LocalDate date) {
        boolean salleIsolement = donnees.planning().sallesIsolement().contains(salleId);
        int nbPatients = patients.getOrDefault(cle(salleId, creneauId, jour), 0);
        if (ouverture.ferme(date)) {
            return new CasePresence(salleId, creneauId, jour, date, nbPatients, 0, salleIsolement, StatutDeCase.FERME, 0, 0, List.of(),
                    List.of());
        }
        Set<UUID> dejaListes = new LinkedHashSet<>();
        List<Present> presents = new ArrayList<>();
        List<Absent> absents = new ArrayList<>();
        for (AffectationPrevue a : donnees.personnel().affectations()) {
            InfirmierDeLaPresence ref = infirmiers.get(a.infirmier());
            if (ref == null || !a.salle().equals(salleId) || !a.creneau().equals(creneauId) || !a.jours().contains(jour)
                    || !dejaListes.add(ref.id())) {
                continue;
            }
            Optional<AbsenceConnue> absence = absenceLe(donnees, ref.id(), date);
            if (absence.isPresent()) {
                absents.add(new Absent(ref.id(), ref.nomAffiche(), absence.get().type()));
            } else {
                presents.add(new Present(ref.id(), ref.nomAffiche(), ref.habiliteIsolement(), false, null));
            }
        }
        for (RemplacementPose r : donnees.personnel().remplacements()) {
            InfirmierDeLaPresence ref = infirmiers.get(r.infirmier());
            if (ref == null || !r.date().equals(date) || !r.salle().equals(salleId) || !r.creneau().equals(creneauId)
                    || absenceLe(donnees, ref.id(), date).isPresent() || !dejaListes.add(ref.id())) {
                continue;
            }
            presents.add(new Present(ref.id(), ref.nomAffiche(), ref.habiliteIsolement(), true, r.id()));
        }
        presents.sort(Comparator.comparing(Present::nom));
        absents.sort(Comparator.comparing(Absent::nom));

        int requis = requis(nbPatients, donnees.patientsParInfirmier());
        int comptes = (int) presents.stream().filter(p -> !salleIsolement || p.habiliteIsolement()).count();
        int manque = Math.max(0, requis - comptes);
        int surplus = Math.max(0, comptes - requis);
        StatutDeCase statut = nbPatients == 0 ? StatutDeCase.SANS_PATIENT : manque > 0 ? StatutDeCase.SOUS_EFFECTIF : StatutDeCase.COUVERT;
        return new CasePresence(salleId, creneauId, jour, date, nbPatients, requis, salleIsolement, statut, manque, surplus, presents,
                absents);
    }

    /** Patients par case de la semaine : un patient sorti ou pas encore admis ne compte pas ce jour-là. */
    private static Map<String, Integer> patientsParCase(List<Occupation> occupations, LocalDate debut) {
        Map<String, Integer> parCase = new HashMap<>();
        for (Occupation o : occupations) {
            for (JourDeLaSemaine jour : o.jours()) {
                if (!o.occupeLe(debut.plusDays(jour.ordinal()))) continue;
                parCase.merge(cle(o.salle(), o.creneau(), jour), 1, Integer::sum);
            }
        }
        return parCase;
    }

    private static String cle(UUID salle, UUID creneau, JourDeLaSemaine jour) {
        return salle + "|" + creneau + "|" + jour;
    }
}
