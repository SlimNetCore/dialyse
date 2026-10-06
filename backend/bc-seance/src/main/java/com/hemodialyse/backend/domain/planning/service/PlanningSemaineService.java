package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.CellulePlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.Conflit;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.DonneesSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.JourPlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.OccupantPlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SeanceRealisee;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SeanceSansCase;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.SemainePlanning;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.TypeConflit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de domaine pur : construit le planning réel d'une semaine à partir des placements des patients actifs et des
 * séances déjà réalisées (visibles là où elles ont eu lieu même si le planning du patient a changé depuis),
 * signale les jours fermés (fermeture hebdomadaire, férié, fermeture exceptionnelle) et détecte les conflits :
 * générateur réservé deux fois, salle surchargée, patient sur un générateur hors service ou sans générateur, règles
 * d'isolement non respectées et générateur mélangeant patients à risque et patients sans risque.
 */
public final class PlanningSemaineService {

    private PlanningSemaineService() {
    }

    /**
     * Dimanche de la semaine contenant la date (la semaine de dialyse commence le dimanche).
     */
    public static LocalDate debutSemaine(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    public static SemainePlanning construire(DonneesSemaine donnees, LocalDate debut) {
        DonneesPlanning planning = donnees.planning();
        Set<JourSemaine> ouverts = planning.joursOuverts() == null || planning.joursOuverts().isEmpty()
                ? EnumSet.allOf(JourSemaine.class) : EnumSet.copyOf(planning.joursOuverts());
        Set<UUID> isolement = planning.sallesIsolement() == null ? Set.of() : planning.sallesIsolement();
        Map<UUID, GenerateurRef> generateurs = new HashMap<>();
        planning.generateurs().forEach(g -> generateurs.put(g.id(), g));
        Map<UUID, Integer> capacites = new HashMap<>();
        planning.generateurs().forEach(g -> capacites.merge(g.salleId(), 1, Integer::sum));
        Set<UUID> sallesConnues = new HashSet<>();
        planning.salles().forEach(s -> sallesConnues.add(s.id()));
        Set<UUID> creneauxConnus = new HashSet<>();
        planning.creneaux().forEach(c -> creneauxConnus.add(c.id()));

        List<JourPlanning> jours = new ArrayList<>();
        for (JourSemaine jour : JourSemaine.values()) {
            LocalDate date = debut.plusDays(jour.ordinal());
            Fermeture fermeture = donnees.fermetures().stream().filter(f -> f.date().equals(date)).findFirst().orElse(null);
            // motif vide (jamais null) pour une fermeture datée sans libellé : le jour reste signalé comme fermé
            String motif = fermeture == null ? null : (fermeture.motif() == null ? "" : fermeture.motif());
            jours.add(new JourPlanning(jour, date, ouverts.contains(jour), motif));
        }

        // Déplacements temporaires de la semaine : le patient quitte sa case habituelle pour cette seule date
        Map<String, DeplacementTemporaire> temporaires = new HashMap<>();
        for (DeplacementTemporaire t : donnees.temporaires()) temporaires.put(t.patientId() + "|" + t.date(), t);

        // Cellules : occupants par (salle, créneau, jour)
        Map<String, List<OccupantPlanning>> occupants = new LinkedHashMap<>();
        Map<String, List<Occupation>> occupationsParCellule = new LinkedHashMap<>();
        for (Occupation o : planning.occupations()) {
            if (!sallesConnues.contains(o.salleId()) || !creneauxConnus.contains(o.creneauId())) continue;
            GenerateurRef g = o.generateurId() == null ? null : generateurs.get(o.generateurId());
            OccupantPlanning occupant = new OccupantPlanning(
                    o.patientId(), donnees.nomsPatients().getOrDefault(o.patientId(), ""),
                    g == null ? null : g.code(), o.aRisque(),
                    o.dernierJour() == null ? null : o.dernierJour().plusDays(1));
            for (JourSemaine jour : o.jours()) {
                // place déjà libérée ce jour-là (patient sorti à une date passée dans la semaine)
                if (!o.occupeLe(debut.plusDays(jour.ordinal()))) continue;
                DeplacementTemporaire t = temporaires.get(o.patientId() + "|" + debut.plusDays(jour.ordinal()));
                if (t != null && sallesConnues.contains(t.salleId()) && creneauxConnus.contains(t.creneauId())) {
                    GenerateurRef cible = generateurs.get(t.generateurId());
                    String cle = key(t.salleId(), t.creneauId(), jour);
                    occupants.computeIfAbsent(cle, k -> new ArrayList<>()).add(new OccupantPlanning(o.patientId(),
                            occupant.nom(), cible == null ? null : cible.code(), o.aRisque(), occupant.libereLe(), true));
                    occupationsParCellule.computeIfAbsent(cle, k -> new ArrayList<>()).add(new Occupation(o.patientId(),
                            t.salleId(), t.creneauId(), t.generateurId(), EnumSet.of(jour), o.aRisque()));
                    continue;
                }
                String key = key(o.salleId(), o.creneauId(), jour);
                occupants.computeIfAbsent(key, k -> new ArrayList<>()).add(occupant);
                occupationsParCellule.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
            }
        }

        List<SeanceSansCase> sansCase = placerSeancesRealisees(donnees, debut, occupants, sallesConnues, creneauxConnus,
                generateurs);

        List<CellulePlanning> cellules = new ArrayList<>();
        List<Conflit> conflits = new ArrayList<>();
        for (SalleRef salle : planning.salles()) {
            int capacite = capacites.getOrDefault(salle.id(), 0);
            for (CreneauRef creneau : planning.creneaux()) {
                for (JourSemaine jour : JourSemaine.values()) {
                    String key = key(salle.id(), creneau.id(), jour);
                    List<OccupantPlanning> liste = new ArrayList<>(occupants.getOrDefault(key, List.of()));
                    liste.sort(Comparator.comparing(OccupantPlanning::nom));
                    cellules.add(new CellulePlanning(salle.id(), creneau.id(), jour, ouverts.contains(jour) ? capacite : 0, liste));
                    detecterConflitsDeCase(conflits, salle, creneau, jour, capacite,
                            occupationsParCellule.getOrDefault(key, List.of()), generateurs, donnees.nomsPatients(), isolement);
                }
            }
        }
        detecterGenerateursMixtes(conflits, planning.occupations(), generateurs, donnees.nomsPatients());

        Set<UUID> aReplanifier = new HashSet<>();
        for (CellulePlanning c : cellules) {
            JourPlanning jour = jours.get(c.jour().ordinal());
            if (!jour.ferme()) continue;
            c.occupants().stream().filter(o -> !o.realiseeHorsPlanning()).forEach(o -> aReplanifier.add(o.patientId()));
        }
        return new SemainePlanning(debut, debut.plusDays(6), jours, planning.salles(), planning.creneaux(), cellules,
                conflits, aReplanifier.size(), sansCase);
    }

    /**
     * Séances réalisées de la semaine que le planning actuel ne montre plus (jours ou place du patient modifiés depuis) :
     * elles sont ajoutées à la case où elles ont eu lieu (place figée à la validation, sinon place actuelle du patient)
     * sans compter dans les conflits — elles sont passées. Les séances encore prévues ensuite dans la semaine sont
     * marquées « déjà réalisée le … » : le patient risque une séance en trop.
     *
     * @return séances réalisées qu'aucune case ne peut accueillir
     */
    private static List<SeanceSansCase> placerSeancesRealisees(DonneesSemaine donnees, LocalDate debut,
                                                               Map<String, List<OccupantPlanning>> occupants,
                                                               Set<UUID> sallesConnues, Set<UUID> creneauxConnus,
                                                               Map<UUID, GenerateurRef> generateurs) {
        Set<String> presents = new HashSet<>();
        occupants.forEach((cle, liste) -> {
            String jour = cle.substring(cle.lastIndexOf('|') + 1);
            liste.forEach(o -> presents.add(o.patientId() + "|" + jour));
        });
        Map<UUID, Occupation> placeActuelle = new HashMap<>();
        donnees.planning().occupations().forEach(o -> placeActuelle.putIfAbsent(o.patientId(), o));

        List<SeanceSansCase> sansCase = new ArrayList<>();
        Map<UUID, LocalDate> horsPlanning = new HashMap<>();
        LocalDate fin = debut.plusDays(6);
        for (SeanceRealisee s : donnees.realisees()) {
            if (s.date().isBefore(debut) || s.date().isAfter(fin)) continue;
            JourSemaine jour = JourSemaine.de(s.date().getDayOfWeek());
            if (presents.contains(s.patientId() + "|" + jour)) continue;
            UUID salle = s.salleId();
            UUID creneau = s.creneauId();
            UUID generateur = s.generateurId();
            boolean aRisque = s.aRisque();
            Occupation actuelle = placeActuelle.get(s.patientId());
            if (!s.placeConnue() && actuelle != null) {
                salle = actuelle.salleId();
                creneau = actuelle.creneauId();
                generateur = actuelle.generateurId();
                aRisque = actuelle.aRisque();
            }
            String nom = donnees.nomsPatients().getOrDefault(s.patientId(), "");
            horsPlanning.merge(s.patientId(), s.date(), (a, b) -> a.isBefore(b) ? a : b);
            if (salle == null || creneau == null || !sallesConnues.contains(salle) || !creneauxConnus.contains(creneau)) {
                sansCase.add(new SeanceSansCase(s.patientId(), nom, s.date(), jour));
                continue;
            }
            GenerateurRef g = generateur == null ? null : generateurs.get(generateur);
            occupants.computeIfAbsent(key(salle, creneau, jour), k -> new ArrayList<>()).add(new OccupantPlanning(
                    s.patientId(), nom, g == null ? null : g.code(), aRisque, null, false, true, null));
            presents.add(s.patientId() + "|" + jour);
        }
        if (horsPlanning.isEmpty()) return sansCase;

        Set<String> realisees = new HashSet<>();
        donnees.realisees().forEach(s -> realisees.add(s.patientId() + "|" + s.date()));
        occupants.forEach((cle, liste) -> {
            JourSemaine jour = JourSemaine.valueOf(cle.substring(cle.lastIndexOf('|') + 1));
            LocalDate date = debut.plusDays(jour.ordinal());
            liste.replaceAll(o -> {
                LocalDate deja = horsPlanning.get(o.patientId());
                boolean aVerifier = deja != null && !o.realiseeHorsPlanning() && date.isAfter(deja)
                        && !realisees.contains(o.patientId() + "|" + date);
                return aVerifier ? o.avecDejaRealiseeLe(deja) : o;
            });
        });
        sansCase.sort(Comparator.comparing(SeanceSansCase::date).thenComparing(SeanceSansCase::nom));
        return sansCase;
    }

    private static void detecterConflitsDeCase(
            List<Conflit> conflits, SalleRef salle, CreneauRef creneau, JourSemaine jour, int capacite,
            List<Occupation> occupations, Map<UUID, GenerateurRef> generateurs, Map<UUID, String> noms,
            Set<UUID> isolement) {
        if (occupations.isEmpty()) return;

        Map<UUID, List<String>> parGenerateur = new LinkedHashMap<>();
        List<String> sansGenerateur = new ArrayList<>();
        List<String> horsService = new ArrayList<>();
        List<String> isolementKo = new ArrayList<>();
        for (Occupation o : occupations) {
            String nom = noms.getOrDefault(o.patientId(), "");
            GenerateurRef g = o.generateurId() == null ? null : generateurs.get(o.generateurId());
            if (o.generateurId() == null) {
                sansGenerateur.add(nom);
            } else if (g == null || !g.salleId().equals(o.salleId())) {
                horsService.add(nom);
            } else {
                parGenerateur.computeIfAbsent(g.id(), k -> new ArrayList<>()).add(nom);
            }
            if (isolement.contains(o.salleId()) != o.aRisque() && (o.aRisque() || !isolement.isEmpty())) {
                isolementKo.add(nom);
            }
        }
        parGenerateur.values().stream().filter(l -> l.size() > 1)
                .forEach(l -> conflits.add(new Conflit(TypeConflit.GENERATEUR_DOUBLE, salle.id(), creneau.id(), jour, l)));
        if (!horsService.isEmpty()) {
            conflits.add(new Conflit(TypeConflit.GENERATEUR_INDISPONIBLE, salle.id(), creneau.id(), jour, horsService));
        }
        if (!sansGenerateur.isEmpty()) {
            conflits.add(new Conflit(TypeConflit.SANS_GENERATEUR, salle.id(), creneau.id(), jour, sansGenerateur));
        }
        if (!isolementKo.isEmpty()) {
            conflits.add(new Conflit(TypeConflit.ISOLEMENT_NON_RESPECTE, salle.id(), creneau.id(), jour, isolementKo));
        }
        if (occupations.size() > capacite) {
            conflits.add(new Conflit(TypeConflit.SALLE_SURCHARGEE, salle.id(), creneau.id(), jour,
                    occupations.stream().map(o -> noms.getOrDefault(o.patientId(), "")).sorted().toList()));
        }
    }

    /**
     * Un même générateur sert à la fois des patients à risque et des patients sans risque (quel que soit le jour).
     */
    private static void detecterGenerateursMixtes(
            List<Conflit> conflits, List<Occupation> occupations, Map<UUID, GenerateurRef> generateurs,
            Map<UUID, String> noms) {
        Map<UUID, Set<UUID>> aRisque = new HashMap<>();
        Map<UUID, Set<UUID>> sansRisque = new HashMap<>();
        for (Occupation o : occupations) {
            if (o.generateurId() == null || !generateurs.containsKey(o.generateurId())) continue;
            (o.aRisque() ? aRisque : sansRisque).computeIfAbsent(o.generateurId(), k -> new HashSet<>()).add(o.patientId());
        }
        for (UUID generateurId : aRisque.keySet()) {
            if (!sansRisque.containsKey(generateurId)) continue;
            List<String> patients = new ArrayList<>();
            patients.add(generateurs.get(generateurId).code());
            aRisque.get(generateurId).forEach(p -> patients.add(noms.getOrDefault(p, "")));
            sansRisque.get(generateurId).forEach(p -> patients.add(noms.getOrDefault(p, "")));
            conflits.add(new Conflit(TypeConflit.GENERATEUR_MIXTE, null, null, null, patients));
        }
    }

    private static String key(UUID salle, UUID creneau, JourSemaine jour) {
        return salle + "|" + creneau + "|" + jour;
    }
}
