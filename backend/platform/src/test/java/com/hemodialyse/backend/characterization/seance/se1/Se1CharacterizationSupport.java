package com.hemodialyse.backend.characterization.seance.se1;

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
 * Harnais de la slice SE1 (création de séance : {@code POST /api/v1/seances}, création par scan {@code POST /api/v1/seances/scan},
 * consultation {@code GET /api/v1/seances/{id}} et liste {@code GET /api/v1/seances}), au-dessus des harnais de Planification.
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter} + {@code CenterAccessGuard},
 * H2 {@code test}), état SQL de la table REELLE {@code seances}, diffusions temps réel (courtier de messages), audit. Aucun mock du
 * code caractérisé, aucun code de production modifié (aucun seam).
 * <p>
 * Dates : explicites FIXES {@code <D+n>} pour les créations avec date ; la création sans date prend le jour du centre
 * ({@code Africa/Algiers}, {@code <J>}) et le scan prend le jour de la JVM ({@code LocalDate.now()}, aussi nommé {@code <J>}) : voir
 * KB-SEA-01. Garde de minuit (JVM et centre). Identifiants : alias nommés ou {@code <UUID_GENERE_n>} ; horodatages masqués.
 * DONNEES FICTIVES uniquement.
 */
public abstract class Se1CharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String SEANCES_API = "/api/v1/seances";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test (ex. {@code creation}). */
    protected abstract String dossier();

    @Override
    protected final String theme() {
        return "../seance/se1/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpSe1() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerSe1();
    }

    @AfterEach
    protected void apresSe1() {
        nettoyerSe1();
    }

    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM volet_paramedical WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM center_closure_day WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
        diffusions.clear();
    }

    private static void attendreSiProcheDeMinuitDeLaJvm() throws InterruptedException {
        ZoneId zone = ZoneId.systemDefault();
        Instant minuit = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Duration reste = Duration.between(Instant.now(), minuit);
        if (reste.compareTo(Duration.ofMinutes(2)) < 0) {
            Thread.sleep(reste.toMillis() + 2000);
        }
    }

    // ------------------------------------------------------------------ dates

    protected LocalDate fixe(int n) {
        return alias(REPERE.plusDays(n), "<D" + (n < 0 ? n : "+" + n) + ">");
    }

    /** Le jour du centre ({@code <J>}) ; le jour de la JVM porte le même nom (voir KB-SEA-01). */
    protected LocalDate aujourdhuiNomme() {
        alias(LocalDate.now(), "<J>");
        return jour(0);
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("se1-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre, programmé tous les jours (colonnes {@code jour_*} à vrai), état PERMANENT. */
    protected UUID patientSe1(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "etat_patient, jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi, created_at) "
                        + "VALUES (?, ?, ?, ?, 'Test', 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, ?)",
                id, centre, "SE1-" + alias, "Patient " + alias, java.sql.Date.valueOf(REPERE.minusDays(400)), "ASS-SE1-" + alias, CREATION_FIXE);
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
        return id;
    }

    /** Patient couvert : PEC validée 2020-01-01 à 2099-12-31 avec forfait, attestation de droits ouverte depuis 2020. */
    protected UUID patientCouvert(UUID centre, String alias) {
        UUID patient = patientSe1(centre, alias);
        pecSe1(centre, patient, "VALIDEE", "2020-01-01", "2099-12-31", "2020-01-01", "2099-12-31", true);
        attestation(centre, patient, "2020-01-01", null);
        return patient;
    }

    protected void pecSe1(UUID centre, UUID patient, String statut, String debutDemande, String finDemande, String debutEffectif,
                          String finEffectif, boolean avecForfait) {
        UUID forfait = avecForfait ? forfaitSe1(centre) : null;
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, date_debut_effectif, "
                        + "date_fin_effectif, forfait_demande_id, forfait_effectif_id, statut, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, patient, date(debutDemande), date(finDemande), date(debutEffectif), date(finEffectif), forfait,
                forfait, statut, CREATION_FIXE);
    }

    private UUID forfaitSe1(UUID centre) {
        UUID id = idStable("forfait", centre.toString());
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM forfait WHERE id = ?", Integer.class, id);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'FORFAIT_SE1', 'Forfait fictif', 100.00)", id, centre);
        }
        return id;
    }

    /** {@code date_fin} est NOT NULL en base : une attestation « ouverte » (fin nulle) est posée jusqu'au 2099-12-31. */
    protected void attestation(UUID centre, UUID patient, String debut, String fin) {
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), patient, centre, date(debut), date(fin == null ? "2099-12-31" : fin), CREATION_FIXE);
    }

    /** Séance posée en base avec le statut donné (y compris un statut inconnu du domaine). */
    protected UUID seanceEnBase(UUID centre, UUID patient, String alias, LocalDate jour, String statut) {
        UUID id = idStable("seance", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) VALUES (?,?,?,?,?,?,FALSE)",
                id, patient, centre, java.sql.Date.valueOf(jour), statut, CREATION_FIXE);
        return id;
    }

    protected void joursDeDialyse(UUID patient, boolean oui) {
        jdbc.update("UPDATE patients SET jour_dimanche = ?, jour_lundi = ?, jour_mardi = ?, jour_mercredi = ?, jour_jeudi = ?, "
                + "jour_vendredi = ?, jour_samedi = ? WHERE id = ?", oui, oui, oui, oui, oui, oui, oui, patient);
    }

    protected void fermerLeCentre(UUID centre, LocalDate jour) {
        fermetureExceptionnelle(centre, jour, "Fermeture fictive");
    }

    private static java.sql.Date date(String d) {
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

    protected String detailApi(UUID seance, UUID centre) {
        return SEANCES_API + "/" + seance + "?centerId=" + centre;
    }

    protected RequestPostProcessor infirmierA() {
        return as(CENTRE_A, "INFIRMIER");
    }

    protected RequestPostProcessor secretaireA() {
        return as(CENTRE_A, "SECRETAIRE");
    }

    protected RequestPostProcessor medecinA() {
        return as(CENTRE_A, "MEDECIN");
    }

    protected RequestPostProcessor infirmierB() {
        return as(CENTRE_B, "INFIRMIER");
    }

    protected Map<String, RequestPostProcessor> profilsSe1() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

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
            return status + " | " + raw;
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

    /** Séances des deux centres de test ; horodatages remplacés par « fixe » (harnais), « renseigne » ou « null ». */
    protected List<Map<String, Object>> etatDesSeances() {
        return lignes("SELECT s.center_id, s.patient_id, s.date_seance, s.statut, s.hors_planning, s.motif_hors_planning, "
                + "s.precision_hors_planning, s.signed_infirmier_by, s.signed_medecin_by, s.facture_id, s.forfait_override_id, "
                + "s.salle_id, s.creneau_id, s.generateur_id, "
                + "(CASE WHEN s.created_at IS NULL THEN 'null' WHEN s.created_at < TIMESTAMP WITH TIME ZONE '2026-01-02 10:00:00+00:00' "
                + "THEN 'fixe' ELSE 'creation par l application' END) AS creation, "
                + "(CASE WHEN s.validated_at IS NULL THEN 'null' ELSE 'renseigne' END) AS validation, "
                + "(CASE WHEN s.signed_infirmier_at IS NULL THEN 'null' ELSE 'renseigne' END) AS signature_infirmier, "
                + "(CASE WHEN s.signed_medecin_at IS NULL THEN 'null' ELSE 'renseigne' END) AS signature_medecin "
                + "FROM seances s WHERE s.center_id IN " + CENTRES
                + " ORDER BY s.center_id, s.patient_id, s.date_seance, s.created_at, s.statut");
    }

    protected int nombreDeSeances(UUID centre) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ?", Integer.class, centre);
    }

    /** Empreinte brute de la table des séances : dit si une requête a écrit quelque chose. */
    protected String empreinteDesSeances() {
        return jdbc.queryForList("SELECT * FROM seances WHERE center_id IN " + CENTRES + " ORDER BY id").toString();
    }

    /** Diffusions temps réel reçues depuis le début du scénario : type, cibles, charge sans identifiant volatil. */
    protected List<Map<String, Object>> diffusionsLues() {
        List<Map<String, Object>> sortie = new ArrayList<>();
        for (Map<String, Object> d : diffusions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("destination", d.get("destination"));
            Object message = d.get("message");
            if (message instanceof JsonNode n) {
                Map<String, Object> corps = new LinkedHashMap<>();
                corps.put("type", n.path("type").asString());
                corps.put("payload", n.path("payload"));
                m.put("message", corps);
            } else {
                m.put("message", String.valueOf(message));
            }
            sortie.add(m);
        }
        return sortie;
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
