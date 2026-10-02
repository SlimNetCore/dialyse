package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.Alerte;
import com.hemodialyse.backend.domain.planning.model.Planning.CaseGrille;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DemandePlacement;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.FermetureJour;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.Proposition;
import com.hemodialyse.backend.domain.planning.model.Planning.Raison;
import com.hemodialyse.backend.domain.planning.model.Planning.ResultatProposition;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;

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
 * Service de domaine pur : propose où placer un nouveau patient (salle, créneau, générateur, jours) et calcule la
 * grille de disponibilité des salles.
 * <p>
 * Un patient garde le même créneau, la même salle et le même générateur tous ses jours de dialyse ; un générateur ne
 * sert qu'un patient par jour et par créneau. Une salle n'a de place pour un jour et un créneau que si l'un de ses
 * générateurs en service est libre, après déduction des patients placés dans la salle sans générateur précis. Seuls
 * les jours d'ouverture du centre sont proposés.
 * <p>
 * Isolement : un patient à risque infectieux (sérologie positive) n'est placé qu'en salle d'isolement et jamais sur
 * un générateur qui sert un patient sans risque ; inversement, un patient sans risque n'est ni placé en salle
 * d'isolement ni sur un générateur dédié à un patient à risque.
 * <p>
 * Classement d'une proposition (0 à 100) : espacement des jours (éviter les séances sur des jours consécutifs, avec un
 * léger avantage aux schémas usuels lundi-mercredi-vendredi et mardi-jeudi-samedi), créneau et salle souhaités, et
 * charge de la salle (répartition équilibrée).
 */
public final class PlanificationAffectationService {

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

    private static final List<Set<JourSemaine>> SCHEMAS_USUELS = List.of(
            EnumSet.of(JourSemaine.LUNDI, JourSemaine.MERCREDI, JourSemaine.VENDREDI),
            EnumSet.of(JourSemaine.MARDI, JourSemaine.JEUDI, JourSemaine.SAMEDI),
            EnumSet.of(JourSemaine.LUNDI, JourSemaine.JEUDI),
            EnumSet.of(JourSemaine.MARDI, JourSemaine.VENDREDI),
            EnumSet.of(JourSemaine.MERCREDI, JourSemaine.SAMEDI));

    private PlanificationAffectationService() {
    }

    // ───────────────────────────── Grille de disponibilité ─────────────────────────────

    /**
     * Capacité et occupation de chaque salle, pour chaque créneau et chaque jour ; un jour de fermeture
     * hebdomadaire a une capacité nulle.
     */
    public static List<CaseGrille> grille(DonneesPlanning donnees) {
        Index index = new Index(donnees);
        List<CaseGrille> cases = new ArrayList<>();
        for (SalleRef salle : donnees.salles()) {
            int capacite = index.generateursDe(salle.id()).size();
            for (CreneauRef creneau : donnees.creneaux()) {
                for (JourSemaine jour : JourSemaine.values()) {
                    if (!index.joursOuverts.contains(jour)) {
                        cases.add(new CaseGrille(salle.id(), creneau.id(), jour, 0, 0));
                        continue;
                    }
                    int libres = index.libres(salle.id(), creneau.id(), jour);
                    cases.add(new CaseGrille(salle.id(), creneau.id(), jour, capacite, capacite - libres));
                }
            }
        }
        return cases;
    }

    // ───────────────────────────── Propositions ─────────────────────────────

    /**
     * @param limite nombre maximal de propositions renvoyées (borné à {@value #LIMITE_MAX})
     * @return les meilleures propositions, du meilleur score au moins bon ; vide si aucune place ne convient
     */
    public static List<Proposition> proposer(DonneesPlanning donnees, DemandePlacement demande, int limite) {
        return rechercher(donnees, demande, limite).propositions();
    }

