package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;

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

/**
 * Le planning réel d'une semaine (du dimanche au samedi) : qui dialyse où, jour par jour, les jours fermés et les conflits à corriger
 * (RG-PLN-040 à 045). Recalculé à chaque affichage à partir des jours et de la place <b>actuels</b> des patients présents
 * ({@link SejourDuPatient}) ; un patient en sommeil n'y figure pas. Service de domaine pur : aucune lecture, aucune horloge.
 * <p>
 * Tolérances reproduites du legacy (KB-PLN-73, 74) : un déplacement temporaire dont la cible est inconnue du centre est ignoré, une
 * place figée à la validation d'une séance n'est pas contrôlée. Leur refus à l'écriture est l'affaire de P8.
 */
public final class SemaineDuPlanning {

    private SemaineDuPlanning() {
    }

    public enum TypeDeConflit {
        GENERATEUR_DOUBLE, SALLE_SURCHARGEE, GENERATEUR_INDISPONIBLE, SANS_GENERATEUR, ISOLEMENT_NON_RESPECTE, GENERATEUR_MIXTE
    }

    /** Un patient avec sa place actuelle et son séjour. */
    public record PatientDeLaSemaine(PatientId patient, String nom, Occupation place, SejourDuPatient sejour) {
    }

    /** Séance réalisée (validée, signée ou facturée), avec la place figée à sa validation (nulle si non mémorisée). */
    public record SeanceRealisee(PatientId patient, String nom, LocalDate date, SalleId salle, CreneauId creneau,
                                 GenerateurId generateur, boolean aRisque) {
        boolean placeConnue() {
            return salle != null && creneau != null;
        }
    }

    /** Le patient dialyse à cette seule date dans une autre case (générateur habituel en maintenance). */
    public record DeplacementTemporaire(PatientId patient, LocalDate date, SalleId salle, CreneauId creneau, GenerateurId generateur) {
    }

    public record Donnees(RessourcesDuCentre ressources, List<PatientDeLaSemaine> patients, List<SeanceRealisee> realisees,
                          List<DeplacementTemporaire> deplacements) {
        public Donnees {
            patients = patients == null ? List.of() : List.copyOf(patients);
            realisees = realisees == null ? List.of() : List.copyOf(realisees);
            deplacements = deplacements == null ? List.of() : List.copyOf(deplacements);
        }
    }

    /** @param fermeture motif de la fermeture datée (férié, fermeture exceptionnelle), vide si sans libellé, nul si ouvert à la date */
    public record Jour(JourDeLaSemaine jour, LocalDate date, boolean ouvertHebdomadaire, String fermeture) {
        public boolean ferme() {
            return !ouvertHebdomadaire || fermeture != null;
        }
    }

    public record Occupant(PatientId patient, String nom, String generateur, boolean aRisque, LocalDate libereLe, boolean temporaire,
                           boolean realiseeHorsPlanning, LocalDate dejaRealiseeLe) {
        Occupant avecDejaRealiseeLe(LocalDate date) {
            return new Occupant(patient, nom, generateur, aRisque, libereLe, temporaire, realiseeHorsPlanning, date);
        }
    }

    public record Case(SalleId salle, CreneauId creneau, JourDeLaSemaine jour, int capacite, List<Occupant> occupants) {
    }

    /** Salle, créneau et jour nuls pour un conflit qui ne dépend pas d'une case (générateur mixte). */
    public record Conflit(TypeDeConflit type, SalleId salle, CreneauId creneau, JourDeLaSemaine jour, List<String> patients) {
    }

    public record SeanceSansCase(PatientId patient, String nom, LocalDate date, JourDeLaSemaine jour) {
    }

    public record Semaine(LocalDate debut, LocalDate fin, List<Jour> jours, List<Salle> salles, List<Creneau> creneaux,
                          List<Case> cases, List<Conflit> conflits, int patientsAReplanifier, List<SeanceSansCase> seancesSansCase) {
    }

