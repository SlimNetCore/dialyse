package com.hemodialyse.backend.characterization.dossiermedical.dm9;

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
import java.util.TreeSet;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Harnais de la slice DM9 (reliquat du legacy médical : lecteurs SQL de la Planification sur {@code serologies_patient} et
 * {@code abords_vasculaires}, statistiques médicales et exports, indicateurs médicaux de la Direction), au-dessus des harnais de
 * Planification (profils, requêtes, dates nommées, goldens). Les 38 scénarios DM8 ({@code dossiermedical/dm8}) restent le filet des
 * statistiques et de la Direction : ce harnais ne sert qu'aux TROUS relevés par l'audit {@code .ddd/safety-net/dossier-medical-dm9.md}.
 * <p>
 * Points d'observation : les BEANS publiés de la Planification ({@code RisquesInfectieux}, {@code ContraintesDesPatients}) appelés tels
 * quels (aucun mock, mêmes requêtes SQL que la production, H2 {@code test}) ; HTTP (MockMvc, contexte Spring complet, sécurité réelle +
 * {@code RoleScopeFilter} + {@code CenterAccessGuard}) pour les statistiques et la Direction ; état SQL des tables RÉELLES. Aucun seam,
 * aucun code de production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », valeurs rondes). Dates FIXES nommées {@code <D+n>}
 * (2026-01-01 + n) ; horodatages fixes masqués {@code <HORODATAGE>} ; aucun test ne lit l'heure de la JVM (garde de minuit conservée pour
 * la lecture {@code LocalDate.now()} du poids sec des statistiques paramédicales, non exercée ici).
 */
public abstract class Dm9CharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final String INDICATEURS = "/api/v1/direction/indicators";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test (ex. {@code planification}). */
    protected abstract String dossier();

    @Override
    protected final String theme() {
        return "../dossiermedical/dm9/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenarioDm9(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm9() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm9();
    }

    @AfterEach
    protected void apresDm9() {
        nettoyerDm9();
    }

    protected void nettoyerDm9() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM prescriptions_medicales WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM abords_vasculaires WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM decisions_rcp WHERE bilan_id IN (SELECT id FROM bilans_pre_greffe WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM bilans_pre_greffe WHERE center_id = ?", centre);
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
        return UUID.nameUUIDFromBytes(("dm9-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    protected UUID patientDm9(UUID centre, String alias) {
        return patientDm9(centre, alias, "M", null, null, null);
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}) ; sexe, naissance et transporteurs (aller, retour) optionnels. */
    protected UUID patientDm9(UUID centre, String alias, String sexe, LocalDate naissance, UUID transporteurAller, UUID transporteurRetour) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, transporteur_aller_id, transporteur_retour_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NON_VACANCIER', 'PERMANENT', ?, ?, ?)",
                id, centre, "DM9-" + alias, "Patient " + alias, "Test", sexe, naissance == null ? null : java.sql.Date.valueOf(naissance),
                java.sql.Date.valueOf(REPERE.minusDays(400)), "ASS-DM9-" + alias, transporteurAller, transporteurRetour, CREATION_FIXE);
        caches.getCacheNames().forEach(nom -> java.util.Optional.ofNullable(caches.getCache(nom)).ifPresent(org.springframework.cache.Cache::clear));
        return id;
    }

    /** Sérologie posée en base dans le centre donné (qui peut différer du centre de la fiche), saisie à l'heure donnée du 2026-01-01. */
    protected UUID serologieEnBase(UUID centre, UUID patient, String alias, String marqueur, String resultat, LocalDate prelevement, int heureDeSaisie) {
        UUID id = idStable("serologie", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        OffsetDateTime saisie = CREATION_FIXE.withHour(heureDeSaisie);
        jdbc.update("INSERT INTO serologies_patient (id, center_id, patient_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", id, centre, patient, marqueur, resultat, java.sql.Date.valueOf(prelevement), saisie, saisie);
        return id;
    }

    protected UUID serologieEnBase(UUID centre, UUID patient, String alias, String marqueur, String resultat, LocalDate prelevement) {
        return serologieEnBase(centre, patient, alias, marqueur, resultat, prelevement, 10);
    }

    protected UUID abordEnBase(UUID centre, UUID patient, String alias, String type, LocalDate creation, LocalDate fin, boolean actif) {
        UUID id = idStable("abord", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO abords_vasculaires (id, patient_id, center_id, type_abord, date_creation, date_fin, actif, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)", id, patient, centre, type, date(creation), date(fin), actif, CREATION_FIXE);
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
            args.add(new BigDecimal(colonnesEtValeurs[i + 1]));
        }
        jdbc.update("INSERT INTO resultats_analyses (" + cols + ") VALUES (" + marques + ")", args.toArray());
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

    protected void bilanEnBase(UUID centre, UUID patient, String alias, String statut, LocalDate greffe) {
        UUID id = idStable("bilan", alias + "-" + centre);
        jdbc.update("INSERT INTO bilans_pre_greffe (id, patient_id, center_id, statut, date_greffe, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
                id, patient, centre, statut, date(greffe), CREATION_FIXE, CREATION_FIXE);
    }

    private static java.sql.Date date(LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    // ------------------------------------------------------------------ chemins, profils et corps

    protected String corps(Object... paires) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paires.length; i += 2) {
            if (paires[i + 1] != null) m.put((String) paires[i], paires[i + 1] instanceof UUID || paires[i + 1] instanceof LocalDate
                    ? paires[i + 1].toString() : paires[i + 1]);
        }
        return mapper.writeValueAsString(m);
    }

    protected String statsApi(UUID patient, String suite) {
        return PATIENTS_API + "/" + patient + "/stats/" + suite;
    }

    protected RequestPostProcessor medecinA() {
        return as(CENTRE_A, "MEDECIN");
    }

    protected RequestPostProcessor adminA2() {
        return as(CENTRE_A, "ADMIN");
    }

    /** Alias nommé d'un identifiant (ou l'identifiant brut s'il est inconnu du harnais). */
    protected String aliasDe(UUID id) {
        return aliasNommes.getOrDefault(id.toString(), id.toString());
    }

    /** Alias nommés, triés, d'un ensemble d'identifiants : forme lisible et déterministe d'un {@code Set}. */
    protected List<String> aliasTries(java.util.Collection<UUID> ids) {
        TreeSet<String> tries = new TreeSet<>();
        for (UUID id : ids) tries.add(aliasDe(id));
        return new ArrayList<>(tries);
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

    protected String resume(JsonNode n, String... champs) {
        List<String> v = new ArrayList<>();
        for (String champ : champs) {
            JsonNode f = n.path(champ);
            v.add(f.isMissingNode() || f.isNull() ? "-" : f.asString());
        }
        return String.join(" | ", v);
    }

    protected MockHttpServletResponse binaire(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(java.net.URI.create(path)).with(who)).andReturn().getResponse();
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

    /** Empreinte brute des tables lues par les lecteurs DM9 : dit si une lecture a écrit quelque chose. */
    protected String empreinteLecteurs() {
        StringBuilder sb = new StringBuilder();
        for (String table : List.of("serologies_patient", "abords_vasculaires", "resultats_analyses", "prescriptions_medicales", "bilans_pre_greffe",
                "patients")) {
            sb.append(jdbc.queryForList("SELECT * FROM " + table + " WHERE center_id IN " + CENTRES + " ORDER BY id").toString());
        }
        return sb.toString();
    }
}
