package com.hemodialyse.backend.characterization.dossiermedical.dm4;

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
 * Harnais de la slice DM4 (sérologies du patient), au-dessus des harnais de Planification (décor, dates nommées, diffusions, audit).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL de la
 * table REELLE {@code serologies_patient} (et non le schéma parallèle gelé {@code serologies} du module dossier-medical, ADR 0072),
 * notifications durables, diffusions, audit, et placement du patient (Planification P5). Aucun mock du code caractérisé, aucun code de
 * production modifié.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (« Patient &lt;alias&gt; », conduites inventées), aucun résultat réel. Dates : les
 * dates explicites sont FIXES et nommées {@code <D+n>}. Seules la date par défaut et la limite « futur » lisent {@code LocalDate.now()}
 * au fuseau de la JVM (sans horloge injectable : KB-MED-45) : dates {@code <J>} / {@code <J+1>} nommées, et une garde attend le passage de
 * minuit (fuseau de la JVM) si l'on en est à moins de deux minutes. Aucun scénario ne dépend du jour UTC ni du jour du centre.
 */
public abstract class SerologieCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final LocalDate REPERE = LocalDate.of(2026, 1, 1);
    protected static final OffsetDateTime CREATION_FIXE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    protected static final String CONDUITE = "Isoler le patient (fictif)";

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test. */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return "../dossiermedical/dm4/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpDm4() throws Exception {
        attendreSiProcheDeMinuitDeLaJvm();
        nettoyerDm4();
    }

    @AfterEach
    protected void apresDm4() {
        nettoyerDm4();
    }

    protected void nettoyerDm4() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
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
        return UUID.nameUUIDFromBytes(("dm4-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}). */
    protected UUID patientDm4(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM4-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(REPERE.minusDays(400)),
                "ASS-DM4-" + alias, CREATION_FIXE);
        return id;
    }

    /** Sérologie posée en base (créée et modifiée à l'instant fixe) ; l'identifiant est nommé {@code <alias>}. */
    protected UUID serologieEnBase(UUID centre, UUID patient, String alias, String marqueur, String resultat, LocalDate date,
                                   String conduite) {
        UUID id = idStable("serologie", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO serologies_patient (id, patient_id, center_id, marqueur, resultat, titre, unite, date_prelevement, "
                        + "laboratoire, date_prochain_controle, conduite_a_tenir, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,NULL,NULL,?,'Laboratoire fictif',NULL,?,?,?)", id, patient, centre, marqueur, resultat,
                java.sql.Date.valueOf(date), conduite, CREATION_FIXE, CREATION_FIXE);
        return id;
    }

    // ------------------------------------------------------------------ corps de requête

    protected String corps(UUID centre, String marqueur, String resultat, LocalDate date, String conduite) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"centerId\":").append(centre == null ? "null" : "\"" + centre + "\"");
        if (marqueur != null) sb.append(",\"marqueur\":\"").append(marqueur).append('"');
        if (resultat != null) sb.append(",\"resultat\":\"").append(resultat).append('"');
        if (date != null) sb.append(",\"datePrelevement\":\"").append(date).append('"');
        if (conduite != null) sb.append(",\"conduiteATenir\":\"").append(conduite).append('"');
        return sb.append('}').toString();
    }

    /** Corps de création avec les champs facultatifs complets. */
    protected String corpsComplet(UUID centre, String marqueur, String resultat, String titre, String unite, LocalDate date,
                                  String laboratoire, LocalDate prochainControle, String conduite) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"centerId\":\"").append(centre).append('"');
        sb.append(",\"marqueur\":\"").append(marqueur).append('"');
        sb.append(",\"resultat\":\"").append(resultat).append('"');
        if (titre != null) sb.append(",\"titre\":").append(titre);
        if (unite != null) sb.append(",\"unite\":\"").append(unite).append('"');
        if (date != null) sb.append(",\"datePrelevement\":\"").append(date).append('"');
        if (laboratoire != null) sb.append(",\"laboratoire\":\"").append(laboratoire).append('"');
        if (prochainControle != null) sb.append(",\"dateProchainControle\":\"").append(prochainControle).append('"');
        if (conduite != null) sb.append(",\"conduiteATenir\":\"").append(conduite).append('"');
        return sb.append('}').toString();
    }

    protected String corpsMaj(UUID centre, String resultat, String conduite) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"centerId\":").append(centre == null ? "null" : "\"" + centre + "\"");
        if (resultat != null) sb.append(",\"resultat\":\"").append(resultat).append('"');
        if (conduite != null) sb.append(",\"conduiteATenir\":\"").append(conduite).append('"');
        return sb.append('}').toString();
    }

    protected String serologiesApi(UUID patient) {
        return PATIENTS_API + "/" + patient + "/serologies";
    }

    protected String serologieApi(UUID patient, UUID serologie) {
        return serologiesApi(patient) + "/" + serologie;
    }

    /** Les onze profils d'accès de Planification, un rôle personnalisé inconnu du serveur et deux cumuls de rôles. */
    protected Map<String, RequestPostProcessor> profilsDm4() {
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

    // ------------------------------------------------------------------ observation

    private static final String CENTRES = "('" + CENTRE_A + "','" + CENTRE_B + "')";

    /** État de la table réelle, trié sans identifiant ; création masquée (fixe ou récente), modification dite. */
    protected void etatDesSerologies(String name) {
        sql(name, "SELECT s.center_id, s.patient_id, s.marqueur, s.date_prelevement, s.resultat, s.titre, s.unite, s.laboratoire, "
                + "s.date_prochain_controle, s.conduite_a_tenir, s.id, (CASE WHEN s.created_at = TIMESTAMP WITH TIME ZONE "
                + "'2026-01-01 10:00:00+00:00' THEN 'fixe' ELSE 'recent' END) AS creation, (CASE WHEN s.updated_at > s.created_at "
                + "THEN 'modifiee' ELSE 'jamais modifiee' END) AS modification FROM serologies_patient s WHERE s.center_id IN "
                + CENTRES + " ORDER BY s.center_id, s.patient_id, s.marqueur, s.date_prelevement");
    }

    /** Empreinte brute : dit si une requête a écrit quelque chose. */
    protected String empreinte() {
        return jdbc.queryForList("SELECT id, patient_id, center_id, marqueur, resultat, titre, unite, date_prelevement, laboratoire, "
                + "date_prochain_controle, conduite_a_tenir, created_at, updated_at FROM serologies_patient WHERE center_id IN "
                + CENTRES + " ORDER BY id").toString();
    }

    protected int nombreDeSerologies() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM serologies_patient WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    protected String resultatEnBase(UUID serologie) {
        return jdbc.queryForObject("SELECT resultat FROM serologies_patient WHERE id = ?", String.class, serologie);
    }

    /** Une sérologie sous forme lisible (dates nommées par {@code dates()} au moment de l'arbre). */
    protected Map<String, Object> serologieLisible(JsonNode s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.path("id").asString());
        m.put("patientId", s.path("patientId").asString());
        m.put("centerId", s.path("centerId").asString());
        m.put("marqueur", s.path("marqueur").asString());
        m.put("resultat", s.path("resultat").asString());
        m.put("titre", texte(s, "titre"));
        m.put("unite", texte(s, "unite"));
        m.put("datePrelevement", s.path("datePrelevement").asString());
        m.put("laboratoire", texte(s, "laboratoire"));
        m.put("dateProchainControle", texte(s, "dateProchainControle"));
        m.put("conduiteATenir", texte(s, "conduiteATenir"));
        return m;
    }

    protected String texte(JsonNode n, String champ) {
        return n.path(champ).isNull() || n.path(champ).isMissingNode() ? null : n.path(champ).asString();
    }
}
