package com.hemodialyse.backend.characterization.planification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de P8 (optimisation du planning) au-dessus de P6 : calculs réels du solveur (aucun double) sur des jeux de données
 * minuscules, lancés par l'API et attendus par interrogation de la base.
 * <p>
 * Déterminisme : le solveur (Timefold, graine fixe, terminaison par le temps) n'est utilisé que sur des problèmes à un seul
 * optimum (un ou deux patients, une ou deux places utiles) ; les durées sont au plancher de l'API (2 s). Aucun scénario ne
 * dépend du résultat d'un arbitrage entre solutions équivalentes. Les dates sont toujours nommées ({@code <S1.LUN>}...).
 * <p>
 * Isolation : chaque test attend la fin de tous les calculs avant de nettoyer (un calcul en cours écrirait dans des tables
 * déjà vidées).
 */
public abstract class OptimisationCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String OPTIMISATIONS = PLANNING + "/optimisations";
    protected static final UUID U_PLANIF = UUID.fromString("99997800-0000-0000-0000-0000000000d1");

    // ------------------------------------------------------------------ cycle

    @BeforeEach
    protected void setUpOptimisation() throws Exception {
        attendreLaFinDeTousLesCalculs();
        nettoyerOptimisation();
        aliasNommes.put(U_PLANIF.toString(), "<UTILISATEUR>");
    }

    @AfterEach
    protected void apresOptimisation() throws Exception {
        attendreLaFinDeTousLesCalculs();
        nettoyerOptimisation();
    }

    protected void nettoyerOptimisation() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM planification_calendrier_case WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM planification_optimisation WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM deplacement_temporaire WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM infirmier_affectation WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM gmao_interventions WHERE centre_id = ?", centre);
        }
    }

    /** Aucun calcul du solveur ne doit survivre à son test. */
    protected void attendreLaFinDeTousLesCalculs() throws Exception {
        long limite = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < limite) {
            Integer enCours = jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE statut = 'EN_COURS'",
                    Integer.class);
            if (enCours == null || enCours == 0) {
                Thread.sleep(150); // laisse l'écouteur terminer (calendrier figé après l'enregistrement du résultat)
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Un calcul d'optimisation ne se termine pas");
    }

    // ------------------------------------------------------------------ profils

    protected static RequestPostProcessor planifAdmin() {
        return comme(U_PLANIF, CENTRE_A, "ADMIN");
    }

    protected static RequestPostProcessor planifSecretaire() {
        return comme(U_PLANIF, CENTRE_A, "SECRETAIRE");
    }

    // ------------------------------------------------------------------ lancement et attente

    /** Corps de lancement : durée au plancher (2 s) ; les champs nuls sont omis (défauts de l'API). */
    protected static String lancement(String perimetre, LocalDate debut, Integer semaines) {
        StringBuilder b = new StringBuilder("{'perimetre':'").append(perimetre).append("','dureeMaxSecondes':2");
        if (debut != null) b.append(",'debutSemaine':'").append(debut).append("'");
        if (semaines != null) b.append(",'nbSemaines':").append(semaines);
        return json(b.append('}').toString());
    }

    protected Resp lancer(String perimetre, LocalDate debut, Integer semaines, RequestPostProcessor who) throws Exception {
        return doPost(OPTIMISATIONS, lancement(perimetre, debut, semaines), who);
    }

    /** Lance en tant qu'ADMIN, nomme l'exécution (`<RUN_n>`), attend sa fin et rend son identifiant. */
    protected UUID calculer(String alias, String perimetre, LocalDate debut, Integer semaines) throws Exception {
        Resp r = lancer(perimetre, debut, semaines, planifAdmin());
        if (r.status() != 202) throw new AssertionError("Lancement refusé : " + r.status() + " " + r.raw());
        UUID id = UUID.fromString(corps(r).path("id").asString());
        aliasNommes.put(id.toString(), "<" + alias + ">");
        attendreLaFin(id);
        return id;
    }

    protected void attendreLaFin(UUID run) throws Exception {
        long limite = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < limite) {
            String statut = jdbc.queryForObject("SELECT statut FROM planification_optimisation WHERE id = ?", String.class, run);
            if (!"EN_COURS".equals(statut)) {
                Thread.sleep(300); // calendrier figé après l'enregistrement du résultat
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Le calcul " + run + " ne se termine pas");
    }

    protected Resp appliquer(UUID run, RequestPostProcessor who) throws Exception {
        return doPost(OPTIMISATIONS + "/" + run + "/application", "", who);
    }

    protected Resp consulter(UUID run) throws Exception {
        return doGet(OPTIMISATIONS + "/" + run, adminA());
    }

    // ------------------------------------------------------------------ projections lisibles

    /**
     * Projection d'une exécution : statut, périmètre, paramètres, score, indicateurs, déplacements, vacations, temporaires...
     * (sans les listes de salles et de créneaux, ni les horodatages).
     */
    protected Map<String, Object> projectionDeLExecution(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        if (r.status() / 100 != 2) {
            m.put("corps", statutEtCode(r));
            return m;
        }
        JsonNode run = corps(r);
        m.put("statutExecution", run.path("statut").asString());
        m.put("appliquee", !run.path("appliqueLe").isNull());
        m.put("lancePar", run.path("lancePar").asString(null));
        m.put("phase", run.path("phase").asString(null));
        m.put("score", run.path("score").asString(null));
        m.put("erreur", run.path("erreur").asString(null));
        JsonNode p = run.path("parametres");
        Map<String, Object> parametres = new LinkedHashMap<>();
        p.properties().forEach(e -> parametres.put(e.getKey(), e.getValue().isString() ? e.getValue().asString()
                : e.getValue().isNumber() ? e.getValue().asInt() : e.getValue().toString()));
        m.put("parametres", parametres);
        m.put("empreinteLongueur", run.path("empreinte").asString("").length());
        JsonNode resultat = run.path("resultat");
        if (resultat.isNull() || resultat.isMissingNode()) {
            m.put("resultat", null);
            m.put("resume", run.path("resume").isNull() ? null : mapper.convertValue(run.path("resume"), Object.class));
            return m;
        }
        m.put("indicateursAvant", mapper.convertValue(resultat.path("avant"), Object.class));
        m.put("indicateursApres", mapper.convertValue(resultat.path("apres"), Object.class));
        for (String liste : List.of("deplacements", "nonPlaces", "vacations", "manques", "temporaires", "seancesSansSolution")) {
            m.put(liste, mapper.convertValue(resultat.path(liste), Object.class));
        }
        return m;
    }

    // ------------------------------------------------------------------ observation de la base

    protected void etatDesPlacesDesPatients(String name) {
        sql(name, "SELECT code_patient, salle_id, position_id, generateur_id, jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, "
                + "jour_jeudi, jour_vendredi, jour_samedi FROM patients WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY code_patient");
    }

    protected void etatDesDeplacementsTemporaires(String name) {
        sql(name, "SELECT center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id, motif FROM deplacement_temporaire "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, date_seance, patient_id");
    }

    protected void etatDesExecutions(String name) {
        sql(name, "SELECT center_id, statut, perimetre, lance_par, phase, (applique_le IS NOT NULL) AS appliquee, erreur, "
                + "LENGTH(empreinte) AS empreinte_longueur FROM planification_optimisation WHERE center_id IN ('" + CENTRE_A + "','"
                + CENTRE_B + "') ORDER BY center_id, perimetre, cree_le, id");
    }

    /** Intervention GMAO sur un équipement (lue par l'optimisation comme indisponibilité si planifiée ou en cours). */
    protected void intervention(UUID centre, UUID equipement, String statut, LocalDate debut, LocalDate fin) {
        java.time.OffsetDateTime d = debut.atTime(8, 0).atOffset(java.time.ZoneOffset.UTC);
        java.time.OffsetDateTime f = fin == null ? null : fin.atTime(17, 0).atOffset(java.time.ZoneOffset.UTC);
        java.time.OffsetDateTime creation = java.time.OffsetDateTime.of(2026, 1, 5, 9, 0, 0, 0, java.time.ZoneOffset.UTC);
        jdbc.update("INSERT INTO gmao_interventions (id, equipement_id, centre_id, type, statut, date_debut, date_fin, description, "
                        + "date_creation, cree_par) VALUES (?, ?, ?, 'PREVENTIVE', ?, ?, ?, 'Maintenance', ?, ?)",
                UUID.randomUUID(), equipement, centre, statut, d, f, creation, U_GMAO);
    }

    /** L'empreinte (SHA-256 des données lues, dates du jour comprises) change chaque jour : elle est masquée dans les goldens. */
    protected Resp sansEmpreinte(Resp r) {
        String brut = r.raw() == null ? null : r.raw().replaceAll("\"empreinte\":\"[0-9a-f]{64}\"", "\"empreinte\":\"<EMPREINTE>\"");
        return new Resp(r.status(), r.contentType(), brut, r.cacheControl());
    }

    /** Écriture ou lecture d'une exécution, rendue sans empreinte ni date réelle. */
    protected void execution(String name, Resp r) {
        goldenSimple(name, new Resp(sansEmpreinte(r).status(), sansEmpreinte(r).contentType(), dates(sansEmpreinte(r).raw()),
                r.cacheControl()));
    }

    /** Remet à zéro toutes les données des tests d'optimisation (entre deux cas d'un même test). */
    protected void reinitialiser() throws Exception {
        attendreLaFinDeTousLesCalculs();
        cleanupPlanification();
        nettoyerPlacement();
        nettoyerRoulement();
        nettoyerSemaine();
        nettoyerInfirmiers();
        nettoyerOptimisation();
        assurerLesCentres();
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom))
                .ifPresent(org.springframework.cache.Cache::clear));
    }

    /** Attend que {@code n} bilans de nuit aient été notifiés et que plus aucun calcul ne soit en cours. */
    protected void attendreLesBilans(int n) throws Exception {
        long limite = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < limite) {
            Integer bilans = jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE type = 'REPLANIFICATION_NOCTURNE'",
                    Integer.class);
            if (bilans != null && bilans >= n) {
                attendreLaFinDeTousLesCalculs();
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Les bilans de la replanification de nuit n'arrivent pas");
    }

    /** Exécution terminée écrite à la main (historique, calendrier absent, échec...). */
    protected UUID executionEcrite(UUID centre, String alias, String statut, String perimetre, int minutesDAge, String erreur) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p8-execution-" + alias).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        String parametres = "{\"perimetre\":\"" + perimetre + "\",\"debutSemaine\":\"" + semaine(1, JourSemaine.DIMANCHE)
                + "\",\"nbSemaines\":1,\"dureeMaxSecondes\":2,\"stabilite\":5,\"objectif\":\"EQUITE\",\"maxVacationsParJour\":2,"
                + "\"maxVacationsParSemaine\":6,\"heuresParVacation\":5,\"heuresHebdoTempsPlein\":40,\"reposHebdoMin\":1}";
        jdbc.update("INSERT INTO planification_optimisation (id, center_id, statut, perimetre, parametres, cree_le, empreinte, erreur) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, centre, statut, perimetre, parametres,
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(60L - minutesDAge)),
                "a".repeat(64), erreur);
        return id;
    }

    protected List<String> joursDe(JsonNode jours) {
        List<String> l = new ArrayList<>();
        jours.forEach(j -> l.add(j.asString()));
        return l;
    }

    /** Nomme les jours des semaines {@code de} à {@code a} (pour les réponses qui en contiennent). */
    protected void nommerLesSemaines(int de, int a) {
        for (int n = de; n <= a; n++) semaine(n);
    }

    protected LocalDate dimancheDeLaSemaine(int n) {
        return semaine(n, JourSemaine.DIMANCHE);
    }
}
