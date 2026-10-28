package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Absent;
import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.JourDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Present;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Planning calendaire d'une proposition (P24, RG-PLN-101) : service de domaine pur qui le construit à partir de l'état du centre lu au
 * lancement et du résultat du calcul, FIGÉ à la fin du calcul. Les patients sont placés comme le propose l'optimisation (places
 * déplacées, jours choisis, patients non placés retirés, déplacements temporaires datés) ; les infirmiers sont ceux de la proposition
 * lorsqu'elle planifie le personnel, sinon ceux du roulement actuel (absences et remplaçants compris, par la règle de présence de la
 * lecture P4). Seuls les salles et créneaux qui accueillent quelqu'un dans la semaine sont conservés. Modèle de LECTURE : ses champs
 * sont ceux du JSON historique.
 */
public final class CalendrierDeLaProposition {

    private CalendrierDeLaProposition() {
    }

    public enum SituationInfirmier {
        PREVU,
        REMPLACANT,
        ABSENT,
        NOUVEAU
    }

    public record PatientDuCalendrier(UUID patient, String nom, String generateurCode, boolean aRisque, boolean deplace,
                                      boolean temporaire, String avant) {
    }

    public record InfirmierDuCalendrier(String nom, SituationInfirmier situation) {
    }

    public record JourDuCalendrier(JourDeLaSemaine jour, LocalDate date, boolean ferme, String motifFermeture, int requis, int manque,
                                   int surplus, List<PatientDuCalendrier> patients, List<InfirmierDuCalendrier> infirmiers) {
        public JourDuCalendrier {
            patients = patients == null ? List.of() : List.copyOf(patients);
            infirmiers = infirmiers == null ? List.of() : List.copyOf(infirmiers);
        }

        public boolean vide() {
            return patients.isEmpty() && infirmiers.isEmpty();
        }
    }

    public record CaseDuCalendrier(LocalDate semaineDebut, UUID salle, String salleNom, int salleOrdre, UUID creneau,
                                   String creneauLibelle, int creneauOrdre, List<JourDuCalendrier> jours) {
        public CaseDuCalendrier {
            jours = jours == null ? List.of() : List.copyOf(jours);
        }
    }

    public static List<CaseDuCalendrier> construire(DonneesDOptimisation donnees, ResultatDOptimisation resultat,
                                                    ParametresDOptimisation parametres) {
        Map<PatientId, Poste> placements = new LinkedHashMap<>(donnees.placementsActuels());
        Map<PatientId, Set<JourDeLaSemaine>> joursChoisis = new HashMap<>();
        Map<PatientId, Poste> deplaces = new HashMap<>();
        for (DeplacementDePatient d : resultat.deplacements()) {
            if (d.vers() != null) {
                placements.put(d.patient(), d.vers());
                deplaces.put(d.patient(), d.de());
            }
            if (d.jours() != null) joursChoisis.put(d.patient(), Set.copyOf(d.jours()));
        }
        resultat.nonPlaces().forEach(p -> placements.remove(p.patient()));
        DonneesDOptimisation finales = donnees.avecPlacements(placements, joursChoisis);
        Map<PatientId, String> noms = new HashMap<>();
        for (PatientAPlacer p : finales.patients()) noms.put(p.patient(), p.nom());
        Map<UUID, String> generateurs = new HashMap<>();
        finales.ressources().generateurs().forEach(g -> generateurs.put(g.id().valeur(), g.code()));
        List<CaseDuCalendrier> cases = new ArrayList<>();
        for (int semaine = 0; semaine < parametres.nbSemaines(); semaine++) {
            LocalDate debut = parametres.debutSemaine().plusWeeks(semaine);
            SemainePresence presence = PresenceDeLaSemaine.construire(finales.presence(), debut);
            cases.addAll(semaine(finales, resultat, parametres, presence, debut, semaine, noms, generateurs, deplaces));
        }
        return cases;
    }

