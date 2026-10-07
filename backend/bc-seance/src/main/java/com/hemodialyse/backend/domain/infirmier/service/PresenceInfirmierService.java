package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Absent;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlerteSureffectif;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.ConflitPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CreneauPersonnel;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SituationPersonnelle;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Present;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.StatutCase;
import com.hemodialyse.backend.domain.infirmier.model.Presence.TypeConflit;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.JourPlanning;
import com.hemodialyse.backend.domain.planning.service.PlanningSemaineService;

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
 * Service de domaine pur : construit le planning de présence des infirmiers d'une semaine. Chaque case (salle, créneau,
 * jour) compare les infirmiers prévus — roulement, absences déduites, remplaçants ajoutés — au nombre requis par le
 * ratio de sécurité. En salle d'isolement, seuls les infirmiers habilités comptent dans l'effectif.
 */
public final class PresenceInfirmierService {

    private PresenceInfirmierService() {
    }

    public static SemainePresence construire(DonneesPresence donnees, LocalDate debut) {
        DonneesPlanning planning = donnees.planning();
        CalendrierCentre calendrier = CalendrierCentre.de(planning);
        Set<UUID> isolement = planning.sallesIsolement() == null ? Set.of() : planning.sallesIsolement();
        Map<UUID, InfirmierRef> infirmiers = new HashMap<>();
        donnees.infirmiers().forEach(i -> infirmiers.put(i.id(), i));
        Map<String, Integer> patients = patientsParCase(planning.occupations(), debut);

        List<JourPlanning> jours = new ArrayList<>();
        Set<JourSemaine> ouverts = planning.joursOuverts();
        for (JourSemaine jour : JourSemaine.values()) {
            LocalDate date = debut.plusDays(jour.ordinal());
            String motif = planning.fermetures() == null ? null : planning.fermetures().stream()
                    .filter(f -> f.date().equals(date)).map(f -> f.motif() == null ? "" : f.motif()).findFirst().orElse(null);
            jours.add(new JourPlanning(jour, date, ouverts == null || ouverts.isEmpty() || ouverts.contains(jour), motif));
        }

        List<CasePresence> cases = new ArrayList<>();
        List<ConflitPresence> conflits = new ArrayList<>();
        for (CreneauRef creneau : planning.creneaux()) {
            for (JourSemaine jour : JourSemaine.values()) {
                LocalDate date = debut.plusDays(jour.ordinal());
                Map<UUID, UUID> salleParInfirmier = new LinkedHashMap<>();
                for (SalleRef salle : planning.salles()) {
                    CasePresence c = calculerCase(donnees, infirmiers, calendrier, isolement, patients, salle.id(),
                            creneau.id(), jour, date);
                    cases.add(c);
                    for (Present p : c.presents()) {
                        UUID deja = salleParInfirmier.putIfAbsent(p.infirmierId(), salle.id());
                        if (deja != null && !deja.equals(salle.id())) {
                            conflits.add(new ConflitPresence(TypeConflit.DOUBLE_AFFECTATION, date, jour, salle.id(),
                                    creneau.id(), p.nom()));
                        }
                        if (c.salleIsolement() && !p.habiliteIsolement()) {
                            conflits.add(new ConflitPresence(TypeConflit.NON_HABILITE, date, jour, salle.id(),
                                    creneau.id(), p.nom()));
                        }
                    }
                }
            }
        }
        int sousEffectif = (int) cases.stream().filter(c -> c.statut() == StatutCase.SOUS_EFFECTIF).count();
        return new SemainePresence(debut, debut.plusDays(6), jours, planning.salles(), planning.creneaux(), cases,
                conflits, donnees.patientsParInfirmier(), sousEffectif);
    }

