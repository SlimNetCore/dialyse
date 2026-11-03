package com.hemodialyse.backend.characterization.patientcouverture;

import com.hemodialyse.backend.characterization.planification.LiberationCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Harnais des slices PC2 (assuré principal) et PC3 (prise en charge), au-dessus des harnais Planification (décor, dates nommées,
 * diffusions, audit, garde de minuit du fuseau du centre, onze profils).
 * <p>
 * Point d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, H2 {@code test}), état SQL de
 * {@code assure}, {@code assure_patient}, {@code prise_en_charge}, {@code attestation_droit}, {@code forfait}, notifications durables,
 * diffusions, audit. Aucun mock.
 * <p>
 * Dates : alias relatifs nommés ({@code <J>}, {@code <J-5>}...), jamais de date réelle (le harnais échoue si une date non nommée reste
 * dans un golden), aucune dépendance au jour du mois ni de la semaine. Les horodatages de création des lignes de fixture sont fixes
 * (ordre déterministe, normalisés par le harnais).
 */
public abstract class CouvertureCharacterizationSupport extends LiberationCharacterizationSupport {

    protected static final String ASSURES = PATIENTS + "/assures";
    protected static final String PEC = "/api/v1/pec";
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    private String scenario = "?";

    /** Racine des goldens (relative au dossier de Planification) : {@code ../patientcouverture/pc2/} ou {@code .../pc3/}. */
    protected abstract String racine();

    /** Sous-dossier de goldens de la classe de test. */
    protected abstract String dossier();

    /** Un sous-dossier par scénario (nom de la méthode tronqué : chemins Windows courts). */
    @Override
    protected final String theme() {
        return racine() + dossier() + "/" + scenario;
    }

    @BeforeEach
    protected void capterLeScenario(org.junit.jupiter.api.TestInfo info) {
        String nom = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse("?");
        scenario = nom.length() > 36 ? nom.substring(0, 36) : nom;
    }

    @BeforeEach
    protected void setUpCouverture() {
        nettoyerCouverture();
        nommerLesJours();
    }

    @AfterEach
    protected void apresCouverture() {
        nettoyerCouverture();
    }

