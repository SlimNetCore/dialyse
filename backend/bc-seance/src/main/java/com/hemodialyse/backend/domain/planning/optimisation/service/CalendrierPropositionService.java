package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.infirmier.model.Presence.Absent;
import com.hemodialyse.backend.domain.infirmier.model.Presence.CasePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.Present;
import com.hemodialyse.backend.domain.infirmier.model.Presence.SemainePresence;
import com.hemodialyse.backend.domain.infirmier.service.PresenceInfirmierService;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.JourPlanning;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.CaseCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.InfirmierCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.JourCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.PatientCalendrier;
import com.hemodialyse.backend.domain.planning.optimisation.model.CalendrierProposition.SituationInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de domaine pur : construit le planning calendaire d'une proposition à partir de l'état du centre lu au
 * lancement et du résultat du calcul. Les patients sont placés comme le propose l'optimisation (places déplacées,
 * jours choisis, patients non placés retirés, déplacements temporaires datés) ; les infirmiers sont ceux de la
 * proposition lorsqu'elle planifie le personnel, sinon ceux du roulement actuel (avec absences et remplaçants).
 * Seules les salles et créneaux qui accueillent quelqu'un dans la semaine sont conservés.
 */
public final class CalendrierPropositionService {

    private CalendrierPropositionService() {
    }

    public static List<CaseCalendrier> construire(DonneesOptimisation donnees, ResultatOptimisation resultat,
                                                  ParametresOptimisation parametres) {
        Map<UUID, Poste> placements = new LinkedHashMap<>(donnees.placementsActuels());
        Map<UUID, Set<JourSemaine>> joursChoisis = new HashMap<>();
        Map<UUID, Poste> deplaces = new HashMap<>();
        for (DeplacementPatient d : resultat.deplacements()) {
            if (d.vers() != null) {
                placements.put(d.patientId(), d.vers());
                deplaces.put(d.patientId(), d.de());
            }
            if (d.jours() != null) joursChoisis.put(d.patientId(), Set.copyOf(d.jours()));
        }
        resultat.nonPlaces().forEach(p -> placements.remove(p.patientId()));
        DonneesOptimisation finales = donnees.avecPlacements(placements, joursChoisis);

        Map<UUID, String> noms = new HashMap<>();
        for (PatientAPlacer p : finales.patients()) noms.put(p.patientId(), p.nom());
        Map<UUID, String> generateurs = new HashMap<>();
        for (GenerateurRef g : finales.planning().generateurs()) generateurs.put(g.id(), g.code());

        List<CaseCalendrier> cases = new ArrayList<>();
        for (int semaine = 0; semaine < parametres.nbSemaines(); semaine++) {
            LocalDate debut = parametres.debutSemaine().plusWeeks(semaine);
            SemainePresence presence = PresenceInfirmierService.construire(finales.presence(), debut);
            cases.addAll(semaine(finales, resultat, parametres, presence, debut, noms, generateurs, deplaces));
        }
        return cases;
    }

    private static List<CaseCalendrier> semaine(DonneesOptimisation finales, ResultatOptimisation resultat,
                                                ParametresOptimisation parametres, SemainePresence presence,
                                                LocalDate debut, Map<UUID, String> noms,
                                                Map<UUID, String> generateurs, Map<UUID, Poste> deplaces) {
        Map<String, CasePresence> presences = new HashMap<>();
        for (CasePresence c : presence.cases()) presences.put(cle(c.salleId(), c.creneauId(), c.date()), c);
        Map<String, List<PatientCalendrier>> patients = patients(finales, resultat, presence, debut, noms, generateurs,
                deplaces);
        boolean infirmiersProposes = parametres.perimetre().planifieInfirmiers() && !resultat.vacations().isEmpty();
        Map<String, List<VacationPlanifiee>> vacations = new HashMap<>();
        for (VacationPlanifiee v : resultat.vacations()) {
            vacations.computeIfAbsent(cle(v.salleId(), v.creneauId(), v.date()), k -> new ArrayList<>()).add(v);
        }

        List<CaseCalendrier> lignes = new ArrayList<>();
        int salleOrdre = 0;
        for (SalleRef salle : finales.planning().salles()) {
            salleOrdre++;
            for (CreneauRef creneau : finales.planning().creneaux().stream()
                    .sorted(Comparator.comparingInt(CreneauRef::ordre)).toList()) {
                List<JourCalendrier> jours = new ArrayList<>();
                for (JourSemaine jour : JourSemaine.values()) {
                    LocalDate date = debut.plusDays(jour.ordinal());
                    String cle = cle(salle.id(), creneau.id(), date);
                    List<VacationPlanifiee> proposees = infirmiersProposes ? vacations.get(cle) : null;
                    jours.add(jour(jour, date, presences.get(cle), presence.jours(),
                            patients.getOrDefault(cle, List.of()), infirmiers(presences.get(cle), proposees),
                            proposees != null && !proposees.isEmpty()));
                }
                if (jours.stream().anyMatch(j -> !j.vide())) {
                    lignes.add(new CaseCalendrier(debut, salle.id(), salle.nom(), salleOrdre, creneau.id(),
                            creneau.libelle(), creneau.ordre(), jours));
                }
            }
        }
        return lignes;
    }