    /**
     * Cases en sous-effectif entre deux dates (bornes incluses), de la plus proche à la plus lointaine.
     */
    public static List<AlertePresence> alertes(DonneesPresence donnees, LocalDate du, LocalDate au) {
        Map<UUID, Integer> ordreCreneau = new HashMap<>();
        for (int i = 0; i < donnees.planning().creneaux().size(); i++) {
            ordreCreneau.put(donnees.planning().creneaux().get(i).id(), i);
        }
        List<AlertePresence> alertes = new ArrayList<>();
        for (LocalDate debut = PlanningSemaineService.debutSemaine(du); !debut.isAfter(au); debut = debut.plusDays(7)) {
            for (CasePresence c : construire(donnees, debut).cases()) {
                if (c.statut() != StatutCase.SOUS_EFFECTIF || c.date().isBefore(du) || c.date().isAfter(au)) continue;
                alertes.add(new AlertePresence(c.date(), c.jour(), c.salleId(), c.creneauId(), c.patients(), c.requis(),
                        c.manque(), c.absents().stream().map(Absent::nom).toList()));
            }
        }
        alertes.sort(Comparator.comparing(AlertePresence::date)
                .thenComparing(a -> ordreCreneau.getOrDefault(a.creneauId(), 0)));
        return alertes;
    }

    /**
     * Cases en sur-effectif entre deux dates (bornes incluses), de la plus proche à la plus lointaine : infirmiers
     * prévus au-delà de l'effectif requis, y compris dans une salle sans patient. Un jour fermé n'en compte pas.
     */
    public static List<AlerteSureffectif> alertesSureffectif(DonneesPresence donnees, LocalDate du, LocalDate au) {
        Map<UUID, Integer> ordreCreneau = new HashMap<>();
        for (int i = 0; i < donnees.planning().creneaux().size(); i++) {
            ordreCreneau.put(donnees.planning().creneaux().get(i).id(), i);
        }
        List<AlerteSureffectif> alertes = new ArrayList<>();
        for (LocalDate debut = PlanningSemaineService.debutSemaine(du); !debut.isAfter(au); debut = debut.plusDays(7)) {
            for (CasePresence c : construire(donnees, debut).cases()) {
                if (c.surplus() <= 0 || c.date().isBefore(du) || c.date().isAfter(au)) continue;
                alertes.add(new AlerteSureffectif(c.date(), c.jour(), c.salleId(), c.creneauId(), c.patients(),
                        c.requis(), c.surplus()));
            }
        }
        alertes.sort(Comparator.comparing(AlerteSureffectif::date)
                .thenComparing(a -> ordreCreneau.getOrDefault(a.creneauId(), 0)));
        return alertes;
    }

    /**
     * Créneaux d'un infirmier dans une semaine déjà construite : prévu, remplaçant ou absent, dans l'ordre
     * chronologique puis selon l'ordre des créneaux de la journée. Les jours fermés n'apparaissent pas.
     */
    public static List<CreneauPersonnel> creneauxDe(SemainePresence semaine, UUID infirmierId) {
        Map<UUID, Integer> ordreCreneau = new HashMap<>();
        for (int i = 0; i < semaine.creneaux().size(); i++) ordreCreneau.put(semaine.creneaux().get(i).id(), i);
        List<CreneauPersonnel> creneaux = new ArrayList<>();
        for (CasePresence c : semaine.cases()) {
            for (Present p : c.presents()) {
                if (p.infirmierId().equals(infirmierId)) {
                    creneaux.add(new CreneauPersonnel(c.date(), c.jour(), c.salleId(), c.creneauId(),
                            p.remplacant() ? SituationPersonnelle.REMPLACANT : SituationPersonnelle.PREVU));
                }
            }
            for (Absent a : c.absents()) {
                if (a.infirmierId().equals(infirmierId)) {
                    creneaux.add(new CreneauPersonnel(c.date(), c.jour(), c.salleId(), c.creneauId(),
                            SituationPersonnelle.ABSENT));
                }
            }
        }
        creneaux.sort(Comparator.comparing(CreneauPersonnel::date)
                .thenComparing(c -> ordreCreneau.getOrDefault(c.creneauId(), 0)));
        return creneaux;
    }

    /**
     * Nombre d'infirmiers requis pour un nombre de patients (arrondi à l'entier supérieur, 0 sans patient).
     */
    public static int requis(int patients, int patientsParInfirmier) {
        return patients <= 0 ? 0 : (patients + patientsParInfirmier - 1) / patientsParInfirmier;
    }

