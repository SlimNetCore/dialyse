package com.hemodialyse.backend.characterization.dossiermedical.dm3;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice DM3 (demandes d'examen), au-dessus des harnais de Planification (décor, dates nommées, diffusions, audit, profils).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL des
 * tables {@code demandes_examen} et {@code lignes_demande_examen} (cette dernière n'a PAS de colonne {@code center_id} : ADR 0072),
 * notifications durables, diffusions, audit. Aucun mock du code caractérisé, aucun code de production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », examens et conclusions inventés). Dates : les dates
 * explicites sont FIXES et nommées {@code <D+n>} (aucune dépendance au jour d'exécution). Seules la date par défaut et la limite « futur »
 * lisent {@code LocalDate.now()} au fuseau de la JVM (sans horloge injectable : KB-MED-34) : dates {@code <J>} / {@code <J+1>} nommées,
 * et une garde attend le passage de minuit (fuseau de la JVM) si l'on en est à moins de deux minutes. Aucun scénario ne dépend du jour
 * UTC ni du jour du centre (Africa/Algiers).
 */
public abstract class ExamensCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    protected static final String MEDECIN_FICTIF = "medecin-fictif";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test. */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return "../dossiermedical/dm3/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm3() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm3();
    }

    @AfterEach
    protected void apresDm3() {
        nettoyerDm3();
    }

    protected void nettoyerDm3() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM lignes_demande_examen WHERE demande_id IN (SELECT id FROM demandes_examen WHERE center_id = ?)", centre);
            jdbc.update("DELETE FROM demandes_examen WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM observations_biologiques WHERE center_id = ?", centre);
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
        return UUID.nameUUIDFromBytes(("dm3-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}). */
    protected UUID patientDm3(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM3-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(REPERE.minusDays(400)),
                "ASS-DM3-" + alias, CREATION_FIXE);
        return id;
    }

    /** Demande d'examen posée en base (créée et modifiée à l'instant fixe) ; l'identifiant est nommé. */
    protected UUID demande(UUID centre, UUID patient, String alias, LocalDate date, String categorie, String statut, boolean urgent,
                           String motif, String conclusion) {
        UUID id = idStable("demande", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, "
                        + "statut, conclusion, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id, patient, centre,
                MEDECIN_FICTIF, java.sql.Date.valueOf(date), categorie, urgent, motif, statut, conclusion, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    /** Demande ordinaire d'une catégorie BIOLOGIE avec une ligne LOINC. */
    protected UUID demandeSimple(UUID centre, UUID patient, String alias, LocalDate date, String statut) {
        UUID id = demande(centre, patient, alias, date, "BIOLOGIE", statut, false, "Bilan fictif " + alias, null);
        ligne(id, alias, "LOINC", "718-7", "Hemoglobine fictive", "Hemoglobine", null);
        return id;
    }

    /** Ligne posée en base ; les colonnes de code sont nulles si {@code systeme} est nul. */
    protected UUID ligne(UUID demande, String alias, String systeme, String code, String display, String libelle, String commentaire) {
        UUID id = idStable("ligne", alias + "-" + demande);
        jdbc.update("INSERT INTO lignes_demande_examen (id, demande_id, analyte_code_system, analyte_code, analyte_code_display, libelle, "
                + "commentaire) VALUES (?,?,?,?,?,?,?)", id, demande, systeme, code, display, libelle, commentaire);
        return id;
    }

    // ------------------------------------------------------------------ corps de requête

    protected String corpsDemande(UUID centre, String prescripteur, LocalDate date, String categorie, Boolean urgent, String motif,
                                  String lignes) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"centerId\":").append(centre == null ? "null" : "\"" + centre + "\"");
        if (prescripteur != null) sb.append(",\"prescripteurId\":\"").append(prescripteur).append('"');
        if (date != null) sb.append(",\"dateDemande\":\"").append(date).append('"');
        if (categorie != null) sb.append(",\"categorie\":\"").append(categorie).append('"');
        if (urgent != null) sb.append(",\"urgent\":").append(urgent);
        if (motif != null) sb.append(",\"motif\":\"").append(motif).append('"');
        if (lignes != null) sb.append(",\"lignes\":").append(lignes);
        return sb.append('}').toString();
    }

    protected String ligneSimple() {
        return "[{\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"codeDisplay\":\"Hemoglobine fictive\",\"libelle\":\"Hemoglobine\"}]";
    }

    protected String demandeOrdinaire(UUID centre, LocalDate date) {
        return corpsDemande(centre, MEDECIN_FICTIF, date, "BIOLOGIE", false, "Bilan fictif", ligneSimple());
    }

    protected String corpsCentre(UUID centre) {
        return "{\"centerId\":" + (centre == null ? "null" : "\"" + centre + "\"") + "}";
    }

    protected String corpsValidation(UUID centre, String conclusion) {
        return "{\"centerId\":\"" + centre + "\"" + (conclusion == null ? "" : ",\"conclusion\":\"" + conclusion + "\"") + "}";
    }

    protected String demandesApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/demandes-examen";
    }

    protected String actionApi(UUID patient, UUID demande, String action) {
        return demandesApi(patient) + "/" + demande + "/" + action;
    }

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, RequestPostProcessor> profilsDm3() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

    protected String idDe(Resp r) {
        return corps(r).path("id").asString();
    }

    // ------------------------------------------------------------------ observation

    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";

    protected void etatDesDemandes(String name) {
        sql(name, "SELECT d.center_id, d.patient_id, d.id, d.prescripteur_id, d.date_demande, d.categorie, d.urgent, d.motif, d.statut, "
                + "d.conclusion, (CASE WHEN d.created_at = TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00:00' THEN 'fixe' "
                + "ELSE 'recent' END) AS creation, (CASE WHEN d.updated_at > d.created_at THEN 'modifiee' ELSE 'jamais modifiee' END) "
                + "AS modification FROM demandes_examen d WHERE d.center_id IN " + CENTRES
                + " ORDER BY d.center_id, d.patient_id, d.date_demande, d.categorie, d.motif, d.statut, d.id");
    }

    protected void etatDesLignes(String name) {
        sql(name, "SELECT l.demande_id, l.analyte_code_system, l.analyte_code, l.analyte_code_display, l.libelle, l.commentaire "
                + "FROM lignes_demande_examen l WHERE l.demande_id IN (SELECT id FROM demandes_examen WHERE center_id IN " + CENTRES
                + ") ORDER BY l.demande_id, l.libelle, l.analyte_code, l.commentaire");
    }

    /** Empreinte brute (statut, conclusion, dates, lignes avec leurs identifiants) : dit si une requête a écrit quelque chose. */
    protected String empreinte(UUID demande) {
        return jdbc.queryForList("SELECT statut, conclusion, urgent, motif, categorie, date_demande, created_at, updated_at "
                + "FROM demandes_examen WHERE id = ?", demande).toString()
                + jdbc.queryForList("SELECT id, analyte_code, libelle, commentaire FROM lignes_demande_examen WHERE demande_id = ? "
                + "ORDER BY id", demande);
    }

    protected int nombreDeDemandes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM demandes_examen WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    protected int nombreDeLignes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM lignes_demande_examen WHERE demande_id IN "
                + "(SELECT id FROM demandes_examen WHERE center_id IN (?, ?))", Integer.class, CENTRE_A, CENTRE_B);
    }

    protected String statutEnBase(UUID demande) {
        return jdbc.queryForObject("SELECT statut FROM demandes_examen WHERE id = ?", String.class, demande);
    }

    /** Une demande sous forme lisible : lignes triées (l'ordre des lignes lues n'est pas garanti : KB-MED-36). */
    protected Map<String, Object> demandeLisible(JsonNode d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.path("id").asString());
        m.put("patientId", d.path("patientId").asString());
        m.put("centerId", d.path("centerId").asString());
        m.put("prescripteurId", texte(d, "prescripteurId"));
        m.put("dateDemande", d.path("dateDemande").asString());
        m.put("categorie", d.path("categorie").asString());
        m.put("urgent", d.path("urgent").asBoolean());
        m.put("motif", texte(d, "motif"));
        m.put("statut", d.path("statut").asString());
        m.put("conclusion", texte(d, "conclusion"));
        List<String> lignes = new java.util.ArrayList<>();
        d.path("lignes").forEach(l -> lignes.add(texte(l, "codeSystem") + ":" + texte(l, "code") + " | " + texte(l, "libelle")
                + " | " + texte(l, "commentaire")));
        lignes.sort(String::compareTo);
        m.put("lignes", lignes);
        return m;
    }

    protected String texte(JsonNode n, String champ) {
        return n.path(champ).isNull() || n.path(champ).isMissingNode() ? null : n.path(champ).asString();
    }
}