    /**
     * Comme {@link #proposer}, avec les alertes expliquant l'absence éventuelle de proposition (jours imposés
     * fermés, aucune salle d'isolement pour un patient à risque).
     */
    public static ResultatProposition rechercher(DonneesPlanning donnees, DemandePlacement demande, int limite) {
        Set<JourSemaine> imposes = demande.joursImposes() == null ? Set.of() : demande.joursImposes();
        int n = imposes.isEmpty() ? demande.seancesParSemaine() : imposes.size();
        if (n < 1 || n > JourSemaine.NB_JOURS) {
            throw new IllegalArgumentException("Le nombre de séances par semaine doit être compris entre 1 et 7");
        }
        Index index = new Index(donnees);
        List<Alerte> alertes = new ArrayList<>();
        if (!imposes.isEmpty() && !index.joursOuverts.containsAll(imposes)) {
            alertes.add(Alerte.JOURS_IMPOSES_FERMES);
            return new ResultatProposition(List.of(), alertes);
        }
        boolean aRisque = demande.patientARisque();
        if (aRisque && donnees.salles().stream().noneMatch(s -> index.estIsolement(s.id()))) {
            alertes.add(Alerte.AUCUNE_SALLE_ISOLEMENT);
            return new ResultatProposition(List.of(), alertes);
        }

        Map<Cle, Candidat> parSalleCreneauJours = new LinkedHashMap<>();
        for (SalleRef salle : donnees.salles()) {
            if (index.estIsolement(salle.id()) != aRisque) continue;
            for (CreneauRef creneau : donnees.creneaux()) {
                for (GenerateurRef generateur : index.generateursDe(salle.id())) {
                    if (!index.generateurCompatible(generateur, aRisque)) continue;
                    Set<JourSemaine> libres = EnumSet.noneOf(JourSemaine.class);
                    for (JourSemaine jour : index.joursOuverts) {
                        if (index.generateurLibre(generateur, creneau.id(), jour)) libres.add(jour);
                    }
                    for (Set<JourSemaine> jours : meilleursJours(libres, imposes, n)) {
                        Cle cle = new Cle(salle.id(), creneau.id(), jours);
                        parSalleCreneauJours.computeIfAbsent(cle, k -> new Candidat(salle, creneau, jours))
                                .generateurs.add(generateur);
                    }
                }
            }
        }

        List<Proposition> propositions = new ArrayList<>();
        for (Candidat c : parSalleCreneauJours.values()) {
            propositions.add(c.versProposition(index, imposes, demande));
        }
        propositions.sort(Comparator.comparingInt(Proposition::score).reversed()
                .thenComparing(p -> p.salle().nom())
                .thenComparingInt(p -> p.creneau().ordre())
                .thenComparing(p -> p.generateur().code())
                .thenComparing(p -> p.jours().toString()));
        return new ResultatProposition(
                limiter(propositions, Math.min(Math.max(limite, 1), LIMITE_MAX)), List.copyOf(alertes));
    }

    /**
     * Garde au plus deux propositions par salle et créneau (variété des choix), puis applique la limite globale.
     */
    private static List<Proposition> limiter(List<Proposition> triees, int limite) {
        Map<String, Integer> parSalleCreneau = new HashMap<>();
        List<Proposition> retenues = new ArrayList<>();
        for (Proposition p : triees) {
            String cle = p.salle().id() + "|" + p.creneau().id();
            int deja = parSalleCreneau.merge(cle, 1, Integer::sum);
            if (deja <= PROPOSITIONS_PAR_SALLE_ET_CRENEAU) retenues.add(p);
            if (retenues.size() == limite) break;
        }
        return retenues;
    }

    /**
     * Meilleurs ensembles de jours de taille {@code n} parmi les jours libres (au plus deux, du meilleur au moins
     * bon) ; l'ensemble imposé, s'il existe et s'il est entièrement libre.
     */
    private static List<Set<JourSemaine>> meilleursJours(Set<JourSemaine> libres, Set<JourSemaine> imposes, int n) {
        if (!imposes.isEmpty()) {
            return libres.containsAll(imposes) ? List.of(EnumSet.copyOf(imposes)) : List.of();
        }
        if (libres.size() < n) return List.of();
        List<Set<JourSemaine>> combinaisons = new ArrayList<>();
        JourSemaine[] jours = JourSemaine.values();
        for (int mask = 1; mask < (1 << jours.length); mask++) {
            if (Integer.bitCount(mask) != n) continue;
            Set<JourSemaine> combinaison = EnumSet.noneOf(JourSemaine.class);
            for (int i = 0; i < jours.length; i++) {
                if ((mask & (1 << i)) != 0) combinaison.add(jours[i]);
            }
            if (libres.containsAll(combinaison)) combinaisons.add(combinaison);
        }
        combinaisons.sort(Comparator.comparingInt((Set<JourSemaine> c) -> scoreEspacement(c, n)).reversed());
        return combinaisons.stream().limit(PROPOSITIONS_PAR_SALLE_ET_CRENEAU).toList();
    }