    private static List<CaseDuCalendrier> semaine(DonneesDOptimisation finales, ResultatDOptimisation resultat,
                                                  ParametresDOptimisation parametres, SemainePresence presence, LocalDate debut,
                                                  int indexSemaine, Map<PatientId, String> noms, Map<UUID, String> generateurs,
                                                  Map<PatientId, Poste> deplaces) {
        Map<String, CasePresence> presences = new HashMap<>();
        for (CasePresence c : presence.cases()) presences.put(cle(c.salleId(), c.creneauId(), c.date()), c);
        Map<LocalDate, Set<UUID>> absentsParDate = new HashMap<>();
        for (CasePresence c : presence.cases()) {
            for (Absent a : c.absents()) absentsParDate.computeIfAbsent(c.date(), k -> new HashSet<>()).add(a.infirmierId());
        }
        Map<String, List<PatientDuCalendrier>> patients = patients(finales, resultat, presence, debut, noms, generateurs, deplaces);
        boolean infirmiersProposes = parametres.perimetre().planifieLesInfirmiers() && !resultat.vacations().isEmpty();
        Map<String, List<VacationPlanifiee>> vacations = new HashMap<>();
        for (VacationPlanifiee v : resultat.vacations()) {
            vacations.computeIfAbsent(cle(v.salle().valeur(), v.creneau().valeur(), v.date()), k -> new ArrayList<>()).add(v);
        }
        List<CaseDuCalendrier> lignes = new ArrayList<>();
        int salleOrdre = 0;
        for (RessourcesDuCentre.Salle salle : finales.ressources().salles()) {
            salleOrdre++;
            for (RessourcesDuCentre.Creneau creneau : finales.ressources().creneaux().stream()
                    .sorted(Comparator.comparingInt(RessourcesDuCentre.Creneau::ordre)).toList()) {
                List<JourDuCalendrier> jours = new ArrayList<>();
                for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                    LocalDate date = debut.plusDays(jour.ordinal());
                    String cle = cle(salle.id().valeur(), creneau.id().valeur(), date);
                    // périmètre non daté : les vacations proposées sont celles de la semaine type (première semaine), reconduites
                    // à l'identique sur chaque semaine de l'horizon
                    String cleVacation = parametres.perimetre().datee() ? cle
                            : cle(salle.id().valeur(), creneau.id().valeur(), date.minusWeeks(indexSemaine));
                    List<VacationPlanifiee> proposees = infirmiersProposes ? vacations.get(cleVacation) : null;
                    jours.add(jour(jour, date, presences.get(cle), presence.jours(), patients.getOrDefault(cle, List.of()),
                            infirmiers(presences.get(cle), proposees, absentsParDate.getOrDefault(date, Set.of())),
                            proposees != null && !proposees.isEmpty()));
                }
                if (jours.stream().anyMatch(j -> !j.vide())) {
                    lignes.add(new CaseDuCalendrier(debut, salle.id().valeur(), salle.nom(), salleOrdre, creneau.id().valeur(),
                            creneau.libelle(), creneau.ordre(), jours));
                }
            }
        }
        return lignes;
    }

    private static JourDuCalendrier jour(JourDeLaSemaine jour, LocalDate date, CasePresence presence, List<JourDuPlanning> jours,
                                         List<PatientDuCalendrier> patients, List<InfirmierDuCalendrier> infirmiers,
                                         boolean infirmiersProposes) {
        JourDuPlanning jp = jours.stream().filter(j -> j.date().equals(date)).findFirst().orElse(null);
        boolean ferme = jp != null && (!jp.ouvertHebdomadaire() || jp.fermetureMotif() != null);
        if (ferme) return new JourDuCalendrier(jour, date, true, jp.fermetureMotif(), 0, 0, 0, List.of(), List.of());
        int requis = presence == null ? 0 : presence.requis();
        long tenus = infirmiers.stream().filter(i -> i.situation() != SituationInfirmier.ABSENT).count();
        int manque = (int) Math.max(0, requis - tenus);
        // roulement actuel : la présence tient compte de l'habilitation en salle d'isolement ; infirmiers de la proposition : leur
        // effectif est comparé tel quel à l'effectif requis par les patients de la proposition
        int surplus = infirmiersProposes ? (int) Math.max(0, tenus - requis) : presence == null ? 0 : presence.surplus();
        return new JourDuCalendrier(jour, date, false, null, requis, manque, surplus, patients, infirmiers);
    }

    /**
     * Patients de chaque case et de chaque date : placements finaux, puis déplacements temporaires datés (le patient quitte sa place
     * habituelle ce jour-là et occupe la place temporaire).
     */
    private static Map<String, List<PatientDuCalendrier>> patients(DonneesDOptimisation finales, ResultatDOptimisation resultat,
                                                                   SemainePresence presence, LocalDate debut,
                                                                   Map<PatientId, String> noms, Map<UUID, String> generateurs,
                                                                   Map<PatientId, Poste> deplaces) {
        Map<UUID, String> salles = new HashMap<>();
        finales.ressources().salles().forEach(s -> salles.put(s.id().valeur(), s.nom()));
        Map<UUID, String> creneaux = new HashMap<>();
        finales.ressources().creneaux().forEach(c -> creneaux.put(c.id().valeur(), c.libelle()));
        Map<String, List<PatientDuCalendrier>> parCase = new HashMap<>();
        for (JourDuPlanning jp : presence.jours()) {
            for (PatientAPlacer p : finales.patients()) {
                Poste poste = p.actuelle();
                if (poste == null || poste.salle() == null || poste.creneau() == null || !occupe(p, jp)) continue;
                parCase.computeIfAbsent(cle(poste.salle().valeur(), poste.creneau().valeur(), jp.date()), k -> new ArrayList<>())
                        .add(new PatientDuCalendrier(p.patient().valeur(), noms.getOrDefault(p.patient(), "?"),
                                poste.generateur() == null ? null : generateurs.get(poste.generateur().valeur()), p.aRisque(),
                                deplaces.containsKey(p.patient()), false, lieu(deplaces.get(p.patient()), salles, creneaux)));
            }
        }
        LocalDate fin = debut.plusDays(6);
        for (DeplacementTemporairePropose t : resultat.temporaires()) {
            if (t.date().isBefore(debut) || t.date().isAfter(fin)) continue;
            if (t.de() != null && t.de().salle() != null && t.de().creneau() != null) {
                List<PatientDuCalendrier> ici = parCase.get(cle(t.de().salle().valeur(), t.de().creneau().valeur(), t.date()));
                if (ici != null) ici.removeIf(p -> p.patient().equals(t.patient().valeur()));
            }
            if (t.vers() != null) {
                boolean risque = finales.patients().stream().anyMatch(p -> p.patient().equals(t.patient()) && p.aRisque());
                parCase.computeIfAbsent(cle(t.vers().salle().valeur(), t.vers().creneau().valeur(), t.date()), k -> new ArrayList<>())
                        .add(new PatientDuCalendrier(t.patient().valeur(), noms.getOrDefault(t.patient(), t.nom()),
                                t.vers().generateurCode(), risque, false, true, lieu(t.de(), salles, creneaux)));
            }
        }
        parCase.values().forEach(l -> l.sort(Comparator.comparing(PatientDuCalendrier::nom)));
        return parCase;
    }

    /** « Salle · créneau · générateur » d'une place ; nul sans place (patient qui n'en avait pas). */
    private static String lieu(Poste poste, Map<UUID, String> salles, Map<UUID, String> creneaux) {
        if (poste == null || poste.salle() == null) return null;
        return String.join(" · ", salles.getOrDefault(poste.salle().valeur(), "?"),
                poste.creneau() == null ? "?" : creneaux.getOrDefault(poste.creneau().valeur(), "?"),
                poste.generateurCode() == null ? "—" : poste.generateurCode());
    }

    private static boolean occupe(PatientAPlacer p, JourDuPlanning jp) {
        if (!p.jours().contains(jp.jour())) return false;
        if (p.premierJour() != null && jp.date().isBefore(p.premierJour())) return false;
        return p.dernierJour() == null || !jp.date().isAfter(p.dernierJour());
    }

    /** Infirmiers d'une case : ceux de la proposition (si elle planifie le personnel et couvre cette case), sinon ceux du roulement. */
    private static List<InfirmierDuCalendrier> infirmiers(CasePresence presence, List<VacationPlanifiee> proposees,
                                                          Set<UUID> absentsDuJour) {
        List<InfirmierDuCalendrier> liste = new ArrayList<>();
        if (proposees != null && !proposees.isEmpty()) {
            // la semaine type proposée ignore les absences datées : un infirmier absent ce jour-là n'est pas présent (il n'est listé
            // qu'en absent), sa vacation reste à pourvoir
            Set<UUID> absents = new HashSet<>(absentsDuJour);
            if (presence != null) presence.absents().forEach(a -> absents.add(a.infirmierId()));
            proposees.stream().filter(v -> !absents.contains(v.infirmier().valeur()))
                    .sorted(Comparator.comparing(VacationPlanifiee::nom)).forEach(v -> liste.add(
                            new InfirmierDuCalendrier(v.nom(), v.existante() ? SituationInfirmier.PREVU : SituationInfirmier.NOUVEAU)));
        } else if (presence != null) {
            for (Present p : presence.presents()) {
                liste.add(new InfirmierDuCalendrier(p.nom(), p.remplacant() ? SituationInfirmier.REMPLACANT : SituationInfirmier.PREVU));
            }
        }
        if (presence != null) {
            for (Absent a : presence.absents()) liste.add(new InfirmierDuCalendrier(a.nom(), SituationInfirmier.ABSENT));
        }
        return liste;
    }

    private static String cle(UUID salle, UUID creneau, LocalDate date) {
        return salle + "|" + creneau + "|" + date;
    }
}
