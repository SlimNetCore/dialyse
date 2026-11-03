package com.hemodialyse.backend.characterization.patientcouverture.pc1;

import com.hemodialyse.backend.characterization.planification.LiberationCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais de la slice PC1 (identité administrative et mouvements du patient : fiche patient, états, mouvements), au-dessus des
 * harnais Planification (décor, patients placés, dates nommées, diffusions, audit, garde de minuit du centre).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL
 * de {@code patients}, {@code mouvement_patient}, {@code attestation_droit}, {@code assure}, {@code assure_patient}. Aucun mock.
 * <p>
 * Dates : alias nommés relatifs ({@code <J>}, {@code <J-400>}, {@code <J+3000>}) ; aucun jour de mois ni jour de semaine fixe. Les
 * goldens sont sous {@code characterization/patientcouverture/pc1/} (chemin relatif au dossier des goldens de Planification).
 */
public abstract class PatientCouvertureCharacterizationSupport extends LiberationCharacterizationSupport {

    protected static final String RACINE_DES_GOLDENS = "../patientcouverture/pc1/";
    protected static final String ASSURES = PATIENTS + "/assures";
    protected static final List<String> PRIMITIFS_BOOLEENS = List.of("sousKt", "epoEnabled", "ferEnabled", "enSommeil", "jourDimanche",
            "jourLundi", "jourMardi", "jourMercredi", "jourJeudi", "jourVendredi", "jourSamedi");

    private String scenario = "?";

