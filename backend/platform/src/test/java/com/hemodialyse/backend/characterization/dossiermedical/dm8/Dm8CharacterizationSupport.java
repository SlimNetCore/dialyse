package com.hemodialyse.backend.characterization.dossiermedical.dm8;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.mock.web.MockHttpServletResponse;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Harnais de la slice DM8 (lectures publiées du Dossier médical : dossier médical de base du patient, export FHIR, statistiques
 * médicales et exports, indicateurs médicaux de la Direction), au-dessus des harnais de Planification (décor, dates nommées, audit).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL des tables REELLES ({@code dossier_medical_patient}, lecteurs : antécédents, allergies, abords, analyses,
 * observations, ordonnances, prescriptions, sérologies, bilans pré-greffe), audit. Aucun mock du code caractérisé, aucun code de
 * production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », valeurs rondes). Dates explicites FIXES nommées
 * {@code <D+n>} ; le domaine lit {@code LocalDate.now()} pour la date par défaut d'une allergie (non utilisée ici) : dates {@code <J>}
 * avec garde de minuit.
 */
public abstract class Dm8CharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";
    private static final String CREATION = "(CASE WHEN %1$s.created_at < TIMESTAMP WITH TIME ZONE '2026-01-02 10:00:00+00:00' "
            + "THEN 'fixe' ELSE 'recent' END) AS creation";
    // Une ligne créée par l'application et modifiée dans la même requête a un écart created_at / updated_at de l'ordre de la
    // microseconde : la modification n'est donc dite que pour les lignes posées par le harnais.
    private static final String MODIFICATION = "(CASE WHEN %1$s.created_at > TIMESTAMP WITH TIME ZONE '2026-01-02 10:00:00+00:00' THEN 'ligne creee par l application' "
            + "WHEN %1$s.updated_at > %1$s.created_at THEN 'modifiee' ELSE 'jamais modifiee' END) AS modification";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test (ex. {@code dossier}). */
    protected abstract String dossier();

    @Override
    protected final String theme() {
        return "../dossiermedical/dm8/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm8() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm8();
    }

    @AfterEach
    protected void apresDm8() {
        nettoyerDm8();
    }

    protected void nettoyerDm8() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM lignes_ordonnance WHERE ordonnance_id IN (SELECT id FROM ordonnances WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ordonnances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prescriptions_medicales WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM observations_biologiques WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM antecedents_medicaux WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM allergies_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM abords_vasculaires WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM decisions_rcp WHERE bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bilans_pre_greffe WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM dossier_medical_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM volet_paramedical WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
    }

    private static void attendreSiProcheDeMinuitDeLaJvm() throws InterruptedException {
        ZoneId zone = ZoneId.systemDefault();
        Instant minuit = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            try {
                Thread.sleep(reste.toMillis() + 2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------ dates

    protected LocalDate fixe(int n) {
        return alias(REPERE.plusDays(n), "<D" + (n < 0 ? n : "+" + n) + ">");
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("dm8-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    protected UUID patientDm8(UUID centre, String alias) {
        return patientDm8(centre, alias, "M", null);
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}) ; sexe et date de naissance déclarés optionnels. */
    protected UUID patientDm8(UUID centre, String alias, String sexe, LocalDate naissance) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, "
                        + "numero_assurance, type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM8-" + alias, "Patient " + alias, "Test", sexe, naissance == null ? null : java.sql.Date.valueOf(naissance),
                java.sql.Date.valueOf(REPERE.minusDays(400)), "ASS-DM8-" + alias, CREATION_FIXE);
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
        return id;
    }

    protected UUID dossierEnBase(UUID centre, UUID patient, String alias, String nephropathie, LocalDate dialyse, String hepB, String hepC,
                                 String observation, String conclusion) {
        UUID id = idStable("dossier", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO dossier_medical_patient (id, patient_id, center_id, nephropathie_initiale, date_mise_en_dialyse, hepatite_b_statut, "
                        + "hepatite_c_statut, observation_globale, conclusion_medicale, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, patient,
                centre, nephropathie, date(dialyse), hepB, hepC, observation, conclusion, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected UUID antecedentEnBase(UUID centre, UUID patient, String alias, String systeme, String code, String affichage, String libelle,
                                    LocalDate debut, LocalDate fin, String statut, String severite, String note) {
        UUID id = idStable("antecedent", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO antecedents_medicaux (id, patient_id, center_id, type_antecedent, diagnostic_code_system, diagnostic_code, "
                        + "diagnostic_code_display, libelle_libre, date_debut, date_fin, statut_clinique, severite, note, created_at, "
                        + "updated_at) VALUES (?,?,?,'MEDICAL',?,?,?,?,?,?,?,?,?,?,?)", id, patient, centre, systeme, code, affichage, libelle,
                date(debut), date(fin), statut, severite, note, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected UUID allergieEnBase(UUID centre, UUID patient, String alias, String systeme, String code, String affichage, String categorie,
                                  String criticite, String manifestations, LocalDate date, String statut) {
        UUID id = idStable("allergie", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO allergies_patient (id, patient_id, center_id, substance_code_system, substance_code, "
                        + "substance_code_display, categorie, criticite, type_reaction, manifestations, date_constatation, "
                        + "statut_verification, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,'ALLERGIE',?,?,?,?,?)", id, patient, centre,
                systeme, code, affichage, categorie, criticite, manifestations, date(date), statut, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected UUID abordEnBase(UUID centre, UUID patient, String alias, String type, String cote, String localisation, LocalDate creation,
                               LocalDate fin, boolean actif, String complications) {
        UUID id = idStable("abord", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO abords_vasculaires (id, patient_id, center_id, type_abord, cote, localisation, date_creation, date_fin, "
                        + "actif, complications, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, patient, centre, type, cote, localisation,
                date(creation), date(fin), actif, complications, CREATION_FIXE);
        return id;
    }

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

    protected UUID observationEnBase(UUID centre, UUID patient, String alias, String systeme, String code, String affichage, String valeurNum,
                                     String unite, String valeurTexte, LocalDate date, String statut) {
        UUID id = idStable("observation", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO observations_biologiques (id, patient_id, center_id, demande_examen_id, analyte_code_system, analyte_code, "
                        + "analyte_code_display, valeur_num, unite, valeur_texte, date_prelevement, statut, source, created_at, updated_at) "
                        + "VALUES (?,?,?,NULL,?,?,?,?,?,?,?,?,'SAISIE_DIRECTE',?,?)", id, patient, centre, systeme, code, affichage,
                valeurNum == null ? null : new BigDecimal(valeurNum), unite, valeurTexte, java.sql.Date.valueOf(date), statut,
                CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected UUID ordonnanceEnBase(UUID centre, UUID patient, String alias, LocalDate date, String statut, String medecin, int lignes) {
        UUID id = idStable("ordonnance", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO ordonnances (id, patient_id, center_id, medecin_id, date_prescription, statut, numero, created_at, updated_at, "
                        + "signed_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, patient, centre, medecin, java.sql.Date.valueOf(date), statut,
                "SIGNEE".equals(statut) ? "ORD-" + alias : null, CREATION_FIXE, CREATION_FIXE, "SIGNEE".equals(statut) ? CREATION_FIXE : null);
        for (int i = 1; i <= lignes; i++) {
            UUID ligne = idStable("ligne", alias + "-" + i + "-" + centre);
            aliasNommes.put(ligne.toString(), "<" + alias + "_L" + i + ">");
            jdbc.update("INSERT INTO lignes_ordonnance (id, ordonnance_id, libelle, posologie, voie, duree_jours, quantite) VALUES (?,?,?,?,?,?,?)",
                    ligne, id, "Medicament fictif " + alias + " " + i, i == 1 ? "1 comprime par jour" : "A la demande", i == 1 ? "orale" : null,
                    i == 1 ? 30 : null, i == 1 ? 2 : null);
        }
        return id;
    }

    protected UUID prescriptionEnBase(UUID centre, UUID patient, String alias, LocalDate date, String epoDose, String voie, Integer frequence,
                                      String unite) {
        UUID id = idStable("prescription", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, epo_dose_ui, epo_voie, epo_frequence_valeur, "
                        + "epo_frequence_unite, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, patient, centre, java.sql.Date.valueOf(date),
                epoDose == null ? null : new BigDecimal(epoDose), voie, frequence, unite, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    protected void serologieEnBase(UUID centre, UUID patient, String alias, String marqueur, String resultat, LocalDate prelevement) {
        UUID id = idStable("serologie", alias + "-" + centre);
        jdbc.update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", id, centre, patient, marqueur, resultat, java.sql.Date.valueOf(prelevement), CREATION_FIXE, CREATION_FIXE);
    }

    protected void bilanEnBase(UUID centre, UUID patient, String alias, String statut, LocalDate greffe) {
        UUID id = idStable("bilan", alias + "-" + centre);
        jdbc.update("INSERT INTO bilans_pre_greffe (id, patient_id, center_id, statut, date_greffe, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                id, patient, centre, statut, date(greffe), CREATION_FIXE, CREATION_FIXE);
    }

    /** Séance du patient avec son volet paramédical (poids avant / après, TA) : lue par le PDF des statistiques. */
    protected void seanceAvecVolet(UUID centre, UUID patient, LocalDate jour, String poidsAvant, String poidsApres, String taAvant, String ufMl) {
        UUID seance = seance(centre, patient, jour, "TERMINEE", null, null, null);
        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg, poids_apres_kg, ta_avant, ultrafiltration_ml, created_at, "
                        + "updated_at) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), seance, centre, new BigDecimal(poidsAvant),
                new BigDecimal(poidsApres), taAvant, ufMl == null ? null : new BigDecimal(ufMl), CREATION_FIXE, CREATION_FIXE);
    }

    private static java.sql.Date date(LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    // ------------------------------------------------------------------ chemins et corps

    protected String corps(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            if (paires[i + 1] != null) m.put((String) paires[i], paires[i + 1] instanceof UUID || paires[i + 1] instanceof LocalDate
                    ? paires[i + 1].toString() : paires[i + 1]);
        }
        return mapper.writeValueAsString(m);
    }

    protected String dossierApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/dossier-medical";
    }

    protected String fhirApi(UUID patient) {
        return dossierApi(patient) + "/export-fhir";
    }

    protected String statsApi(UUID patient, String suite) {
        return PATIENTS_API + "/" + patient + "/stats/" + suite;
    }

    protected RequestPostProcessor medecinA() {
        return as(CENTRE_A, "MEDECIN");
    }

    protected RequestPostProcessor infirmierA() {
        return as(CENTRE_A, "INFIRMIER");
    }

    protected RequestPostProcessor adminA2() {
        return as(CENTRE_A, "ADMIN");
    }

    protected RequestPostProcessor medecinB() {
        return as(CENTRE_B, "MEDECIN");
    }

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, RequestPostProcessor> profilsDm8() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

    /** Regroupe les routes par issue (statut et code) : « 200 » -&gt; numéros des routes ; garde le golden lisible. */
    protected Map<String, String> regrouper(Map<String, String> parRoute) {
        Map<String, List<String>> groupes = new java.util.TreeMap<>();
        parRoute.forEach((route, issue) -> groupes.computeIfAbsent(issue, k -> new ArrayList<>()).add(route.substring(0, 2)));
        Map<String, String> sortie = new LinkedHashMap<>();
        groupes.forEach((issue, routes) -> sortie.put(issue, String.join(" ", routes)));
        return sortie;
    }

    protected String idDe(Resp r) {
        return corps(r).path("id").asString();
    }

    // ------------------------------------------------------------------ lecture des réponses

    /** Statut, code et détail (sauf 5xx : le détail technique n'est pas un comportement métier). */
    protected String refus(Resp r) {
        if (r.status() == 200) return "200 ok";
        return refusBrut(r.status(), r.raw());
    }

    protected String refusBrut(int status, String raw) {
        if (raw == null || raw.isBlank()) return String.valueOf(status);
        try {
            JsonNode c = mapper.readTree(raw);
            String code = c.has("code") ? " " + c.path("code").asString() : "";
            String detail = status < 500 && c.has("detail") ? " | " + c.path("detail").asString() : "";
            return status + code + detail;
        } catch (RuntimeException e) {
            return String.valueOf(status);
        }
    }

    /** Une réponse lisible : statut et corps (pour une erreur 5xx, le seul code). */
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

    protected String resume(JsonNode n, String... champs) {
        List<String> v = new ArrayList<>();
        for (String champ : champs) {
            JsonNode f = n.path(champ);
            v.add(f.isMissingNode() || f.isNull() ? "-" : f.asString());
        }
        return String.join(" | ", v);
    }

    protected String resume(Resp r, String... champs) {
        return r.status() == 200 ? "200 " + resume(corps(r), champs) : refus(r);
    }

    protected MockHttpServletResponse binaire(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse();
    }

    protected MockHttpServletResponse binairePost(String path, String corps, RequestPostProcessor who) throws Exception {
        return mvc.perform(post(java.net.URI.create(path)).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps).with(who))
                .andReturn().getResponse();
    }

    // ------------------------------------------------------------------ état SQL

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

    protected List<Map<String, Object>> etatDesDossiers() {
        return lignes("SELECT d.center_id, d.patient_id, d.nephropathie_initiale, d.date_mise_en_dialyse, d.hepatite_b_statut, d.hepatite_c_statut, "
                + "d.observation_globale, d.conclusion_medicale, d.id, " + CREATION.formatted("d") + ", " + MODIFICATION.formatted("d")
                + " FROM dossier_medical_patient d WHERE d.center_id IN " + CENTRES + " ORDER BY d.center_id, d.patient_id, d.id");
    }

    /** Empreinte brute des tables lues par les lecteurs DM8 : dit si une requête a écrit quelque chose. */
    protected String empreinteLecteurs() {
        StringBuilder sb = new StringBuilder();
        for (String table : List.of("dossier_medical_patient", "antecedents_medicaux", "allergies_patient", "abords_vasculaires", "resultats_analyses",
                "observations_biologiques", "ordonnances", "prescriptions_medicales", "serologies_patient", "bilans_pre_greffe", "patients")) {
            sb.append(jdbc.queryForList("SELECT * FROM " + table + " WHERE center_id IN " + CENTRES + " ORDER BY id").toString());
        }
        return sb.toString();
    }

    protected int nombreDe(String table, UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE center_id = ?", Integer.class, centre);
    }

    protected List<Map<String, Object>> journalDAudit() {
        viderLAudit();
        List<Map<String, Object>> lignes = new ArrayList<>(lignes("SELECT action_code, entity_type, entity_id, http_method, route_template, "
                + "status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id IN " + CENTRES + " GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code"));
        lignes.sort(java.util.Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("route_template")))
                .thenComparing(l -> String.valueOf(l.get("http_method")))
                .thenComparing(l -> String.valueOf(l.get("status_code")))
                .thenComparing(l -> aliasNommes.getOrDefault(String.valueOf(l.get("entity_id")), String.valueOf(l.get("entity_id")))));
        return lignes;
    }
}