    private static JourCalendrier jour(JourSemaine jour, LocalDate date, CasePresence presence,
                                       List<JourPlanning> jours, List<PatientCalendrier> patients,
                                       List<InfirmierCalendrier> infirmiers, boolean infirmiersProposes) {
        JourPlanning jp = jours.stream().filter(j -> j.date().equals(date)).findFirst().orElse(null);
        boolean ferme = jp != null && jp.ferme();
        if (ferme) return new JourCalendrier(jour, date, true, jp.fermetureMotif(), 0, 0, 0, List.of(), List.of());
        int requis = presence == null ? 0 : presence.requis();
        long tenus = infirmiers.stream().filter(i -> i.situation() != SituationInfirmier.ABSENT).count();
        int manque = (int) Math.max(0, requis - tenus);
        // roulement actuel : le calcul de présence tient compte de l'habilitation en salle d'isolement ; infirmiers de la
        // proposition : leur effectif est comparé tel quel à l'effectif requis par les patients de la proposition
        int surplus = infirmiersProposes ? (int) Math.max(0, tenus - requis) : presence == null ? 0 : presence.surplus();
        return new JourCalendrier(jour, date, false, null, requis, manque, surplus, patients, infirmiers);
    }

    /**
     * Patients de chaque case et de chaque date : placements finaux, puis déplacements temporaires datés (le patient
     * quitte sa place habituelle ce jour-là et occupe la place temporaire).
     */
    private static Map<String, List<PatientCalendrier>> patients(DonneesOptimisation finales,
                                                                 ResultatOptimisation resultat,
                                                                 SemainePresence presence, LocalDate debut,
                                                                 Map<UUID, String> noms,
                                                                 Map<UUID, String> generateurs,
                                                                 Map<UUID, Poste> deplaces) {
        Map<UUID, String> salles = new HashMap<>();
        finales.planning().salles().forEach(s -> salles.put(s.id(), s.nom()));
        Map<UUID, String> creneaux = new HashMap<>();
        finales.planning().creneaux().forEach(c -> creneaux.put(c.id(), c.libelle()));
        Map<String, List<PatientCalendrier>> parCase = new HashMap<>();
        for (JourPlanning jp : presence.jours()) {
            for (Occupation o : finales.planning().occupations()) {
                if (!occupe(o, jp)) continue;
                parCase.computeIfAbsent(cle(o.salleId(), o.creneauId(), jp.date()), k -> new ArrayList<>())
                        .add(new PatientCalendrier(o.patientId(), noms.getOrDefault(o.patientId(), "?"),
                                generateurs.get(o.generateurId()), o.aRisque(), deplaces.containsKey(o.patientId()),
                                false, lieu(deplaces.get(o.patientId()), salles, creneaux)));
            }
        }
        LocalDate fin = debut.plusDays(6);
        for (DeplacementTemporairePropose t : resultat.temporaires()) {
            if (t.date().isBefore(debut) || t.date().isAfter(fin)) continue;
            if (t.de() != null) {
                List<PatientCalendrier> ici = parCase.get(cle(t.de().salleId(), t.de().creneauId(), t.date()));
                if (ici != null) ici.removeIf(p -> p.patientId().equals(t.patientId()));
            }
            if (t.vers() != null) {
                boolean risque = finales.patients().stream()
                        .anyMatch(p -> p.patientId().equals(t.patientId()) && p.aRisque());
                parCase.computeIfAbsent(cle(t.vers().salleId(), t.vers().creneauId(), t.date()),
                                k -> new ArrayList<>())
                        .add(new PatientCalendrier(t.patientId(), noms.getOrDefault(t.patientId(), t.nom()),
                                t.vers().generateurCode(), risque, false, true, lieu(t.de(), salles, creneaux)));
            }
        }
        parCase.values().forEach(l -> l.sort(Comparator.comparing(PatientCalendrier::nom)));
        return parCase;
    }

    /**
     * « Salle · créneau · générateur » d'une place ; null sans place (patient qui n'en avait pas).
     */
    private static String lieu(Poste poste, Map<UUID, String> salles, Map<UUID, String> creneaux) {
        if (poste == null || poste.salleId() == null) return null;
        return String.join(" · ", salles.getOrDefault(poste.salleId(), "?"),
                creneaux.getOrDefault(poste.creneauId(), "?"),
                poste.generateurCode() == null ? "—" : poste.generateurCode());
    }

    private static boolean occupe(Occupation o, JourPlanning jp) {
        if (o.jours() == null || !o.jours().contains(jp.jour())) return false;
        if (o.premierJour() != null && jp.date().isBefore(o.premierJour())) return false;
        return o.dernierJour() == null || !jp.date().isAfter(o.dernierJour());
    }

    /**
     * Infirmiers d'une case : ceux de la proposition (quand elle planifie le personnel et couvre cette case), sinon
     * ceux du roulement actuel ; les absents sont toujours signalés.
     */
    private static List<InfirmierCalendrier> infirmiers(CasePresence presence, List<VacationPlanifiee> proposees) {
        List<InfirmierCalendrier> liste = new ArrayList<>();
        if (proposees != null && !proposees.isEmpty()) {
            proposees.stream().sorted(Comparator.comparing(VacationPlanifiee::nom)).forEach(v -> liste.add(
                    new InfirmierCalendrier(v.nom(), v.existante() ? SituationInfirmier.PREVU : SituationInfirmier.NOUVEAU)));
        } else if (presence != null) {
            for (Present p : presence.presents()) {
                liste.add(new InfirmierCalendrier(p.nom(),
                        p.remplacant() ? SituationInfirmier.REMPLACANT : SituationInfirmier.PREVU));
            }
        }
        if (presence != null) {
            for (Absent a : presence.absents()) liste.add(new InfirmierCalendrier(a.nom(), SituationInfirmier.ABSENT));
        }
        return liste;
    }

    private static String cle(UUID salleId, UUID creneauId, LocalDate date) {
        return salleId + "|" + creneauId + "|" + date;
    }
}
