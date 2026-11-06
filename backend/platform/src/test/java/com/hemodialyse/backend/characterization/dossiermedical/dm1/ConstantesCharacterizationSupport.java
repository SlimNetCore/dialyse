package com.hemodialyse.backend.characterization.dossiermedical.dm1;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice DM1 (constantes du patient), au-dessus des harnais de Planification (décor, dates nommées, diffusions, audit,
 * garde de minuit du fuseau du centre, onze profils).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL de
 * {@code volet_paramedical} / {@code seances}, notifications durables, diffusions, audit. Aucun mock.
 * <p>
 * DONNÉES DE SANTÉ : toutes les valeurs sont fictives (patients « Patient &lt;alias&gt; », poids et tensions inventés). Dates : alias
 * relatifs nommés ({@code <J-4>}...), jamais de date réelle (le harnais échoue si une date non nommée reste dans un golden). Les
 * horodatages de création des lignes de fixture sont fixes (ordre déterministe).
 */
public abstract class ConstantesCharacterizationSupport extends SemaineCharacterizationSupport {

    protected static final String PATIENTS_API = "/api/v1/patients";
    protected static final String SEANCES_API = "/api/v1/seances";
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test. */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return "../dossiermedical/dm1/" + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpConstantes() {
        nettoyerConstantes();
        for (int i = -12; i <= 3; i++) jour(i);
    }

    @AfterEach
    protected void apresConstantes() {
        nettoyerConstantes();
    }

    protected void nettoyerConstantes() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM volet_paramedical WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
        // Un volet posé sous un autre centre que sa séance (scénarios de cloisonnement) : retiré par séance.
        jdbc.update("DELETE FROM volet_paramedical WHERE seance_id IN (SELECT id FROM seances WHERE center_id IN (?, ?))", CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("dm1-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /** Patient fictif du centre (nom {@code Patient <alias>}), admis à {@code <J-12>}-ou-avant, permanent. */
    protected UUID patientDm1(UUID centre, String alias) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient, created_at) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, 'NON_VACANCIER', 'PERMANENT', ?)",
                id, centre, "DM1-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(jour(-400)), "ASS-DM1-" + alias, BASE);
        return id;
    }

    /** Séance du patient ({@code minutes} après la base fixe pour {@code created_at} : ordre d'égalité de date déterministe). */
    protected UUID seanceDm1(UUID centre, String alias, UUID patient, LocalDate jour, String statut, int minutes) {
        UUID id = idStable("seance", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) "
                        + "VALUES (?, ?, ?, ?, ?, ?, FALSE)", id, patient, centre, java.sql.Date.valueOf(jour), statut, BASE.plusMinutes(minutes));
        return id;
    }

    /** Volet paramédical posé en base (valeurs fictives ; nul = colonne nulle). */
    protected UUID voletDm1(UUID centre, String alias, UUID seance, String poidsAvant, String poidsApres, String taAvant, String taApres,
                            Integer debit, String uf, Integer duree) {
        UUID id = idStable("volet", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, "
                        + "debit_sang_ml_min, ultrafiltration_ml, duree_minutes, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, seance, poidsAvant == null ? null : new BigDecimal(poidsAvant), poidsApres == null ? null : new BigDecimal(poidsApres),
                taAvant, taApres, debit, uf == null ? null : new BigDecimal(uf), duree, BASE, BASE);
        return id;
    }

    // ------------------------------------------------------------------ profils

    /** Les onze profils d'accès, plus le rôle personnalisé (rôle inconnu du serveur) : nom -> session ; l'anonyme est traité à part. */
    protected Map<String, RequestPostProcessor> profilsEtRolePersonnalise() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>(profils());
        p.put("role personnalise (CHEF_DE_SALLE)", as(CENTRE_A, "CHEF_DE_SALLE"));
        p.put("INFIRMIER + SECRETAIRE", as(CENTRE_A, "INFIRMIER", "SECRETAIRE"));
        p.put("MEDECIN + ADMIN", as(CENTRE_A, "MEDECIN", "ADMIN"));
        return p;
    }

    protected String constantes(UUID patient, String parametres) {
        return PATIENTS_API + "/" + patient + "/constantes" + (parametres == null || parametres.isEmpty() ? "" : "?" + parametres);
    }

    /** Corps de l'enregistrement d'un volet paramédical (JSON), centre du corps explicite. */
    protected String corpsVolet(UUID centre, String champs) {
        return "{\"centerId\":" + (centre == null ? "null" : "\"" + centre + "\"") + (champs == null || champs.isEmpty() ? "" : "," + champs) + "}";
    }

    /** Volet de la séance tel qu'en base (forme lisible, sans horodatage). */
    protected void etatDuVolet(String name, UUID seance) {
        sql(name, "SELECT center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, debit_sang_ml_min, ultrafiltration_ml, "
                + "duree_minutes, anticoagulant, type_dialysat, incidents FROM volet_paramedical WHERE seance_id = '" + seance + "' ORDER BY id");
    }

    /**
     * Les statistiques sérialisent la date de séance (colonne DATE) en horodatage ISO à minuit du fuseau de la JVM, converti en UTC
     * (KB-MED-xx) : on la ramène au jour du fuseau de la JVM pour que le golden ne dépende pas du fuseau de la machine de test.
     */
    protected String datesDeLaStatistique(String brut) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})").matcher(brut);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String jour = OffsetDateTime.parse(m.group()).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString();
            m.appendReplacement(sb, jour);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    protected int nombreDeVolets(UUID seance) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM volet_paramedical WHERE seance_id = ?", Integer.class, seance);
    }
}
