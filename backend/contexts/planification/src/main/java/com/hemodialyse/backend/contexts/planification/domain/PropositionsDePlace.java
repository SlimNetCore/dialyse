package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service de domaine pur : propose où placer un patient (salle, créneau, générateur, jours) et calcule la grille de
 * disponibilité (RG-PLN-020 à 036). Un patient garde la même place tous ses jours ; un générateur ne sert qu'un patient par
 * jour et par créneau ; seuls les jours d'ouverture sont proposés ; un patient à risque n'est proposé qu'en salle d'isolement et
 * jamais sur un générateur qui sert un patient sans risque (et inversement).
 * <p>
 * Score (0 à 100, RG-PLN-030) : espacement des jours (poids 0,55), créneau souhaité (20), salle souhaitée (15) et charge de la
 * salle (10), normalisé sur le maximum atteignable pour la demande : 100 = placement idéal.
 */
public final class PropositionsDePlace {

    public static final int LIMITE_MAX = 30;
    private static final int PROPOSITIONS_PAR_SALLE_ET_CRENEAU = 2;
    private static final int POINTS_CRENEAU_PREFERE = 20;
    private static final int POINTS_SALLE_PREFEREE = 15;
    private static final int POINTS_CHARGE = 10;
    private static final int BONUS_SCHEMA_USUEL = 3;
    private static final double POIDS_ESPACEMENT = 0.55;
    private static final int PENALITE_JOURS_CONSECUTIFS = 30;
    private static final int PENALITE_ECART_MAX = 8;
    private static final double SEUIL_SALLE_PEU_CHARGEE = 0.7;
    private static final int SEUIL_BIEN_ESPACES = 95;