    /** Sous-dossier de goldens de la classe de test. */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts), pour que les noms de goldens ne se télescopent jamais. */
    @Override
    protected final String theme() {
        return RACINE_DES_GOLDENS + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(org.junit.jupiter.api.TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpPc1() {
        // KB-PAT-17 corrigé : toutes les routes (affecterAssure compris) datent au jour du centre (fuseau du centre), comme les alias
        // des goldens : plus d'hypothèse « jour de la JVM = jour du centre », plus de scénario ignoré en silence autour de minuit.
        nettoyerPc1();
        nommerLesJours();
    }

    @AfterEach
    protected void apresPc1() {
        nettoyerPc1();
    }

    protected void nettoyerPc1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
        jdbc.update("DELETE FROM patients WHERE numero_assurance LIKE 'PC1-%'");
    }

    // ------------------------------------------------------------------ corps de requête

    /** Fiche complète minimale (assuré lui-même, permanent, attestation valide) ; les tests la modifient par {@code put}/{@code remove}. */
    protected Map<String, Object> ficheMap(String nom, String numeroAssurance) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("centerId", CENTRE_A.toString());
        f.put("nom", nom);
        f.put("prenom", "Prenom");
        f.put("sexe", "M");
        f.put("dateAdmission", jour(-400).toString());
        f.put("dateNaissance", jour(-20000).toString());
        f.put("nombreEnfants", 0);
        // Les champs primitifs du corps sont tous obligatoires : un champ absent donne une erreur 500 (KB-PAT-01).
        for (String booleen : PRIMITIFS_BOOLEENS) f.put(booleen, false);
        f.put("etatPatient", "PERMANENT");
        f.put("attestationDebut", jour(-400).toString());
        f.put("attestationFin", jour(3000).toString());
        f.put("qualiteAssure", "ASSURE_LUI_MEME");
        f.put("numeroAssurance", numeroAssurance);
        return f;
    }

    /** Fiche d'un patient fixture ({@link #patientPlace} : nom {@code Patient <alias>}, numéro {@code ASS-P3-<alias>}). */
    protected Map<String, Object> ficheDuPatient(String alias) {
        return ficheMap("Patient " + alias, "ASS-P3-" + alias);
    }

    protected String corps(Map<String, Object> fiche) {
        return mapper.writeValueAsString(fiche);
    }

    protected UUID idDe(String alias) {
        return UUID.nameUUIDFromBytes(("planification-p3-patient-" + alias).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Patient fixture sans place, permanent, admis dans le passé fixe de P3 ({@code 2026-01-01}, sans alias : jamais lu dans un golden). */
    protected UUID patientSansPlace(String alias) {
        UUID id = patientPlace(CENTRE_A, alias, null, null);
        jdbc.update("UPDATE patients SET date_admission = ? WHERE id = ?", java.sql.Date.valueOf(jour(-400)), id);
        return id;
    }

    /** Fixe l'état et la date d'événement (relative, nommée) d'un patient fixture. */
    protected void etatEnBase(UUID patient, String etat, Integer decalage) {
        etat(patient, etat, decalage == null ? null : jour(decalage));
    }

    // ------------------------------------------------------------------ requêtes

    /** Création : si elle réussit, l'identifiant rendu est nommé {@code <alias>} pour les goldens. */
    protected Resp creer(String alias, Map<String, Object> fiche, RequestPostProcessor who) throws Exception {
        Resp r = doPost(PATIENTS, corps(fiche), who);
        if (r.status() == 200) {
            aliasNommes.put(corps(r).path("id").asString(), "<" + alias + ">");
        }
        return r;
    }

    protected Resp modifier(UUID id, Map<String, Object> fiche, RequestPostProcessor who) throws Exception {
        return doPut(PATIENTS + "/" + id, corps(fiche), who);
    }

    protected Resp lireLaFiche(UUID id, UUID centre, RequestPostProcessor who) throws Exception {
        return doGet(PATIENTS + "/" + id + "?centerId=" + centre, who);
    }

    protected Resp recherche(String corpsJson, RequestPostProcessor who) throws Exception {
        return doPost(PATIENTS + "/search", json(corpsJson), who);
    }

    /** Statut, code et message d'une réponse (forme compacte des refus). */
    protected String refus(Resp r) {
        if (r.raw() == null || r.raw().isBlank()) return String.valueOf(r.status());
        try {
            tools.jackson.databind.JsonNode c = mapper.readTree(r.raw());
            if (!c.has("code")) return String.valueOf(r.status());
            String detail = c.path("detail").asString("");
            if ("INTERNAL_ERROR".equals(c.path("code").asString()) || "DATA_ACCESS_ERROR".equals(c.path("code").asString())) {
                detail = "<message technique non verrouillé>";
            }
            return r.status() + " " + c.path("code").asString() + " : " + dates(detail);
        } catch (RuntimeException e) {
            return r.status() + " (corps illisible)";
        }
    }

    // ------------------------------------------------------------------ observation de la base

    /** Identité, état et valeurs par défaut de la fiche (sans horodatage de création ni code patient aléatoire). */
    protected void etatDeLIdentite(String name) {
        sql(name, "SELECT nom, prenom, sexe, civilite, center_id, date_admission, date_naissance, lieu_naissance, nombre_enfants, "
                + "etat_patient, date_evenement_etat, type_patient, en_sommeil, qualite_assure, numero_assurance, "
                + "assure_numero_assurance, sous_kt, epo_enabled, fer_enabled, adresse, tel_mobile, email, "
                + "CASE WHEN code_patient LIKE 'PAT-________' THEN 'PAT-xxxxxxxx' ELSE code_patient END AS code_patient "
                + "FROM patients WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, nom, prenom");
    }

    protected void etatDesAttestations(String name) {
        sql(name, "SELECT p.nom, a.center_id, a.date_debut, a.date_fin FROM attestation_droit a JOIN patients p ON p.id = a.patient_id "
                + "WHERE a.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY a.center_id, p.nom, a.date_debut, a.date_fin");
    }

    protected int nombreDeFiches() {
        return nombreDe("patients");
    }

    protected int nombreDeMouvements() {
        return nombreDe("mouvement_patient");
    }

    /** Empreinte de la fiche et de ses tables liées : dit si une requête a écrit quelque chose. */
    protected String empreinteDeLaFiche() {
        return jdbc.queryForList("SELECT * FROM patients WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT id, type, date_effet FROM mouvement_patient WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B)
                + jdbc.queryForList("SELECT patient_id, date_debut, date_fin FROM attestation_droit WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B)
                + jdbc.queryForList("SELECT numero_assurance FROM assure WHERE center_id IN (?, ?) ORDER BY numero_assurance", CENTRE_A, CENTRE_B);
    }
}
