package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice DM2 (prescriptions, ordonnances, anémie et observance), au-dessus des harnais de Planification (décor, dates nommées,
 * diffusions, audit, profils d'accès).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL
 * des tables {@code ordonnances}, {@code lignes_ordonnance}, {@code prescriptions_medicales}, {@code administrations_anemie},
 * {@code alertes_observance}, notifications durables, diffusions, audit. Aucun mock du code caractérisé.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », médicaments et doses inventés). Dates : deux familles,
 * jamais de date réelle dans un golden (le harnais échoue si une date non nommée reste) :
 * <ul>
 *   <li>dates FIXES nommées {@code <D+n>} (lendemain de {@link #REPERE} plus n jours) pour les prescriptions et ordonnances, qui ne
 *   dépendent pas de l'horloge ;</li>
 *   <li>dates RELATIVES au jour de la JVM nommées {@code <J-3>} pour l'observance et le planificateur (l'application lit
 *   {@code LocalDate.now()} sans horloge injectable, au fuseau de la JVM et non au fuseau du centre : KB-MED-15) ; une garde attend
 *   le passage de minuit (fuseau de la JVM) si l'on en est à moins de deux minutes.</li>
 * </ul>
 */
public abstract class PrescriptionsCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test. */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return "../dossiermedical/dm2/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm2() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm2();
    }

    @AfterEach
    protected void apresDm2() {
        nettoyerDm2();
    }

    protected void nettoyerDm2() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM lignes_ordonnance WHERE ordonnance_id IN (SELECT id FROM ordonnances WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM ordonnances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prescriptions_medicales WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM administrations_anemie WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM alertes_observance WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle = 'SEQ_ORD'", centre);
            jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
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

    /** Jour de la JVM décalé de n jours, nommé {@code <J>}, {@code <J-3>}, {@code <J+2>}. */
    protected LocalDate relatif(int n) {
        return alias(LocalDate.now().plusDays(n), n == 0 ? "<J>" : n < 0 ? "<J" + n + ">" : "<J+" + n + ">");
    }

    /** Les dates du texte écrites {@code dd/MM/yyyy} (messages d'alerte) sont remplacées par leur alias nommé. */
    protected String joursEnFrancais(String texte) {
        String resultat = texte;
        for (Map.Entry<String, String> e : datesNommees.entrySet()) {
            LocalDate d = LocalDate.parse(e.getKey());
            resultat = resultat.replace(String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()), e.getValue());
        }
        return resultat;
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("dm2-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}). */
    protected UUID patientDm2(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM2-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(REPERE.minusDays(400)),
                "ASS-DM2-" + alias, CREATION_FIXE);
        return id;
    }

    /** Prescription posée en base (cibles de dialyse seulement) ; l'identifiant est nommé. */
    protected UUID prescription(UUID centre, UUID patient, String alias, LocalDate date, Integer qb, String poidsSec) {
        UUID id = idStable("prescription", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, qb_cible, poids_sec_cible_kg, "
                        + "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)", id, patient, centre, java.sql.Date.valueOf(date), qb,
                poidsSec == null ? null : new java.math.BigDecimal(poidsSec), CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    /** Prescription du traitement de l'anémie posée en base ; article nommé, fréquence en unité {@code SEMAINE}, {@code JOUR}... */
    protected UUID prescriptionAnemie(UUID centre, UUID patient, String alias, LocalDate date, UUID epoArticle, Integer epoDose,
                                      Integer epoFrequence, String epoUnite, UUID ferArticle, Integer ferDose, Integer ferFrequence,
                                      String ferUnite) {
        UUID id = idStable("prescription", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, epo_article_id, epo_dose_ui, "
                        + "epo_voie, epo_frequence_valeur, epo_frequence_unite, fer_article_id, fer_dose_mg, fer_voie, "
                        + "fer_frequence_valeur, fer_frequence_unite, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, patient, centre, java.sql.Date.valueOf(date), epoArticle, epoDose, epoArticle == null ? null : "SC", epoFrequence,
                epoUnite, ferArticle, ferDose, ferArticle == null ? null : "IV", ferFrequence, ferUnite, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    /** Ordonnance posée en base avec une ligne ; {@code numero} nul pour un brouillon. */
    protected UUID ordonnance(UUID centre, UUID patient, String alias, LocalDate date, String statut, String numero) {
        UUID id = idStable("ordonnance", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO ordonnances (id, patient_id, center_id, medecin_id, date_prescription, statut, numero, created_at, "
                        + "updated_at, signed_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, patient, centre, "medecin-fictif",
                java.sql.Date.valueOf(date), statut, numero, CREATION_FIXE, CREATION_FIXE, numero == null ? null : CREATION_FIXE);
        jdbc.update("INSERT INTO lignes_ordonnance (id, ordonnance_id, libelle, posologie, voie, duree_jours, quantite) "
                        + "VALUES (?,?,?,?,?,?,?)", idStable("ligne", alias + "-" + centre), id, "Medicament fictif " + alias,
                "1 comprime par jour", "orale", 30, 1);
        return id;
    }

    /** Administration du traitement de l'anémie posée en base. */
    protected UUID administration(UUID centre, UUID patient, String alias, String type, LocalDate jour, boolean administree,
                                  Integer dose, String unite) {
        UUID id = idStable("administration", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO administrations_anemie (id, patient_id, center_id, type_traitement, date_administration, administree, "
                        + "dose, unite_dose, molecule, motif_non_administration, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, patient,
                centre, type, java.sql.Date.valueOf(jour), administree, dose, unite, "EPO".equals(type) ? "Epoetine fictive" : "Fer fictif",
                administree ? null : "Patient absent", CREATION_FIXE);
        return id;
    }

    /** Article du catalogue du centre (traitement de l'anémie, dosage par unité de stock facultatif). */
    protected UUID article(UUID centre, String alias, String code, String type, String dosage, String uniteDosage) {
        UUID id = idStable("article", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, gere_par_lot, active, "
                        + "type_traitement_anemie, dosage_par_unite, unite_dosage) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id, centre, code,
                "Article fictif " + code, "seringue", new java.math.BigDecimal("50"), new java.math.BigDecimal("1"), false, true, type,
                dosage == null ? null : new java.math.BigDecimal(dosage), uniteDosage);
        return id;
    }

    /** Alerte d'observance posée en base ; {@code creeeA} fixe l'ordre (elles sont lues de la plus récente à la plus ancienne). */
    protected UUID alerte(UUID centre, UUID patient, String alias, String traitement, String type, LocalDate debut, LocalDate fin,
                          int attendu, int administre, String message, int minutes, boolean resolue) {
        UUID id = idStable("alerte", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO alertes_observance (id, patient_id, center_id, type_traitement, type_alerte, periode_debut, periode_fin, "
                        + "doses_attendues, doses_administrees, message, unite_dose, dose_prescrite, frequence_valeur, frequence_unite, "
                        + "created_at, resolved_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", id, patient, centre, traitement, type,
                java.sql.Date.valueOf(debut), java.sql.Date.valueOf(fin), attendu, administre, message, "EPO".equals(traitement) ? "UI" : "mg",
                4000, 2, "SEMAINE", CREATION_FIXE.plusMinutes(minutes), resolue ? CREATION_FIXE.plusDays(30) : null);
        return id;
    }

    /** Résultat d'analyse fictif (seules les colonnes lues par le suivi de l'anémie). */
    protected void analyse(UUID centre, UUID patient, String alias, LocalDate prelevement, String hb, String ferritine, String cst,
                           String albumine, String ktv) {
        jdbc.update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, hb_g_dl, ferritine_ng_ml, cstf_pct, "
                        + "albumine_g_dl, kt_v_mensuel, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                idStable("analyse", alias + "-" + centre), patient, centre, java.sql.Date.valueOf(prelevement),
                hb == null ? null : new java.math.BigDecimal(hb), ferritine == null ? null : new java.math.BigDecimal(ferritine),
                cst == null ? null : new java.math.BigDecimal(cst), albumine == null ? null : new java.math.BigDecimal(albumine),
                ktv == null ? null : new java.math.BigDecimal(ktv), CREATION_FIXE, CREATION_FIXE);
    }

    protected void etatDesAlertes(String name) {
        sql(name, "SELECT center_id, patient_id, id, type_traitement, type_alerte, periode_debut, periode_fin, doses_attendues, "
                + "doses_administrees, unite_dose, dose_prescrite, frequence_valeur, frequence_unite, "
                + "REGEXP_REPLACE(message, '[0-9]{2}/[0-9]{2}/[0-9]{4}', '<jj/mm/aaaa>') AS message, "
                + "(CASE WHEN resolved_at IS NULL THEN 'ouverte' ELSE 'resolue' END) AS etat FROM alertes_observance WHERE center_id IN ('"
                + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, patient_id, type_traitement, type_alerte, periode_debut, id");
    }

    protected void etatDesAdministrations(String name) {
        sql(name, "SELECT center_id, patient_id, prescription_medicale_id, type_traitement, molecule, dose, unite_dose, voie, "
                + "date_administration, seance_id, administre_par, administree, motif_non_administration, article_id, quantite_article "
                + "FROM administrations_anemie WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY center_id, date_administration, administree, dose NULLS FIRST, molecule");
    }

    // ------------------------------------------------------------------ corps de requête

    protected String corpsOrdonnance(UUID centre, String medecin, LocalDate date, String lignes) {
        return "{\"centerId\":" + (centre == null ? "null" : "\"" + centre + "\"")
                + (medecin == null ? "" : ",\"medecinId\":\"" + medecin + "\"")
                + (date == null ? "" : ",\"datePrescription\":\"" + date + "\"")
                + (lignes == null ? "" : ",\"lignes\":" + lignes) + "}";
    }

    protected String ligneSimple() {
        return "[{\"libelle\":\"Medicament fictif\",\"posologie\":\"1 par jour\"}]";
    }

    protected String corpsCentre(UUID centre) {
        return "{\"centerId\":" + (centre == null ? "null" : "\"" + centre + "\"") + "}";
    }

    protected String ordonnancesApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/ordonnances";
    }

    protected String prescriptionsApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/prescriptions";
    }

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, org.springframework.test.web.servlet.request.RequestPostProcessor> profilsDm2() {
        Map<String, org.springframework.test.web.servlet.request.RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

    // ------------------------------------------------------------------ observation

    protected void etatDesOrdonnances(String name) {
        sql(name, "SELECT o.center_id, o.patient_id, o.medecin_id, o.date_prescription, o.statut, o.numero, "
                + "(CASE WHEN o.signed_at IS NULL THEN 'non' ELSE 'oui' END) AS signee, o.id FROM ordonnances o "
                + "WHERE o.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY o.center_id, o.date_prescription, o.numero, o.id");
    }

    protected void etatDesLignes(String name) {
        sql(name, "SELECT l.ordonnance_id, l.medicament_code_system, l.medicament_code, l.medicament_code_display, l.libelle, "
                + "l.posologie, l.voie, l.duree_jours, l.quantite, l.instructions FROM lignes_ordonnance l "
                + "WHERE l.ordonnance_id IN (SELECT id FROM ordonnances WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "')) ORDER BY l.ordonnance_id, l.libelle, l.posologie");
    }

    protected void etatDesPrescriptions(String name) {
        sql(name, "SELECT center_id, patient_id, id, date_prescription, medecin_id, qb_cible, qd_cible, uf_max_ml, duree_cible_min, "
                + "poids_sec_cible_kg, type_dialyseur_prescrit, anticoag_type_prescrit, epo_article_id, epo_dose_ui, epo_voie, "
                + "epo_frequence_valeur, epo_frequence_unite, fer_article_id, fer_dose_mg, fer_voie, fer_frequence_valeur, "
                + "fer_frequence_unite, (CASE WHEN created_at = TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00:00' THEN 'fixe' "
                + "ELSE 'recent' END) AS creation FROM prescriptions_medicales WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY center_id, date_prescription, id");
    }

    /** Une ordonnance sous forme lisible : ses lignes sont triées (l'ordre des lignes lues n'est pas garanti : KB-MED-16). */
    protected Map<String, Object> ordonnanceLisible(JsonNode o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.path("id").asString());
        m.put("patientId", o.path("patientId").asString());
        m.put("centerId", o.path("centerId").asString());
        m.put("medecinId", o.path("medecinId").isNull() ? null : o.path("medecinId").asString());
        m.put("datePrescription", o.path("datePrescription").asString());
        m.put("statut", o.path("statut").asString());
        m.put("numero", o.path("numero").isNull() ? null : o.path("numero").asString());
        m.put("signee", !o.path("signedAt").isNull());
        List<String> lignes = new java.util.ArrayList<>();
        o.path("lignes").forEach(l -> lignes.add(l.path("libelle").asString() + " | " + l.path("posologie").asString()));
        lignes.sort(String::compareTo);
        m.put("lignes", lignes);
        return m;
    }
}