    /**
     * Qualité de l'espacement des séances (0 à 100) : pénalise les séances sur des jours consécutifs au-delà du
     * minimum inévitable, et les trop longues interruptions ; avantage léger aux schémas usuels.
     */
    static int scoreEspacement(Set<JourSemaine> jours, int n) {
        if (n <= 1) return 100;
        List<JourSemaine> tries = jours.stream().sorted().toList();
        int consecutifs = 0;
        int ecartMax = 0;
        for (int i = 0; i < tries.size(); i++) {
            JourSemaine suivant = tries.get((i + 1) % tries.size());
            int ecart = tries.get(i).ecartAvec(suivant);
            if (ecart == 1) consecutifs++;
            ecartMax = Math.max(ecartMax, ecart);
        }
        int consecutifsInevitables = Math.max(0, 2 * n - JourSemaine.NB_JOURS);
        int ecartMaxIdeal = (JourSemaine.NB_JOURS + n - 1) / n;
        int score = 100
                - PENALITE_JOURS_CONSECUTIFS * Math.max(0, consecutifs - consecutifsInevitables)
                - PENALITE_ECART_MAX * Math.max(0, ecartMax - ecartMaxIdeal);
        score = Math.max(0, Math.min(100, score));
        // Quand des schémas usuels existent pour ce nombre de séances, les autres schémas perdent quelques points
        boolean schemaUsuelExiste = SCHEMAS_USUELS.stream().anyMatch(s -> s.size() == n);
        return schemaUsuelExiste && !SCHEMAS_USUELS.contains(jours) ? Math.max(0, score - BONUS_SCHEMA_USUEL) : score;
    }

    // ───────────────────────────── Structures internes ─────────────────────────────

    private record Cle(UUID salleId, UUID creneauId, Set<JourSemaine> jours) {
    }

    private static final class Candidat {
        private final SalleRef salle;
        private final CreneauRef creneau;
        private final Set<JourSemaine> jours;
        private final List<GenerateurRef> generateurs = new ArrayList<>();

        private Candidat(SalleRef salle, CreneauRef creneau, Set<JourSemaine> jours) {
            this.salle = salle;
            this.creneau = creneau;
            this.jours = jours;
        }

        private Proposition versProposition(Index index, Set<JourSemaine> imposes, DemandePlacement demande) {
            List<GenerateurRef> tries = generateurs.stream().sorted(Comparator.comparing(GenerateurRef::code)).toList();
            List<Raison> raisons = new ArrayList<>();
            int espacement = imposes.isEmpty() ? scoreEspacement(jours, jours.size()) : 100;
            if (!imposes.isEmpty()) {
                raisons.add(Raison.JOURS_IMPOSES_LIBRES);
            } else if (jours.size() > 1 && espacement >= SEUIL_BIEN_ESPACES) {
                raisons.add(Raison.JOURS_BIEN_ESPACES);
            }
            if (demande.patientARisque()) raisons.add(Raison.SALLE_ISOLEMENT);
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
            // Normalisé sur le maximum atteignable pour cette demande : 100 = placement idéal
            double maximum = POIDS_ESPACEMENT * 100 + POINTS_CHARGE
                    + (demande.creneauPrefere() != null ? POINTS_CRENEAU_PREFERE : 0)
                    + (demande.sallePreferee() != null ? POINTS_SALLE_PREFEREE : 0);
            int score = (int) Math.min(100, Math.round(100 * points / maximum));
            List<JourSemaine> joursTries = jours.stream().sorted().toList();
            return new Proposition(salle, creneau, tries.getFirst(), tries.subList(1, tries.size()),
                    joursTries, score, List.copyOf(raisons), index.prochainesFermetures(joursTries));
        }
    }

    /**
     * Index d'occupation : générateurs occupés par jour et créneau, places de salle prises sans générateur précis,
     * générateurs dédiés aux patients à risque ou servant des patients sans risque, jours d'ouverture, salles
     * d'isolement et fermetures datées.
     */
    private static final class Index {
        private final Map<UUID, List<GenerateurRef>> generateursParSalle = new HashMap<>();
        private final Map<String, Integer> generateursOccupes = new HashMap<>();
        private final Map<String, Integer> sansGenerateur = new HashMap<>();
        private final Set<String> generateurJourCreneauOccupe = new HashSet<>();
        private final Set<UUID> generateursARisque = new HashSet<>();
        private final Set<UUID> generateursSansRisque = new HashSet<>();
        private final Set<JourSemaine> joursOuverts;
        private final Set<UUID> sallesIsolement;
        private final List<Fermeture> fermetures;

