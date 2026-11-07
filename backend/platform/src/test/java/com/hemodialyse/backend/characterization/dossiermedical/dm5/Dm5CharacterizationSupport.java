package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

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
 * Harnais de la slice DM5 (antécédents, allergies, abords vasculaires), au-dessus des harnais de Planification (décor, dates nommées,
 * diffusions, audit).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL
 * des tables REELLES {@code antecedents_medicaux}, {@code allergies_patient} et {@code abords_vasculaires} (et non les schémas
 * parallèles gelés du module dossier-medical, ADR 0072), audit, diffusions, notifications durables, export FHIR et lecteur de la
 * Planification (observés sans les modifier). Aucun mock du code caractérisé, aucun code de production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », diagnostics et substances inventés ou codes génériques),
 * aucun dossier réel. Dates : les dates explicites sont FIXES et nommées {@code <D+n>}. Seules la date par défaut (allergie) et la
 * limite « futur » lisent {@code LocalDate.now()} au fuseau de la JVM (sans horloge injectable) : dates {@code <J>} / {@code <J+1>}
 * nommées, et une garde attend le passage de minuit (fuseau de la JVM) si l'on en est à moins de deux minutes. Aucun scénario ne
 * dépend du jour UTC ni du jour du centre.
 */
public abstract class Dm5CharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";
    private static final String CREATION = "(CASE WHEN %1$s.created_at = TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00:00' "
            + "THEN 'fixe' ELSE 'recent' END) AS creation";
    private static final String MODIFICATION = "(CASE WHEN %1$s.updated_at > %1$s.created_at THEN 'modifiee' "
            + "ELSE 'jamais modifiee' END) AS modification";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test (ex. {@code antecedents/enregistrement}). */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return "../dossiermedical/dm5/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm5() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm5();
    }

    @AfterEach
    protected void apresDm5() {
        nettoyerDm5();
    }

    protected void nettoyerDm5() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM antecedents_medicaux WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM allergies_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM abords_vasculaires WHERE center_id = ?", centre);
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
        return UUID.nameUUIDFromBytes(("dm5-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}). */
    protected UUID patientDm5(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM5-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(REPERE.minusDays(400)),
                "ASS-DM5-" + alias, CREATION_FIXE);
        return id;
    }

    /** Antécédent posé en base (créé et modifié à l'instant fixe) ; l'identifiant est nommé {@code <alias>}. */
    protected UUID antecedentEnBase(UUID centre, UUID patient, String alias, String type, String systeme, String code,
                                    String libelle, LocalDate debut, LocalDate fin, String statut) {
        UUID id = idStable("antecedent", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO antecedents_medicaux (id, patient_id, center_id, type_antecedent, diagnostic_code_system, diagnostic_code, "
                        + "diagnostic_code_display, libelle_libre, date_debut, date_fin, statut_clinique, severite, note, created_at, "
                        + "updated_at) VALUES (?,?,?,?,?,?,NULL,?,?,?,?,NULL,NULL,?,?)", id, patient, centre, type, systeme, code, libelle,
                java.sql.Date.valueOf(debut), fin == null ? null : java.sql.Date.valueOf(fin), statut, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    /** Allergie posée en base (créée et modifiée à l'instant fixe). */
    protected UUID allergieEnBase(UUID centre, UUID patient, String alias, String systeme, String code, String categorie,
                                  String criticite, String reaction, String manifestations, LocalDate date, String statut) {
        UUID id = idStable("allergie", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO allergies_patient (id, patient_id, center_id, substance_code_system, substance_code, "
                        + "substance_code_display, categorie, criticite, type_reaction, manifestations, date_constatation, "
                        + "statut_verification, created_at, updated_at) VALUES (?,?,?,?,?,NULL,?,?,?,?,?,?,?,?)", id, patient, centre,
                systeme, code, categorie, criticite, reaction, manifestations, java.sql.Date.valueOf(date), statut, CREATION_FIXE,
                CREATION_FIXE);
        return id;
    }

    /** Abord vasculaire posé en base (créé à l'instant fixe). */
    protected UUID abordEnBase(UUID centre, UUID patient, String alias, String type, String cote, String localisation,
                               LocalDate creation, LocalDate fin, boolean actif) {
        UUID id = idStable("abord", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO abords_vasculaires (id, patient_id, center_id, type_abord, cote, localisation, date_creation, date_fin, "
                        + "actif, complications, created_at) VALUES (?,?,?,?,?,?,?,?,?,NULL,?)", id, patient, centre, type, cote,
                localisation, creation == null ? null : java.sql.Date.valueOf(creation), fin == null ? null : java.sql.Date.valueOf(fin),
                actif, CREATION_FIXE);
        return id;
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

    protected String antecedentsApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/antecedents";
    }

    protected String antecedentApi(UUID patient, UUID antecedent) {
        return antecedentsApi(patient) + "/" + antecedent;
    }

    protected String allergiesApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/allergies";
    }

    protected String allergieApi(UUID patient, UUID allergie) {
        return allergiesApi(patient) + "/" + allergie;
    }

    protected String abordsApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/abords-vasculaires";
    }

    protected String abordApi(UUID patient, UUID abord) {
        return abordsApi(patient) + "/" + abord;
    }

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, RequestPostProcessor> profilsDm5() {
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

    /** « 200 ok » pour un succès (le corps est observé ailleurs), sinon le statut, le code et le détail du refus. */
    protected String statutOuRefus(Resp r) {
        return r.status() == 200 ? "200 ok" : refus(r);
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

    /** État de la table réelle des antécédents ; création masquée (fixe ou récente), modification dite. */
    protected List<Map<String, Object>> etatDesAntecedents() {
        return lignes("SELECT a.center_id, a.patient_id, a.type_antecedent, a.diagnostic_code_system, a.diagnostic_code, "
                + "a.diagnostic_code_display, a.libelle_libre, a.date_debut, a.date_fin, a.statut_clinique, a.severite, a.note, a.id, "
                + CREATION.formatted("a") + ", " + MODIFICATION.formatted("a") + " FROM antecedents_medicaux a WHERE a.center_id IN "
                + CENTRES + " ORDER BY a.center_id, a.patient_id, a.date_debut, a.diagnostic_code, a.libelle_libre, a.note, "
                + "a.statut_clinique");
    }

    protected List<Map<String, Object>> etatDesAllergies() {
        return lignes("SELECT a.center_id, a.patient_id, a.substance_code_system, a.substance_code, a.substance_code_display, "
                + "a.categorie, a.criticite, a.type_reaction, a.manifestations, a.date_constatation, a.statut_verification, a.id, "
                + CREATION.formatted("a") + ", " + MODIFICATION.formatted("a") + " FROM allergies_patient a WHERE a.center_id IN "
                + CENTRES + " ORDER BY a.center_id, a.patient_id, a.date_constatation, a.substance_code, a.substance_code_system, "
                + "a.categorie");
    }

    protected List<Map<String, Object>> etatDesAbords() {
        return lignes("SELECT a.center_id, a.patient_id, a.type_abord, a.cote, a.localisation, a.date_creation, a.date_fin, a.actif, "
                + "a.complications, a.id, " + CREATION.formatted("a") + " FROM abords_vasculaires a WHERE a.center_id IN " + CENTRES
                + " ORDER BY a.center_id, a.patient_id, a.date_creation, a.type_abord, a.cote, a.localisation, a.actif");
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
        return lignes("SELECT action_code, entity_type, entity_id, http_method, route_template, status_code, COUNT(*) AS nombre "
                + "FROM audit_log WHERE center_id IN " + CENTRES + " GROUP BY action_code, entity_type, entity_id, http_method, "
                + "route_template, status_code ORDER BY route_template, http_method, status_code, entity_id");
    }

    protected List<Map<String, Object>> notificationsDurables() {
        return lignes("SELECT type, target_roles, payload FROM notification_evenement WHERE center_id IN " + CENTRES
                + " ORDER BY type, payload");
    }
}