    /** Le dimanche de la semaine contenant ce jour. */
    public static LocalDate debutDeLaSemaineDe(LocalDate jour) {
        return jour.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    public static Semaine construire(Donnees donnees, LocalDate jourDeLaSemaine) {
        LocalDate debut = debutDeLaSemaineDe(jourDeLaSemaine);
        RessourcesDuCentre ressources = donnees.ressources();
        Set<JourDeLaSemaine> ouverts = ressources.joursOuverts();
        Map<GenerateurId, Generateur> generateurs = new HashMap<>();
        Map<SalleId, Integer> capacites = new HashMap<>();
        for (Generateur g : ressources.generateurs()) {
            generateurs.put(g.id(), g);
            capacites.merge(g.salle(), 1, Integer::sum);
        }

        List<Jour> jours = new ArrayList<>();
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            LocalDate date = debut.plusDays(jour.ordinal());
            RessourcesDuCentre.Fermeture fermeture = ressources.fermetures().stream().filter(f -> f.date().equals(date)).findFirst().orElse(null);
            String motif = fermeture == null ? null : (fermeture.motif() == null ? "" : fermeture.motif());
            jours.add(new Jour(jour, date, ouverts.contains(jour), motif));
        }

        // Les patients présents dans la semaine : place complète en salle et créneau, au moins un jour, place pas libérée avant
        // la semaine, pas en sommeil (KB-PLN-71). Un patient sans présence de la semaine ne compte ni dans les cases ni dans les conflits.
        List<PatientDeLaSemaine> presents = donnees.patients().stream()
                .filter(p -> p.place().salle() != null && p.place().creneau() != null && !p.place().jours().isEmpty())
                .filter(p -> !p.sejour().enSommeil() && !p.sejour().placeLibereeAvant(debut)).toList();

        Map<String, DeplacementTemporaire> temporaires = new HashMap<>();
        for (DeplacementTemporaire t : donnees.deplacements()) temporaires.put(t.patient() + "|" + t.date(), t);

        Map<String, List<Occupant>> occupants = new LinkedHashMap<>();
        Map<String, List<Occupation>> occupationsParCase = new LinkedHashMap<>();
        for (PatientDeLaSemaine p : presents) {
            Occupation o = p.place();
            if (!ressources.contientSalle(o.salle()) || !ressources.contientCreneau(o.creneau())) continue;
            Generateur g = o.generateur() == null ? null : generateurs.get(o.generateur());
            boolean aRisque = o.categorie() == CategorieDIsolement.A_RISQUE;
            LocalDate libereLe = p.sejour().dernierJourDePlace() == null ? null : p.sejour().dernierJourDePlace().plusDays(1);
            Occupant habituel = new Occupant(p.patient(), p.nom(), g == null ? null : g.code(), aRisque, libereLe, false, false, null);
            for (JourDeLaSemaine jour : o.jours()) {
                LocalDate date = debut.plusDays(jour.ordinal());
                if (!p.sejour().occupeSaPlaceLe(date)) continue;
                DeplacementTemporaire t = temporaires.get(p.patient() + "|" + date);
                if (t != null && t.salle() != null && t.creneau() != null && ressources.contientSalle(t.salle())
                        && ressources.contientCreneau(t.creneau())) {
                    Generateur cible = t.generateur() == null ? null : generateurs.get(t.generateur());
                    String cle = cle(t.salle(), t.creneau(), jour);
                    occupants.computeIfAbsent(cle, k -> new ArrayList<>()).add(new Occupant(p.patient(), p.nom(),
                            cible == null ? null : cible.code(), aRisque, libereLe, true, false, null));
                    occupationsParCase.computeIfAbsent(cle, k -> new ArrayList<>()).add(new Occupation(p.patient(), t.salle(),
                            t.creneau(), t.generateur(), EnumSet.of(jour), o.categorie()));
                    continue;
                }
                String cle = cle(o.salle(), o.creneau(), jour);
                occupants.computeIfAbsent(cle, k -> new ArrayList<>()).add(habituel);
                occupationsParCase.computeIfAbsent(cle, k -> new ArrayList<>()).add(o);
            }
        }

        List<SeanceSansCase> sansCase = placerLesSeancesRealisees(donnees, presents, debut, occupants, generateurs);

        Map<PatientId, String> noms = new HashMap<>();
        donnees.patients().forEach(p -> noms.put(p.patient(), p.nom()));
        donnees.realisees().forEach(s -> noms.putIfAbsent(s.patient(), s.nom()));

        List<Case> cases = new ArrayList<>();
        List<Conflit> conflits = new ArrayList<>();
        for (Salle salle : ressources.salles()) {
            int capacite = capacites.getOrDefault(salle.id(), 0);
            for (Creneau creneau : ressources.creneaux()) {
                for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                    String cle = cle(salle.id(), creneau.id(), jour);
                    List<Occupant> liste = new ArrayList<>(occupants.getOrDefault(cle, List.of()));
                    liste.sort(Comparator.comparing(Occupant::nom));
                    // une case fermée (fermeture hebdomadaire, férié ou fermeture exceptionnelle) n'a plus de capacité (KB-PLN-72)
                    cases.add(new Case(salle.id(), creneau.id(), jour, jours.get(jour.ordinal()).ferme() ? 0 : capacite, liste));
                    conflitsDeLaCase(conflits, salle, creneau, jour, capacite, occupationsParCase.getOrDefault(cle, List.of()),
                            generateurs, noms, ressources);
                }
            }
        }
        conflitsDeGenerateurMixte(conflits, presents, generateurs, noms);