    private static final List<Set<JourDeLaSemaine>> SCHEMAS_USUELS = List.of(
            EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MERCREDI, JourDeLaSemaine.VENDREDI),
            EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.JEUDI, JourDeLaSemaine.SAMEDI),
            EnumSet.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI),
            EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.VENDREDI),
            EnumSet.of(JourDeLaSemaine.MERCREDI, JourDeLaSemaine.SAMEDI));

    private PropositionsDePlace() {
    }

    public enum Raison {
        JOURS_IMPOSES_LIBRES,
        JOURS_BIEN_ESPACES,
        CRENEAU_PREFERE,
        SALLE_PREFEREE,
        SALLE_PEU_CHARGEE,
        SALLE_ISOLEMENT
    }

    /** Pourquoi aucune proposition ne peut être faite. */
    public enum Alerte {
        AUCUNE_SALLE_ISOLEMENT,
        JOURS_IMPOSES_FERMES
    }

    /**
     * Besoin d'un patient à placer.
     *
     * @param seancesParSemaine nombre de séances (1 à 7) ; ignoré si des jours sont imposés
     * @param joursImposes      jours obligatoires (vide : les jours les mieux espacés)
     * @param creneauPrefere    facultatif
     * @param sallePreferee     facultatif
     */
    public record DemandeDePlace(int seancesParSemaine, Set<JourDeLaSemaine> joursImposes, CreneauId creneauPrefere,
                                 SalleId sallePreferee, CategorieDIsolement categorie) {
        public DemandeDePlace {
            joursImposes = joursImposes == null || joursImposes.isEmpty() ? Set.of() : Set.copyOf(joursImposes);
            categorie = categorie == null ? CategorieDIsolement.SANS_RISQUE : categorie;
        }

        int nombreDeSeances() {
            int n = joursImposes.isEmpty() ? seancesParSemaine : joursImposes.size();
            if (n < 1 || n > JourDeLaSemaine.NOMBRE_DE_JOURS) {
                throw new SaisieInvalide("Le nombre de séances par semaine doit être compris entre 1 et 7");
            }
            return n;
        }
    }

    /** Prochaine fermeture datée tombant sur un jour de dialyse proposé (RG-PLN-013). */
    public record FermetureDuJour(JourDeLaSemaine jour, LocalDate date, String motif) {
    }

    /** @param generateursAlternatifs autres générateurs libres de la salle pour exactement les mêmes jours et le même créneau */
    public record Proposition(Salle salle, Creneau creneau, Generateur generateur, List<Generateur> generateursAlternatifs,
                              List<JourDeLaSemaine> jours, int score, List<Raison> raisons, List<FermetureDuJour> fermetures) {
        public Place enPlace() {
            return new Place(salle.id(), creneau.id(), generateur.id(), Set.copyOf(jours));
        }
    }

    public record Resultat(List<Proposition> propositions, List<Alerte> alertes) {
    }

    /** Capacité et occupation d'une salle pour un créneau et un jour (capacité nulle un jour de fermeture hebdomadaire). */
    public record CaseDeGrille(SalleId salle, CreneauId creneau, JourDeLaSemaine jour, int capacite, int occupes) {
        public int libres() {
            return Math.max(0, capacite - occupes);
        }
    }

    // ───────────────────────────── Grille ─────────────────────────────

    public static List<CaseDeGrille> grille(RessourcesDuCentre ressources, Collection<Occupation> occupations) {
        return grille(new IndexDOccupation(ressources, occupations));
    }

    static List<CaseDeGrille> grille(IndexDOccupation index) {
        List<CaseDeGrille> cases = new ArrayList<>();
        for (Salle salle : index.ressources().salles()) {
            int capacite = index.generateursDe(salle.id()).size();
            for (Creneau creneau : index.ressources().creneaux()) {
                for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                    if (!index.joursOuverts().contains(jour)) {
                        cases.add(new CaseDeGrille(salle.id(), creneau.id(), jour, 0, 0));
                        continue;
                    }
                    int libres = index.libres(salle.id(), creneau.id(), jour);
                    cases.add(new CaseDeGrille(salle.id(), creneau.id(), jour, capacite, capacite - libres));
                }
            }
        }
        return cases;
    }

    // ───────────────────────────── Propositions ─────────────────────────────

    /** @param limite nombre maximal de propositions (borné à {@value #LIMITE_MAX}) */
    public static Resultat rechercher(RessourcesDuCentre ressources, Collection<Occupation> occupations,
                                      DemandeDePlace demande, int limite) {
        return rechercher(new IndexDOccupation(ressources, occupations), demande, limite);
    }

    static Resultat rechercher(IndexDOccupation index, DemandeDePlace demande, int limite) {
        Set<JourDeLaSemaine> imposes = demande.joursImposes();
        int n = demande.nombreDeSeances();
        List<Alerte> alertes = new ArrayList<>();
        if (!imposes.isEmpty() && !index.joursOuverts().containsAll(imposes)) {
            alertes.add(Alerte.JOURS_IMPOSES_FERMES);
            return new Resultat(List.of(), alertes);
        }
        boolean aRisque = demande.categorie().aRisque();
        if (aRisque && !index.ressources().aUneSalleDIsolement()) {
            alertes.add(Alerte.AUCUNE_SALLE_ISOLEMENT);
            return new Resultat(List.of(), alertes);
        }

        Map<Cle, Candidat> parSalleCreneauJours = new LinkedHashMap<>();
        for (Salle salle : index.ressources().salles()) {
            if (index.estIsolement(salle.id()) != aRisque) continue;
            for (Creneau creneau : index.ressources().creneaux()) {
                for (Generateur generateur : index.generateursDe(salle.id())) {
                    if (!index.generateurCompatible(generateur, aRisque)) continue;
                    Set<JourDeLaSemaine> libres = EnumSet.noneOf(JourDeLaSemaine.class);
                    for (JourDeLaSemaine jour : index.joursOuverts()) {
                        if (index.generateurLibre(generateur, creneau.id(), jour)) libres.add(jour);
                    }
                    for (Set<JourDeLaSemaine> jours : meilleursJours(libres, imposes, n)) {
                        Cle cle = new Cle(salle.id(), creneau.id(), jours);
                        parSalleCreneauJours.computeIfAbsent(cle, k -> new Candidat(salle, creneau, jours))
                                .generateurs.add(generateur);
                    }
                }
            }
        }

        List<Proposition> propositions = new ArrayList<>();
        for (Candidat c : parSalleCreneauJours.values()) propositions.add(c.versProposition(index, imposes, demande));
        propositions.sort(Comparator.comparingInt(Proposition::score).reversed()
                .thenComparing(p -> p.salle().nom())
                .thenComparingInt(p -> p.creneau().ordre())
                .thenComparing(p -> p.generateur().code())
                .thenComparing(p -> p.jours().toString()));
        return new Resultat(limiter(propositions, Math.min(Math.max(limite, 1), LIMITE_MAX)), List.copyOf(alertes));
    }

    /** Au plus deux propositions par salle et créneau (variété, RG-PLN-033), puis la limite globale. */
    private static List<Proposition> limiter(List<Proposition> triees, int limite) {
        Map<String, Integer> parSalleCreneau = new HashMap<>();
        List<Proposition> retenues = new ArrayList<>();
        for (Proposition p : triees) {
            String cle = p.salle().id().valeur() + "|" + p.creneau().id().valeur();
            int deja = parSalleCreneau.merge(cle, 1, Integer::sum);
            if (deja <= PROPOSITIONS_PAR_SALLE_ET_CRENEAU) retenues.add(p);
            if (retenues.size() == limite) break;
        }
        return retenues;
    }

    private static List<Set<JourDeLaSemaine>> meilleursJours(Set<JourDeLaSemaine> libres, Set<JourDeLaSemaine> imposes, int n) {
        if (!imposes.isEmpty()) {
            return libres.containsAll(imposes) ? List.of(EnumSet.copyOf(imposes)) : List.of();
        }
        if (libres.size() < n) return List.of();
        List<Set<JourDeLaSemaine>> combinaisons = new ArrayList<>();
        JourDeLaSemaine[] jours = JourDeLaSemaine.values();
        for (int mask = 1; mask < (1 << jours.length); mask++) {
            if (Integer.bitCount(mask) != n) continue;
            Set<JourDeLaSemaine> combinaison = EnumSet.noneOf(JourDeLaSemaine.class);
            for (int i = 0; i < jours.length; i++) {
                if ((mask & (1 << i)) != 0) combinaison.add(jours[i]);
            }
            if (libres.containsAll(combinaison)) combinaisons.add(combinaison);
        }
        combinaisons.sort(Comparator.comparingInt((Set<JourDeLaSemaine> c) -> scoreEspacement(c, n)).reversed());
        return combinaisons.stream().limit(PROPOSITIONS_PAR_SALLE_ET_CRENEAU).toList();
    }

    /**
     * Qualité de l'espacement des séances (0 à 100, RG-PLN-031) : 30 points par paire de jours consécutifs au-delà du minimum
     * inévitable, 8 points par jour d'écart au-delà de l'idéal ; 3 points de moins hors des schémas usuels.
     */
    public static int scoreEspacement(Set<JourDeLaSemaine> jours, int n) {
        if (n <= 1) return 100;
        List<JourDeLaSemaine> tries = jours.stream().sorted().toList();
        int consecutifs = 0;
        int ecartMax = 0;
        for (int i = 0; i < tries.size(); i++) {
            int ecart = tries.get(i).ecartAvec(tries.get((i + 1) % tries.size()));
            if (ecart == 1) consecutifs++;
            ecartMax = Math.max(ecartMax, ecart);
        }
        int consecutifsInevitables = Math.max(0, 2 * n - JourDeLaSemaine.NOMBRE_DE_JOURS);
        int ecartMaxIdeal = (JourDeLaSemaine.NOMBRE_DE_JOURS + n - 1) / n;
        int score = 100 - PENALITE_JOURS_CONSECUTIFS * Math.max(0, consecutifs - consecutifsInevitables)
                - PENALITE_ECART_MAX * Math.max(0, ecartMax - ecartMaxIdeal);
        score = Math.max(0, Math.min(100, score));
        boolean schemaUsuelExiste = SCHEMAS_USUELS.stream().anyMatch(s -> s.size() == n);
        return schemaUsuelExiste && !SCHEMAS_USUELS.contains(jours) ? Math.max(0, score - BONUS_SCHEMA_USUEL) : score;
    }

    private record Cle(SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {
    }

    private static final class Candidat {
        private final Salle salle;
        private final Creneau creneau;
        private final Set<JourDeLaSemaine> jours;
        private final List<Generateur> generateurs = new ArrayList<>();

        private Candidat(Salle salle, Creneau creneau, Set<JourDeLaSemaine> jours) {
            this.salle = salle;
            this.creneau = creneau;
            this.jours = jours;
        }

        private Proposition versProposition(IndexDOccupation index, Set<JourDeLaSemaine> imposes, DemandeDePlace demande) {
            List<Generateur> tries = generateurs.stream().sorted(Comparator.comparing(Generateur::code)).toList();
            List<Raison> raisons = new ArrayList<>();
            int espacement = imposes.isEmpty() ? scoreEspacement(jours, jours.size()) : 100;
            if (!imposes.isEmpty()) {
                raisons.add(Raison.JOURS_IMPOSES_LIBRES);
            } else if (jours.size() > 1 && espacement >= SEUIL_BIEN_ESPACES) {
                raisons.add(Raison.JOURS_BIEN_ESPACES);
            }
            if (demande.categorie().aRisque()) raisons.add(Raison.SALLE_ISOLEMENT);
            double points = POIDS_ESPACEMENT * espacement;
            if (creneau.id().equals(demande.creneauPrefere())) {
                points += POINTS_CRENEAU_PREFERE;
                raisons.add(Raison.CRENEAU_PREFERE);
            }
            if (salle.id().equals(demande.sallePreferee())) {
                points += POINTS_SALLE_PREFEREE;
                raisons.add(Raison.SALLE_PREFEREE);
            }
            double ratioLibre = index.ratioLibreMoyen(salle.id(), creneau.id(), jours);
            points += POINTS_CHARGE * ratioLibre;
            if (ratioLibre >= SEUIL_SALLE_PEU_CHARGEE) raisons.add(Raison.SALLE_PEU_CHARGEE);
            double maximum = POIDS_ESPACEMENT * 100 + POINTS_CHARGE
                    + (demande.creneauPrefere() != null ? POINTS_CRENEAU_PREFERE : 0)
                    + (demande.sallePreferee() != null ? POINTS_SALLE_PREFEREE : 0);
            int score = (int) Math.min(100, Math.round(100 * points / maximum));
            List<JourDeLaSemaine> joursTries = jours.stream().sorted().toList();
            return new Proposition(salle, creneau, tries.getFirst(), tries.subList(1, tries.size()), joursTries, score,
                    List.copyOf(raisons), prochainesFermetures(index.fermetures(), joursTries));
        }

        /** Pour chaque jour proposé, la prochaine fermeture datée tombant ce jour de la semaine. */
        private static List<FermetureDuJour> prochainesFermetures(List<Fermeture> fermetures, List<JourDeLaSemaine> jours) {
            Map<JourDeLaSemaine, Fermeture> premiere = new HashMap<>();
            for (Fermeture f : fermetures.stream().sorted(Comparator.comparing(Fermeture::date)).toList()) {
                JourDeLaSemaine jour = JourDeLaSemaine.de(f.date().getDayOfWeek());
                if (jours.contains(jour)) premiere.putIfAbsent(jour, f);
            }
            return jours.stream().filter(premiere::containsKey)
                    .map(j -> new FermetureDuJour(j, premiere.get(j).date(), premiere.get(j).motif())).toList();
        }
    }
}