    private static CasePresence calculerCase(
            DonneesPresence donnees, Map<UUID, InfirmierRef> infirmiers, CalendrierCentre calendrier,
            Set<UUID> isolement, Map<String, Integer> patients, UUID salleId, UUID creneauId, JourSemaine jour,
            LocalDate date) {
        boolean salleIso = isolement.contains(salleId);
        int nbPatients = patients.getOrDefault(cle(salleId, creneauId, jour), 0);
        if (calendrier.ferme(date)) {
            return new CasePresence(salleId, creneauId, jour, date, nbPatients, 0, salleIso, StatutCase.FERME, 0, 0,
                    List.of(), List.of());
        }

        Set<UUID> dejaListes = new LinkedHashSet<>();
        List<Present> presents = new ArrayList<>();
        List<Absent> absents = new ArrayList<>();
        for (AffectationInfirmier a : donnees.affectations()) {
            InfirmierRef ref = infirmiers.get(a.infirmierId());
            if (ref == null || !a.salleId().equals(salleId) || !a.creneauId().equals(creneauId)
                    || !a.jours().contains(jour) || !dejaListes.add(ref.id())) {
                continue;
            }
            Optional<AbsenceInfirmier> absence = absenceLe(donnees, ref.id(), date);
            if (absence.isPresent()) {
                absents.add(new Absent(ref.id(), ref.nom(), absence.get().type()));
            } else {
                presents.add(new Present(ref.id(), ref.nom(), ref.habiliteIsolement(), false, null));
            }
        }
        for (RemplacementInfirmier r : donnees.remplacements()) {
            InfirmierRef ref = infirmiers.get(r.infirmierId());
            if (ref == null || !r.date().equals(date) || !r.salleId().equals(salleId)
                    || !r.creneauId().equals(creneauId) || absenceLe(donnees, ref.id(), date).isPresent()
                    || !dejaListes.add(ref.id())) {
                continue;
            }
            presents.add(new Present(ref.id(), ref.nom(), ref.habiliteIsolement(), true, r.id()));
        }
        presents.sort(Comparator.comparing(Present::nom));
        absents.sort(Comparator.comparing(Absent::nom));

        int requis = requis(nbPatients, donnees.patientsParInfirmier());
        int comptes = (int) presents.stream().filter(p -> !salleIso || p.habiliteIsolement()).count();
        int manque = Math.max(0, requis - comptes);
        int surplus = Math.max(0, comptes - requis);
        StatutCase statut = nbPatients == 0 ? StatutCase.SANS_PATIENT
                : manque > 0 ? StatutCase.SOUS_EFFECTIF : StatutCase.COUVERT;
        return new CasePresence(salleId, creneauId, jour, date, nbPatients, requis, salleIso, statut, manque, surplus,
                presents, absents);
    }

    static Optional<AbsenceInfirmier> absenceLe(DonneesPresence donnees, UUID infirmierId, LocalDate date) {
        return donnees.absences().stream()
                .filter(a -> a.infirmierId().equals(infirmierId) && a.couvre(date)).findFirst();
    }

    /**
     * Patients par case de la semaine commençant à {@code debut} : un patient sorti (transfert, décès, greffe,
     * guérison) ne compte plus à partir de la date de libération de sa place.
     */
    private static Map<String, Integer> patientsParCase(List<Occupation> occupations, LocalDate debut) {
        Map<String, Integer> parCase = new HashMap<>();
        for (Occupation o : occupations) {
            for (JourSemaine jour : o.jours()) {
                if (!o.occupeLe(debut.plusDays(jour.ordinal()))) continue;
                parCase.merge(cle(o.salleId(), o.creneauId(), jour), 1, Integer::sum);
            }
        }
        return parCase;
    }

    private static String cle(UUID salleId, UUID creneauId, JourSemaine jour) {
        return salleId + "|" + creneauId + "|" + jour;
    }
}