        Set<PatientId> aReplanifier = new HashSet<>();
        for (Case c : cases) {
            if (!jours.get(c.jour().ordinal()).ferme()) continue;
            c.occupants().stream().filter(o -> !o.realiseeHorsPlanning()).forEach(o -> aReplanifier.add(o.patient()));
        }
        return new Semaine(debut, debut.plusDays(6), jours, ressources.salles(), ressources.creneaux(), cases, conflits,
                aReplanifier.size(), sansCase);
    }

    private static List<SeanceSansCase> placerLesSeancesRealisees(Donnees donnees, List<PatientDeLaSemaine> presents, LocalDate debut,
                                                                  Map<String, List<Occupant>> occupants,
                                                                  Map<GenerateurId, Generateur> generateurs) {
        RessourcesDuCentre ressources = donnees.ressources();
        Set<String> deja = new HashSet<>();
        occupants.forEach((cle, liste) -> {
            String jour = cle.substring(cle.lastIndexOf('|') + 1);
            liste.forEach(o -> deja.add(o.patient() + "|" + jour));
        });
        Map<PatientId, Occupation> placeActuelle = new HashMap<>();
        presents.forEach(p -> placeActuelle.putIfAbsent(p.patient(), p.place()));

        List<SeanceSansCase> sansCase = new ArrayList<>();
        Map<PatientId, LocalDate> horsPlanning = new HashMap<>();
        LocalDate fin = debut.plusDays(6);
        for (SeanceRealisee s : donnees.realisees()) {
            if (s.date().isBefore(debut) || s.date().isAfter(fin)) continue;
            JourDeLaSemaine jour = JourDeLaSemaine.de(s.date().getDayOfWeek());
            if (deja.contains(s.patient() + "|" + jour)) continue;
            SalleId salle = s.salle();
            CreneauId creneau = s.creneau();
            GenerateurId generateur = s.generateur();
            boolean aRisque = s.aRisque();
            Occupation actuelle = placeActuelle.get(s.patient());
            if (!s.placeConnue() && actuelle != null) {
                salle = actuelle.salle();
                creneau = actuelle.creneau();
                generateur = actuelle.generateur();
                aRisque = actuelle.categorie() == CategorieDIsolement.A_RISQUE;
            }
            horsPlanning.merge(s.patient(), s.date(), (a, b) -> a.isBefore(b) ? a : b);
            if (salle == null || creneau == null || !ressources.contientSalle(salle) || !ressources.contientCreneau(creneau)) {
                sansCase.add(new SeanceSansCase(s.patient(), s.nom(), s.date(), jour));
                continue;
            }
            Generateur g = generateur == null ? null : generateurs.get(generateur);
            occupants.computeIfAbsent(cle(salle, creneau, jour), k -> new ArrayList<>())
                    .add(new Occupant(s.patient(), s.nom(), g == null ? null : g.code(), aRisque, null, false, true, null));
            deja.add(s.patient() + "|" + jour);
        }
        if (horsPlanning.isEmpty()) return sansCase;

        Set<String> realisees = new HashSet<>();
        donnees.realisees().forEach(s -> realisees.add(s.patient() + "|" + s.date()));
        occupants.forEach((cle, liste) -> {
            JourDeLaSemaine jour = JourDeLaSemaine.valueOf(cle.substring(cle.lastIndexOf('|') + 1));
            LocalDate date = debut.plusDays(jour.ordinal());
            liste.replaceAll(o -> {
                LocalDate dejaLe = horsPlanning.get(o.patient());
                boolean aVerifier = dejaLe != null && !o.realiseeHorsPlanning() && date.isAfter(dejaLe)
                        && !realisees.contains(o.patient() + "|" + date);
                return aVerifier ? o.avecDejaRealiseeLe(dejaLe) : o;
            });
        });
        sansCase.sort(Comparator.comparing(SeanceSansCase::date).thenComparing(SeanceSansCase::nom));
        return sansCase;
    }

    private static void conflitsDeLaCase(List<Conflit> conflits, Salle salle, Creneau creneau, JourDeLaSemaine jour, int capacite,
                                         List<Occupation> occupations, Map<GenerateurId, Generateur> generateurs,
                                         Map<PatientId, String> noms, RessourcesDuCentre ressources) {
        if (occupations.isEmpty()) return;
        Map<GenerateurId, List<String>> parGenerateur = new LinkedHashMap<>();
        List<String> sansGenerateur = new ArrayList<>();
        List<String> horsService = new ArrayList<>();
        List<String> isolementKo = new ArrayList<>();
        for (Occupation o : occupations) {
            String nom = noms.getOrDefault(o.patient(), "");
            Generateur g = o.generateur() == null ? null : generateurs.get(o.generateur());
            if (o.generateur() == null) {
                sansGenerateur.add(nom);
            } else if (g == null || !g.salle().equals(o.salle())) {
                horsService.add(nom);
            } else {
                parGenerateur.computeIfAbsent(g.id(), k -> new ArrayList<>()).add(nom);
            }
            boolean aRisque = o.categorie() == CategorieDIsolement.A_RISQUE;
            if (ressources.estIsolement(o.salle()) != aRisque && (aRisque || ressources.aUneSalleDIsolement())) isolementKo.add(nom);
        }
        parGenerateur.values().stream().filter(l -> l.size() > 1)
                .forEach(l -> conflits.add(new Conflit(TypeDeConflit.GENERATEUR_DOUBLE, salle.id(), creneau.id(), jour, l)));
        if (!horsService.isEmpty()) conflits.add(new Conflit(TypeDeConflit.GENERATEUR_INDISPONIBLE, salle.id(), creneau.id(), jour, horsService));
        if (!sansGenerateur.isEmpty()) conflits.add(new Conflit(TypeDeConflit.SANS_GENERATEUR, salle.id(), creneau.id(), jour, sansGenerateur));
        if (!isolementKo.isEmpty()) conflits.add(new Conflit(TypeDeConflit.ISOLEMENT_NON_RESPECTE, salle.id(), creneau.id(), jour, isolementKo));
        if (occupations.size() > capacite) {
            conflits.add(new Conflit(TypeDeConflit.SALLE_SURCHARGEE, salle.id(), creneau.id(), jour,
                    occupations.stream().map(o -> noms.getOrDefault(o.patient(), "")).sorted().toList()));
        }
    }

    /** Un même générateur sert à la fois des patients à risque et des patients sans risque (quel que soit le jour). */
    private static void conflitsDeGenerateurMixte(List<Conflit> conflits, List<PatientDeLaSemaine> presents,
                                                  Map<GenerateurId, Generateur> generateurs, Map<PatientId, String> noms) {
        // ordre déterministe (revue P6) : générateurs par code, puis les patients à risque, puis les sans risque, chaque groupe par nom
        Map<GenerateurId, Set<PatientId>> aRisque = new HashMap<>();
        Map<GenerateurId, Set<PatientId>> sansRisque = new HashMap<>();
        for (PatientDeLaSemaine p : presents) {
            GenerateurId g = p.place().generateur();
            if (g == null || !generateurs.containsKey(g)) continue;
            (p.place().categorie().aRisque() ? aRisque : sansRisque).computeIfAbsent(g, k -> new HashSet<>()).add(p.patient());
        }
        List<GenerateurId> concernes = aRisque.keySet().stream().filter(sansRisque::containsKey)
                .sorted(Comparator.comparing((GenerateurId g) -> generateurs.get(g).code()).thenComparing(g -> g.valeur().toString())).toList();
        for (GenerateurId g : concernes) {
            List<String> patients = new ArrayList<>();
            patients.add(generateurs.get(g).code());
            aRisque.get(g).stream().map(p -> noms.getOrDefault(p, "")).sorted().forEach(patients::add);
            sansRisque.get(g).stream().map(p -> noms.getOrDefault(p, "")).sorted().forEach(patients::add);
            conflits.add(new Conflit(TypeDeConflit.GENERATEUR_MIXTE, null, null, null, patients));
        }
    }

    private static String cle(SalleId salle, CreneauId creneau, JourDeLaSemaine jour) {
        return salle.valeur() + "|" + creneau.valeur() + "|" + jour;
    }
}