    protected void nettoyerCouverture() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM assure_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM assure WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
        }
        jdbc.update("DELETE FROM assure WHERE numero_assurance LIKE 'PC2-%' OR numero_assurance LIKE 'PC3-%'");
    }

    // ------------------------------------------------------------------ fixtures

    protected static UUID idStable(String famille, String alias) {
        return UUID.nameUUIDFromBytes(("couverture-" + famille + "-" + alias).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Patient du centre (nom {@code Patient <alias>}, numéro d'assurance {@code ASS-<alias>}), admis à {@code <J-400>}, sans place.
     * {@code qualite} nulle = colonne nulle ; {@code vacancier} = type {@code VACANCIER}, état {@code VACANCIER_LOCAL}.
     */
    protected UUID patientCouvert(UUID centre, String alias, boolean vacancier, String qualite) {
        UUID id = idStable("patient", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, date_naissance, numero_assurance, "
                        + "type_patient, etat_patient, date_evenement_etat, qualite_assure, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'M', ?, ?, ?, ?, ?, ?, ?, ?)",
                id, centre, "PC-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(jour(-400)), java.sql.Date.valueOf(jour(-20000)),
                "ASS-" + alias, vacancier ? "VACANCIER" : "NON_VACANCIER", vacancier ? "VACANCIER_LOCAL" : "PERMANENT",
                vacancier ? java.sql.Date.valueOf(jour(300)) : null, qualite, BASE);
        return id;
    }

    protected UUID patientAyantDroit(String alias) {
        return patientCouvert(CENTRE_A, alias, false, "ENFANT");
    }

    protected void assure(UUID centre, String numero, String nom, String prenom) {
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, created_at) VALUES (?,?,?,?,?,?)",
                numero, centre, nom, prenom, "M", BASE);
    }

    /** Affectation posée en base ({@code minutes} après la base fixe : ordre d'historique déterministe). */
    protected UUID affectationEnBase(UUID centre, String alias, UUID patient, String numero, boolean principale, LocalDate debut,
                                     LocalDate fin, int minutes) {
        UUID id = idStable("affectation", alias);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation, "
                        + "date_debut_affectation, date_fin_affectation) VALUES (?,?,?,?,?,?,?,?)", id, patient, numero, centre, principale,
                BASE.plusMinutes(minutes), debut == null ? null : java.sql.Date.valueOf(debut), fin == null ? null : java.sql.Date.valueOf(fin));
        return id;
    }

    protected UUID forfait(UUID centre, String code, String prix) {
        UUID id = idStable("forfait", code + "-" + centre);
        aliasNommes.put(id.toString(), "<FORFAIT_" + code + (centre.equals(CENTRE_B) ? "_B" : "") + ">");
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,?)", id, centre, code, "Forfait " + code,
                new java.math.BigDecimal(prix));
        return id;
    }

    protected void attestation(UUID centre, UUID patient, LocalDate debut, LocalDate fin) {
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), patient, centre, java.sql.Date.valueOf(debut), java.sql.Date.valueOf(fin), BASE);
    }

    /** PEC posée en base ({@code minutes} après la base fixe pour {@code created_at}). */
    protected UUID pecEnBase(UUID centre, String alias, UUID patient, String statut, LocalDate dd, LocalDate df, UUID forfaitDemande,
                             LocalDate de, LocalDate fe, UUID forfaitEffectif, int minutes) {
        UUID id = idStable("pec", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, statut, date_debut_demande, date_fin_demande, forfait_demande_id, "
                        + "date_debut_effectif, date_fin_effectif, forfait_effectif_id, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, patient, statut, sqlDate(dd), sqlDate(df), forfaitDemande, sqlDate(de), sqlDate(fe), forfaitEffectif,
                BASE.plusMinutes(minutes));
        return id;
    }

    protected static java.sql.Date sqlDate(LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    /**
     * La validité d'attestation de l'ancien {@code PecDomainService} (retiré, PC3) lisait {@code LocalDate.now()} (fuseau de la JVM, KB-PAT-20) alors que les
     * alias de dates sont ceux du jour du centre : un scénario qui touche la borne « aujourd'hui » est ignoré quand les deux jours
     * diffèrent (fenêtre d'une heure par jour si la JVM n'est pas au fuseau du centre).
     */
    protected static void supposerLeJourDeLaJvmEgalAuJourDuCentre() {
        org.junit.jupiter.api.Assumptions.assumeTrue(LocalDate.now().equals(aujourdhui()),
                "jour de la JVM différent du jour du centre (KB-PAT-20)");
    }

    /** Laisse passer quelques millisecondes : l'ordre de l'historique repose sur l'horodatage de création. */
    protected static void pause() throws InterruptedException {
        Thread.sleep(15);
    }

    // ------------------------------------------------------------------ corps de fiche (POST/PUT /api/v1/patients)

    private static final List<String> PRIMITIFS_BOOLEENS = List.of("sousKt", "epoEnabled", "ferEnabled", "enSommeil", "jourDimanche",
            "jourLundi", "jourMardi", "jourMercredi", "jourJeudi", "jourVendredi", "jourSamedi");

    /** Fiche complète minimale d'un patient assuré lui-même, permanent, avec attestation valide, dans le centre A. */
    protected Map<String, Object> ficheMap(String nom, String numeroAssurance) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("centerId", CENTRE_A.toString());
        f.put("nom", nom);
        f.put("prenom", "Prenom");
        f.put("sexe", "M");
        f.put("dateAdmission", jour(-400).toString());
        f.put("dateNaissance", jour(-20000).toString());
        f.put("nombreEnfants", 0);
        for (String booleen : PRIMITIFS_BOOLEENS) f.put(booleen, false);
        f.put("etatPatient", "PERMANENT");
        f.put("attestationDebut", jour(-400).toString());
        f.put("attestationFin", jour(3000).toString());
        f.put("qualiteAssure", "ASSURE_LUI_MEME");
        f.put("numeroAssurance", numeroAssurance);
        return f;
    }

    /** Fiche d'un ayant droit : la qualité et les coordonnées de l'assuré principal tiers. */
    protected Map<String, Object> ficheAyantDroit(String nom, String numeroAssurance, String assureNumero, String assureNom) {
        Map<String, Object> f = ficheMap(nom, numeroAssurance);
        f.put("qualiteAssure", "ENFANT");
        f.put("assureNumeroAssurance", assureNumero);
        f.put("assureNom", assureNom);
        f.put("assurePrenom", "Prenom assuré");
        f.put("assureSexe", "F");
        f.put("assureDateNaissance", jour(-15000).toString());
        f.put("assureAdresse", "rue de l'assuré");
        return f;
    }

    /** Création de fiche : si elle réussit, l'identifiant rendu est nommé {@code <alias>}. */
    protected Resp creerLaFiche(String alias, Map<String, Object> fiche, RequestPostProcessor who) throws Exception {
        Resp r = doPost(PATIENTS, corps(fiche), who);
        if (r.status() == 200) aliasNommes.put(corps(r).path("id").asString(), "<" + alias + ">");
        return r;
    }

    protected Resp modifierLaFiche(UUID id, Map<String, Object> fiche, RequestPostProcessor who) throws Exception {
        return doPut(PATIENTS + "/" + id, corps(fiche), who);
    }

    // ------------------------------------------------------------------ requêtes

    /** {@code POST /api/v1/pec} : si elle réussit, l'identifiant rendu est nommé {@code <alias>}. */
    protected Resp creerLaPec(String alias, UUID patient, UUID centre, LocalDate debut, LocalDate fin, UUID forfait, RequestPostProcessor who)
            throws Exception {
        Resp r = doPost(PEC, corps(champs("patientId", patient, "centerId", centre, "userId", "utilisateur", "dateDebutDemande",
                debut == null ? null : debut.toString(), "dateFinDemande", fin == null ? null : fin.toString(), "forfaitDemandeId", forfait)), who);
        if (r.status() == 200) aliasNommes.put(corps(r).path("id").asString(), "<" + alias + ">");
        return r;
    }

    protected Resp valider(UUID pec, UUID centre, LocalDate debut, LocalDate fin, UUID forfait, RequestPostProcessor who) throws Exception {
        return doPost(PEC + "/" + pec + "/validate", corps(champs("centerId", centre, "userId", "utilisateur", "dateDebutEffectif",
                debut == null ? null : debut.toString(), "dateFinEffectif", fin == null ? null : fin.toString(), "forfaitEffectifId", forfait)), who);
    }

    protected Resp cloturer(UUID pec, UUID centre, RequestPostProcessor who) throws Exception {
        return doPost(PEC + "/" + pec + "/close", corps(champs("centerId", centre)), who);
    }

    protected Resp sessionAllowed(UUID pec, UUID centre, RequestPostProcessor who) throws Exception {
        return doGet(PEC + "/" + pec + "/session-allowed?centerId=" + centre + "&userId=utilisateur", who);
    }

    protected Resp supprimer(UUID pec, UUID centre, UUID patient, RequestPostProcessor who) throws Exception {
        return doDelete(PEC + "/" + pec + "?centerId=" + centre + (patient == null ? "" : "&patientId=" + patient), who);
    }

    protected Resp pecsDuCentre(UUID centre, RequestPostProcessor who) throws Exception {
        return doGet(PEC + "?centerId=" + centre, who);
    }

    protected Resp pecsDuPatient(UUID patient, UUID centre, RequestPostProcessor who) throws Exception {
        return doGet(PEC + "/patient/" + patient + "?centerId=" + centre, who);
    }

    protected Resp anonyme(String methode, String path, String corps) throws Exception {
        return appel(methode, path, corps, null);
    }

    protected Resp affecter(UUID patient, String numero, RequestPostProcessor who) throws Exception {
        return doPost(PATIENTS + "/" + patient + "/assures/" + numero + "/affecter?centerId=" + CENTRE_A, "", who);
    }

    protected Resp historique(UUID patient, RequestPostProcessor who) throws Exception {
        return doGet(PATIENTS + "/" + patient + "/assures/history?centerId=" + CENTRE_A, who);
    }

    /** Statut, code et message d'un refus (forme compacte), dates nommées. */
    protected String refus(Resp r) {
        if (r.raw() == null || r.raw().isBlank()) return String.valueOf(r.status());
        if (r.status() >= 200 && r.status() < 300) return r.status() + " (accepté)";
        try {
            tools.jackson.databind.JsonNode c = mapper.readTree(r.raw());
            if (!c.has("code")) return r.status() + " " + dates(abrege(r.raw()));
            String detail = c.path("detail").asString("");
            if ("INTERNAL_ERROR".equals(c.path("code").asString()) || "DATA_ACCESS_ERROR".equals(c.path("code").asString())) {
                detail = "<message technique non verrouillé>";
            }
            // PEC introuvable : l'adaptateur met en cache un résultat vide, Spring refuse la valeur nulle (KB-PAT-21) ; le texte du
            // framework n'est pas verrouillé, le fait que la réponse soit ce 400 l'est.
            if (detail.startsWith("Cache '")) detail = "<message du cache Spring : valeur nulle refusée>";
            return r.status() + " " + c.path("code").asString() + " : " + dates(detail);
        } catch (RuntimeException e) {
            return r.status() + " (corps illisible)";
        }
    }

    private static String abrege(String s) {
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    // ------------------------------------------------------------------ observation de la base

    protected void etatDesAssures(String name) {
        sql(name, "SELECT numero_assurance, center_id, nom, prenom, sexe, date_naissance, tel_personnel, tel_mobile, tel_bureau, adresse, "
                + "groupe_sanguin FROM assure WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY numero_assurance, center_id");
    }

    protected void etatDesAffectations(String name) {
        sql(name, "SELECT p.nom AS patient, a.numero_assurance, a.center_id, a.is_primary, a.date_debut_affectation AS debut, "
                + "a.date_fin_affectation AS fin FROM assure_patient a JOIN patients p ON p.id = a.patient_id WHERE a.center_id IN ('"
                + CENTRE_A + "','" + CENTRE_B + "') ORDER BY p.nom, a.date_affectation, a.numero_assurance");
    }

    protected void etatDesPec(String name) {
        sql(name, "SELECT p.nom AS patient, c.center_id, c.statut, c.date_debut_demande, c.date_fin_demande, fd.code AS forfait_demande, "
                + "c.date_debut_effectif, c.date_fin_effectif, fe.code AS forfait_effectif FROM prise_en_charge c "
                + "JOIN patients p ON p.id = c.patient_id LEFT JOIN forfait fd ON fd.id = c.forfait_demande_id "
                + "LEFT JOIN forfait fe ON fe.id = c.forfait_effectif_id WHERE c.center_id IN ('" + CENTRE_A + "','" + CENTRE_B
                + "') ORDER BY p.nom, c.created_at, c.date_debut_demande, c.statut");
    }

    protected int nombreDePec() {
        return nombreDe("prise_en_charge");
    }

    protected String empreinteDeLaCouverture() {
        return jdbc.queryForList("SELECT * FROM assure WHERE center_id IN (?, ?) ORDER BY numero_assurance", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT * FROM assure_patient WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B)
                + jdbc.queryForList("SELECT * FROM prise_en_charge WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B);
    }

    /** Corps JSON (apostrophes) d'une table de champs, dans l'ordre d'insertion. */
    protected String corps(Map<String, Object> champs) {
        return mapper.writeValueAsString(champs);
    }

    protected static Map<String, Object> champs(Object... clesEtValeurs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < clesEtValeurs.length; i += 2) m.put((String) clesEtValeurs[i], clesEtValeurs[i + 1]);
        return m;
    }
}
