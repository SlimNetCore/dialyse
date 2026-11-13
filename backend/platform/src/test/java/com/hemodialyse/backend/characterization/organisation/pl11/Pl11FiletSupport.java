package com.hemodialyse.backend.characterization.organisation.pl11;

import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.organisation.OrganisationCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Filet PL11-0 : administration generique des referentiels (salles, creneaux, medecins) au point HTTP. Chaque test construit un
 * journal ordonne (etape numerotee -> statut, type, corps) verrouille par UN golden relisible. Aucune date du jour : les dates de
 * fixtures sont fixes. Le filtre d'audit est branche pour observer le journal de tracabilite.
 */
public abstract class Pl11FiletSupport extends OrganisationCharacterizationSupport {

    protected static final String A = "/api/v1/admin/referentials";
    protected static final String ORG = "/api/v1/organisation/referentiels";
    protected static final String LECTURES = "/api/v1/referentials";

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    protected ObjectNode journal;
    private int etape;
    private int sequenceEquipement;

    @BeforeEach
    void preparerPl11() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter, filtreDAudit).build();
        nettoyerPl11();
        journal = mapper.createObjectNode();
        etape = 0;
        sequenceEquipement = 0;
    }

    @AfterEach
    void nettoyerApresPl11() {
        nettoyerPl11();
    }

    private void nettoyerPl11() {
        ecrivainDAudit.flush();
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : List.of("patients", "seances", "prescriptions_medicales", "infirmier_affectation",
                    "mouvement_patient", "planification_calendrier_case", "audit_log")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", centre);
        }
    }

    // ------------------------------------------------------------------ journal

    protected void trace(String libelle, Resp r) {
        ObjectNode n = journal.putObject(String.format("%02d %s", ++etape, libelle));
        n.put("status", r.status());
        n.put("contentType", r.contentType());
        if (r.raw() == null || r.raw().isBlank()) {
            n.putNull("body");
        } else {
            try {
                n.set("body", mapper.readTree(r.raw()));
            } catch (RuntimeException e) {
                n.put("bodyText", r.raw());
            }
        }
    }

    protected void traceSql(String libelle, String sql, Object... args) {
        ArrayNode arr = journal.putObject(String.format("%02d %s", ++etape, libelle)).putArray("lignes");
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            ObjectNode o = arr.addObject();
            row.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> o.put(e.getKey().toLowerCase(), e.getValue() == null ? null : String.valueOf(e.getValue())));
        }
    }

    protected void traceNote(String libelle, String valeur) {
        journal.putObject(String.format("%02d %s", ++etape, libelle)).put("valeur", valeur);
    }

    protected void verrouiller(String nom) {
        goldenNode(nom, journal);
    }

    /** Journal d'audit des centres de test, apres vidage de l'ecrivain asynchrone. */
    protected void traceAudit(String libelle) {
        ecrivainDAudit.flush();
        traceSql(libelle, "SELECT action_code, entity_type, entity_id, http_method, route_template, status_code, roles, COUNT(*) AS nombre "
                + "FROM audit_log WHERE center_id IN (?, ?) GROUP BY action_code, entity_type, "
                + "entity_id, http_method, route_template, status_code, roles ORDER BY route_template, http_method, status_code, entity_id, roles, action_code",
                CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ requetes

    protected static String corps(UUID centre, String values) {
        return "{'centerId':'" + centre + "','values':" + values + "}";
    }

    protected Resp creer(String kind, UUID centre, String values, RequestPostProcessor who) throws Exception {
        return doPost(A + "/" + kind, corps(centre, values), who);
    }

    protected Resp modifier(String kind, UUID id, UUID centre, String values, RequestPostProcessor who) throws Exception {
        return doPut(A + "/" + kind + "/" + id, corps(centre, values), who);
    }

    protected Resp supprimer(String kind, UUID id, UUID centre, RequestPostProcessor who) throws Exception {
        return doDelete(A + "/" + kind + "/" + id + "?centerId=" + centre, who);
    }

    protected Resp lister(String kind, UUID centre, String complement, RequestPostProcessor who) throws Exception {
        return doGet(A + "/" + kind + "?centerId=" + centre + (complement == null ? "" : "&" + complement), who);
    }

    protected Resp importer(String kind, UUID centre, boolean reel, String csv, RequestPostProcessor who) throws Exception {
        return doPostFichier(A + "/" + kind + "/import?centerId=" + centre + (reel ? "&dryRun=false" : ""),
                csv.getBytes(StandardCharsets.UTF_8), "import.csv", who);
    }

    protected RequestPostProcessor admin() {
        return as(CENTRE_A, "ADMIN");
    }

    // ------------------------------------------------------------------ fixtures d'usage

    private static final Date JOUR = Date.valueOf("2026-01-02");

    protected UUID patient(UUID centre, UUID salle, UUID creneau, UUID medecin) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, qualite_assure, sous_kt, epo_enabled, created_at, salle_id, position_id, "
                        + "medecin_traitant_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,FALSE,CURRENT_TIMESTAMP,?,?,?)",
                id, centre, "CP-" + id.toString().substring(0, 8), "NOM", "Prenom", "M", Date.valueOf("1970-05-05"),
                "ASS-" + id.toString().substring(0, 8), JOUR, "NON_VACANCIER", "ASSURE", false, salle, creneau, medecin);
        return id;
    }

    protected void equipement(UUID centre, UUID salle) {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "date_creation, cree_par, salle_id) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)",
                UUID.randomUUID(), "PL11-EQ-" + (++sequenceEquipement), "Generateur", centre, new UUID(0, 0), salle);
    }

    protected void affectationInfirmier(UUID centre, UUID salle, UUID creneau) {
        jdbc.update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), centre, UUID.randomUUID(), salle, creneau, "LUNDI");
    }

    protected void mouvement(UUID centre, UUID patient, UUID salle, UUID creneau) {
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, salle_id, position_id, cree_le) "
                        + "VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), centre, patient, "AFFECTATION", JOUR, salle, creneau);
    }

    protected void caseDeCalendrier(UUID centre, UUID salle, UUID creneau) {
        jdbc.update("INSERT INTO planification_calendrier_case (run_id, center_id, semaine_debut, salle_id, salle_nom, salle_ordre, "
                        + "creneau_id, creneau_libelle, creneau_ordre, jours) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, JOUR, salle, "Salle", 0, creneau, "Creneau", 0, "[]");
    }

    protected void seance(UUID centre, UUID patient, UUID salle, UUID creneau) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, salle_id, creneau_id) "
                + "VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,?,?)", UUID.randomUUID(), patient, centre, JOUR, "PLANIFIEE", salle, creneau);
    }

    protected void prescription(UUID centre, UUID patient, UUID medecin) {
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, medecin_id, created_at, "
                + "updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", UUID.randomUUID(), patient, centre, JOUR, medecin);
    }
}