        private Index(DonneesPlanning donnees) {
            this.joursOuverts = donnees.joursOuverts() == null || donnees.joursOuverts().isEmpty()
                    ? EnumSet.allOf(JourSemaine.class) : EnumSet.copyOf(donnees.joursOuverts());
            this.sallesIsolement = donnees.sallesIsolement() == null ? Set.of() : donnees.sallesIsolement();
            this.fermetures = donnees.fermetures() == null ? List.of() : donnees.fermetures();

            Map<UUID, GenerateurRef> operationnels = new HashMap<>();
            for (GenerateurRef g : donnees.generateurs()) {
                operationnels.put(g.id(), g);
                generateursParSalle.computeIfAbsent(g.salleId(), k -> new ArrayList<>()).add(g);
            }
            generateursParSalle.values().forEach(l -> l.sort(Comparator.comparing(GenerateurRef::code)));

            for (Occupation o : donnees.occupations()) {
                GenerateurRef g = o.generateurId() == null ? null : operationnels.get(o.generateurId());
                boolean surGenerateurDeLaSalle = g != null && g.salleId().equals(o.salleId());
                if (surGenerateurDeLaSalle) {
                    (o.aRisque() ? generateursARisque : generateursSansRisque).add(g.id());
                }
                for (JourSemaine jour : o.jours()) {
                    String caseKey = caseKey(o.salleId(), o.creneauId(), jour);
                    if (surGenerateurDeLaSalle
                            && generateurJourCreneauOccupe.add(genKey(g.id(), o.creneauId(), jour))) {
                        generateursOccupes.merge(caseKey, 1, Integer::sum);
                    } else if (!surGenerateurDeLaSalle) {
                        // générateur inconnu, hors service ou d'une autre salle : le patient prend une place de la salle
                        sansGenerateur.merge(caseKey, 1, Integer::sum);
                    }
                }
            }
        }

        private static String caseKey(UUID salle, UUID creneau, JourSemaine jour) {
            return salle + "|" + creneau + "|" + jour;
        }

        private static String genKey(UUID generateur, UUID creneau, JourSemaine jour) {
            return generateur + "|" + creneau + "|" + jour;
        }

        private List<GenerateurRef> generateursDe(UUID salleId) {
            return generateursParSalle.getOrDefault(salleId, List.of());
        }

        private boolean estIsolement(UUID salleId) {
            return sallesIsolement.contains(salleId);
        }

        /**
         * Un générateur ne mélange jamais patients à risque et patients sans risque.
         */
        private boolean generateurCompatible(GenerateurRef g, boolean aRisque) {
            return aRisque ? !generateursSansRisque.contains(g.id()) : !generateursARisque.contains(g.id());
        }

        private int libres(UUID salleId, UUID creneauId, JourSemaine jour) {
            String key = caseKey(salleId, creneauId, jour);
            int capacite = generateursDe(salleId).size();
            int occupes = generateursOccupes.getOrDefault(key, 0) + sansGenerateur.getOrDefault(key, 0);
            return Math.max(0, capacite - occupes);
        }

        private boolean generateurLibre(GenerateurRef g, UUID creneauId, JourSemaine jour) {
            return !generateurJourCreneauOccupe.contains(genKey(g.id(), creneauId, jour))
                    && libres(g.salleId(), creneauId, jour) > 0;
        }

        private double ratioLibreMoyen(UUID salleId, UUID creneauId, Set<JourSemaine> jours) {
            int capacite = generateursDe(salleId).size();
            if (capacite == 0 || jours.isEmpty()) return 0;
            double somme = 0;
            for (JourSemaine jour : jours) somme += (double) libres(salleId, creneauId, jour) / capacite;
            return somme / jours.size();
        }

        /**
         * Pour chaque jour proposé, la prochaine fermeture datée tombant ce jour de la semaine.
         */
        private List<FermetureJour> prochainesFermetures(List<JourSemaine> jours) {
            Map<JourSemaine, Fermeture> premiere = new HashMap<>();
            for (Fermeture f : fermetures.stream().sorted(Comparator.comparing(Fermeture::date)).toList()) {
                JourSemaine jour = JourSemaine.de(f.date().getDayOfWeek());
                if (jours.contains(jour)) premiere.putIfAbsent(jour, f);
            }
            return jours.stream().filter(premiere::containsKey)
                    .map(j -> new FermetureJour(j, premiere.get(j).date(), premiere.get(j).motif())).toList();
        }
    }
}
