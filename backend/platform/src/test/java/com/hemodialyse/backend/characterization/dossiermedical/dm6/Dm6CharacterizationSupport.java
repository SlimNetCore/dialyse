package com.hemodialyse.backend.characterization.dossiermedical.dm6;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice DM6 (biologie : observations LOINC et résultats d'analyses à colonnes fixes), au-dessus des harnais de
 * Planification (décor, dates nommées, diffusions, audit).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL
 * des tables REELLES {@code observations_biologiques} et {@code resultats_analyses}, audit, diffusions, notifications durables, et
 * lecteurs observés sans les modifier (suivi de l'anémie, statistiques médicales, KDIGO, export FHIR). Aucun mock du code caractérisé,
 * aucun code de production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », valeurs rondes, aucun dossier réel). Dates : les dates
 * explicites sont FIXES et nommées {@code <D+n>}. Seule la limite « pas dans le futur » d'une observation lit {@code LocalDate.now()} au
 * fuseau de la JVM (sans horloge injectable) : dates {@code <J>} / {@code <J+1>} nommées, et une garde attend le passage de minuit si l'on
 * en est à moins de deux minutes. Aucun scénario ne dépend du jour UTC ni du jour du centre.
 */
public abstract class Dm6CharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    protected static final UUID MEDECIN_FICTIF = UUID.fromString("99997800-0000-0000-0000-0000000000d6");
    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";
    private static final String CREATION = "(CASE WHEN %1$s.created_at = TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00:00' "
            + "THEN 'fixe' ELSE 'recent' END) AS creation";
    private static final String MODIFICATION = "(CASE WHEN %1$s.updated_at > %1$s.created_at THEN 'modifiee' "
            + "ELSE 'jamais modifiee' END) AS modification";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test (ex. {@code observations/enregistrement}). */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return "../dossiermedical/dm6/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm6() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm6();
    }

    @AfterEach
    protected void apresDm6() {
        nettoyerDm6();
    }

    protected void nettoyerDm6() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM observations_biologiques WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM lignes_demande_examen WHERE demande_id IN (SELECT id FROM demandes_examen WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM demandes_examen WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
    }

    /** L'application lit le jour au fuseau de la JVM : on évite une bascule du jour pendant un test. */
    private static void attendreSiProcheDeMinuitDeLaJvm() throws InterruptedException {
        ZoneId zone = ZoneId.systemDefault();
        Instant minuit = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            Thread.sleep(reste.toMillis() + 2000);
        }
    }

    // ------------------------------------------------------------------ dates

    /** Date fixe nommée {@code <D+n>} (n jours après le repère) : indépendante de l'horloge. */
    protected LocalDate fixe(int n) {
        return alias(REPERE.plusDays(n), "<D" + (n < 0 ? n : "+" + n) + ">");
    }

    /** Jour de la JVM décalé de n jours, nommé {@code <J>}, {@code <J-3>}, {@code <J+1>}. */
    protected LocalDate relatif(int n) {
        return alias(LocalDate.now().plusDays(n), n == 0 ? "<J>" : n < 0 ? "<J" + n + ">" : "<J+" + n + ">");
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("dm6-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}). */
    protected UUID patientDm6(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM6-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(REPERE.minusDays(400)),
                "ASS-DM6-" + alias, CREATION_FIXE);
        return id;
    }

    /** Observation posée en base (créée et modifiée à l'instant fixe) ; l'identifiant est nommé {@code <alias>}. */
    protected UUID observationEnBase(UUID centre, UUID patient, String alias, UUID demande, String code, String display, String valeurNum,
                                     String unite, String valeurTexte, LocalDate date, String statut, String source) {
        UUID id = idStable("observation", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO observations_biologiques (id, patient_id, center_id, demande_examen_id, analyte_code_system, analyte_code, "
                        + "analyte_code_display, valeur_num, unite, valeur_texte, date_prelevement, statut, source, created_at, updated_at) "
                        + "VALUES (?,?,?,?,'LOINC',?,?,?,?,?,?,?,?,?,?)", id, patient, centre, demande, code, display,
                valeurNum == null ? null : new BigDecimal(valeurNum), unite, valeurTexte, java.sql.Date.valueOf(date), statut, source,
                CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    /** Observation ordinaire (718-7, valeur numérique) posée en base. */
    protected UUID observationSimple(UUID centre, UUID patient, String alias, LocalDate date) {
        return observationEnBase(centre, patient, alias, null, "718-7", "Hemoglobine fictive", "11.5", "g/dL", null, date, "FINAL", "SAISIE_DIRECTE");
    }

    /**
     * Bilan d'analyses posé en base (créé et modifié à l'instant fixe) : date, puis paires colonne/valeur
     * (ex. {@code "hb_g_dl", "11.5"}).
     */
    protected UUID analyseEnBase(UUID centre, UUID patient, String alias, LocalDate date, String... colonnesEtValeurs) {
        UUID id = idStable("analyse", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        StringBuilder cols = new StringBuilder("id, patient_id, center_id, date_prelevement, created_at, updated_at");
        StringBuilder marques = new StringBuilder("?,?,?,?,?,?");
        List<Object> args = new ArrayList<>(List.of(id, patient, centre, java.sql.Date.valueOf(date), CREATION_FIXE, CREATION_FIXE));
        for (int i = 0; i < colonnesEtValeurs.length; i += 2) {
            cols.append(", ").append(colonnesEtValeurs[i]);
            marques.append(",?");
            args.add("plaquettes".equals(colonnesEtValeurs[i]) ? (Object) Integer.valueOf(colonnesEtValeurs[i + 1])
                    : new BigDecimal(colonnesEtValeurs[i + 1]));
        }
        jdbc.update("INSERT INTO resultats_analyses (" + cols + ") VALUES (" + marques + ")", args.toArray());
        return id;
    }

    /** Demande d'examen posée en base (le seul lien du legacy avec une observation est cette référence souple). */
    protected UUID demandeEnBase(UUID centre, UUID patient, String alias, String statut) {
        UUID id = idStable("demande", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, "
                        + "statut, conclusion, created_at, updated_at) VALUES (?,?,?,?,?,'BIOLOGIE',FALSE,?,?,NULL,?,?)", id, patient, centre,
                MEDECIN_FICTIF, java.sql.Date.valueOf(fixe(1)), "Bilan fictif " + alias, statut, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected String statutDeLaDemande(UUID demande) {
        return jdbc.queryForObject("SELECT statut FROM demandes_examen WHERE id = ?", String.class, demande);
    }

    // ------------------------------------------------------------------ corps de requête

    /** Corps JSON à partir de paires nom/valeur ; une valeur nulle est omise (champ absent). */
    protected String corps(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            if (paires[i + 1] != null) m.put((String) paires[i], paires[i + 1] instanceof UUID || paires[i + 1] instanceof LocalDate
                    ? paires[i + 1].toString() : paires[i + 1]);
        }
        return mapper.writeValueAsString(m);
    }

    protected String observationsApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/observations";
    }

    protected String observationApi(UUID patient, UUID observation) {
        return observationsApi(patient) + "/" + observation;
    }

    protected String observationsDeLaDemandeApi(UUID patient, UUID demande) {
        return PATIENTS_API + "/" + patient + "/demandes-examen/" + demande + "/observations";
    }

    protected String analysesApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/analyses";
    }

    protected String analyseApi(UUID patient, UUID analyse) {
        return analysesApi(patient) + "/" + analyse;
    }

    protected static final String RECHERCHE_ANALYSES = PATIENTS_API + "/analyses/search";

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, RequestPostProcessor> profilsDm6() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

    protected String idDe(Resp r) {
        return corps(r).path("id").asString();
    }

    protected RequestPostProcessor medecinA() {
        return as(CENTRE_A, "MEDECIN");
    }

    /** Statut, code et détail (sauf 5xx : le détail technique n'est pas un comportement métier). */
    protected String refus(Resp r) {
        if (r.status() == 200) return "200 ok";
        if (r.raw() == null || r.raw().isBlank()) return String.valueOf(r.status());
        try {
            JsonNode c = mapper.readTree(r.raw());
            String code = c.has("code") ? " " + c.path("code").asString() : "";
            String detail = r.status() < 500 && c.has("detail") ? " | " + c.path("detail").asString() : "";
            return r.status() + code + detail;
        } catch (RuntimeException e) {
            return String.valueOf(r.status());
        }
    }

    /** Une réponse lisible : statut et corps (pour une erreur 5xx, le seul code : le détail technique n'est pas un comportement métier). */
    protected Map<String, Object> reponse(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.raw() == null || r.raw().isBlank()) return m;
        try {
            JsonNode c = mapper.readTree(r.raw());
            if (r.status() >= 500) {
                m.put("code", c.path("code").asString());
            } else {
                m.put("corps", c);
            }
        } catch (RuntimeException e) {
            m.put("texte", r.raw());
        }
        return m;
    }

    /** Une page : statut, métadonnées de pagination et, pour chaque élément, les champs demandés joints par « | ». */
    protected Map<String, Object> page(Resp r, String... champs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        if (r.status() != 200) {
            m.put("refus", refus(r));
            return m;
        }
        JsonNode c = corps(r);
        m.put("total", c.path("total").asLong());
        m.put("page", c.path("page").asInt());
        m.put("size", c.path("size").asInt());
        List<String> items = new ArrayList<>();
        c.path("items").forEach(i -> items.add(resume(i, champs)));
        m.put("items", items);
        return m;
    }

    /** Les champs demandés d'un élément joints par « | » (absent ou nul : {@code -}). */
    protected String resume(JsonNode n, String... champs) {
        List<String> v = new ArrayList<>();
        for (String champ : champs) {
            JsonNode f = n.path(champ);
            v.add(f.isMissingNode() || f.isNull() ? "-" : f.asString());
        }
        return String.join(" | ", v);
    }

    /** Une réponse d'écriture réduite aux champs demandés si 200, sinon le refus. */
    protected String resume(Resp r, String... champs) {
        return r.status() == 200 ? "200 " + resume(corps(r), champs) : refus(r);
    }

    // ------------------------------------------------------------------ observation

    /** Lignes d'une requête SQL, colonnes triées par nom, valeurs en texte (pour les arbres de goldens). */
    protected List<Map<String, Object>> lignes(String requete, Object... args) {
        List<Map<String, Object>> sortie = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(requete, args)) {
            Map<String, Object> m = new LinkedHashMap<>();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> m.put(e.getKey().toLowerCase(), e.getValue() == null ? null : String.valueOf(e.getValue())));
            sortie.add(m);
        }
        return sortie;
    }

    /** État de la table réelle des observations ; création masquée (fixe ou récente), modification dite. */
    protected List<Map<String, Object>> etatDesObservations() {
        return lignes("SELECT o.center_id, o.patient_id, o.demande_examen_id, o.analyte_code_system, o.analyte_code, o.analyte_code_display, "
                + "o.valeur_num, o.unite, o.valeur_texte, o.date_prelevement, o.statut, o.source, o.id, "
                + CREATION.formatted("o") + ", " + MODIFICATION.formatted("o") + " FROM observations_biologiques o WHERE o.center_id IN "
                + CENTRES + " ORDER BY o.center_id, o.patient_id, o.date_prelevement, o.analyte_code, o.valeur_num, o.valeur_texte, o.statut");
    }

    protected List<Map<String, Object>> etatDesAnalyses() {
        return lignes("SELECT a.center_id, a.patient_id, a.date_prelevement, a.hb_g_dl, a.ht_pct, a.plaquettes, a.ferritine_ng_ml, a.cstf_pct, "
                + "a.epo_endogene_mui_ml, a.uree_pre_mg_dl, a.uree_post_mg_dl, a.creatinine_mg_dl, a.kt_v_mensuel, a.phosphore_mg_dl, "
                + "a.calcium_mg_dl, a.pth_pg_ml, a.albumine_g_dl, a.proteines_g_dl, a.crp_mg_l, a.id, "
                + CREATION.formatted("a") + ", " + MODIFICATION.formatted("a") + " FROM resultats_analyses a WHERE a.center_id IN "
                + CENTRES + " ORDER BY a.center_id, a.patient_id, a.date_prelevement, a.hb_g_dl, a.creatinine_mg_dl, a.ferritine_ng_ml, a.id");
    }

    /** Empreinte brute d'une table : dit si une requête a écrit quelque chose. */
    protected String empreinte(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE center_id IN " + CENTRES + " ORDER BY id").toString();
    }

    protected int nombreDe(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    /** Le journal d'audit des centres de test (après vidage de l'écrivain asynchrone). */
    protected List<Map<String, Object>> journalDAudit() {
        viderLAudit();
        List<Map<String, Object>> lignes = new ArrayList<>(lignes("SELECT action_code, entity_type, entity_id, http_method, route_template, "
                + "status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id IN " + CENTRES + " GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code"));
        // Tri sur l'alias nommé de l'identifiant (les identifiants générés sont aléatoires : leur ordre brut n'est pas stable).
        lignes.sort(java.util.Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("route_template")))
                .thenComparing(l -> String.valueOf(l.get("http_method")))
                .thenComparing(l -> String.valueOf(l.get("status_code")))
                .thenComparing(l -> aliasNommes.getOrDefault(String.valueOf(l.get("entity_id")), String.valueOf(l.get("entity_id")))));
        return lignes;
    }

    protected List<Map<String, Object>> notificationsDurables() {
        return lignes("SELECT type, target_roles, payload FROM notification_evenement WHERE center_id IN " + CENTRES
                + " ORDER BY type, payload");
    }
}
