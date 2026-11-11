package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet S6a (ADR 0133), droits, garde de centre, annulation d'un lot ({@code MigrationRollbackJdbcAdapter}) et erreurs
 * generales de fichier / de lot (cadre de l'outil : {@code MigrationDomainService}). Observation : routes
 * {@code /api/v1/admin/migration/**} et tables {@code patients}, {@code assure}, {@code assure_patient}, {@code factures}.
 */
class HistoriqueDroitsEtAnnulationCharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final String ENTETE_AFFECTATIONS = "legacyId;patient;assureNumeroAssurance;dateDebut;dateFin;principale";
    private static final String ENTETE_SOLDES =
            "numeroFacture;patient;dateFacture;periodeDebut;periodeFin;montantTtc;montantRegle;dateDernierReglement;libelle";

    private ObjectNode statuts(UUID lot, RequestPostProcessor who, String fichierPatients) throws Exception {
        ObjectNode o = mapper.createObjectNode();
        o.put("GET entities", envoyer(get(BASE + "/entities"), who).status());
        o.put("GET batches", envoyer(get(BASE + "/batches"), who).status());
        o.put("POST batches", envoyerJson(post(BASE + "/batches"), who, "{'libelle':'Autre'}").status());
        o.put("POST import patients", importer(lot, "patients", fichierPatients, false, who, null).status());
        o.put("POST close", envoyer(post(BASE + "/batches/{id}/close", lot), who).status());
        o.put("POST cancel", envoyer(post(BASE + "/batches/{id}/cancel", lot), who).status());
        o.put("PUT value-mappings", envoyerJson(put(BASE + "/value-mappings"), who,
                "{'column':'etatPatient','source':'D','target':'DECEDE'}").status());
        return o;
    }

    @Test
    void la_reprise_est_reservee_aux_administrateurs_de_leur_centre_et_au_superadmin() throws Exception {
        UUID lot = ouvrirLot(adminA());
        String patients = fichier(ENTETE_PATIENTS, "P1;ZD111;BENALI;Karim;M;1962-03-15;2019-01-02;;;");
        ObjectNode r = mapper.createObjectNode();
        r.set("technicien", statuts(lot, qui(CENTRE_A, TECH, "TECHNICIEN"), patients));
        r.set("infirmier", statuts(lot, qui(CENTRE_A, TECH, "INFIRMIER"), patients));
        r.set("technicien_corps_du_refus", rendu(importer(lot, "patients", patients, false, qui(CENTRE_A, TECH, "TECHNICIEN"), null)));
        r.set("etat_apres_refus_des_roles", etat());

        ObjectNode versB = mapper.createObjectNode();
        versB.put("GET batches?centerId=B", envoyer(get(BASE + "/batches").param("centerId", CENTRE_B.toString()), adminA()).status());
        versB.put("POST batches centerId=B", envoyerJson(post(BASE + "/batches"), adminA(),
                "{'libelle':'Autre','centerId':'" + CENTRE_B + "'}").status());
        versB.put("POST import centerId=B", importer(lot, "patients", patients, false, adminA(), CENTRE_B).status());
        versB.put("GET value-mappings?centerId=B", envoyer(get(BASE + "/value-mappings").param("centerId", CENTRE_B.toString()),
                adminA()).status());
        r.set("admin_A_vers_centre_B", versB);

        ObjectNode adminBSurLotA = mapper.createObjectNode();
        adminBSurLotA.set("GET lot", rendu(envoyer(get(BASE + "/batches/{id}", lot), adminB())));
        adminBSurLotA.set("POST import", rendu(importer(lot, "patients", patients, false, adminB(), null)));
        adminBSurLotA.set("POST cancel", rendu(annuler(lot, adminB())));
        r.set("admin_B_sur_le_lot_de_A", adminBSurLotA);
        r.set("etat_centre_A_intact", etat());
        r.set("lots_A", lots(CENTRE_A));
        r.set("lots_B", lots(CENTRE_B));

        // Le SUPERADMIN est arrete par le filtre de portee des roles avant le controleur.
        RequestPostProcessor superadmin = qui(CENTRE_A, SUPER, "SUPERADMIN");
        ObjectNode superAdmin = mapper.createObjectNode();
        superAdmin.set("GET batches", rendu(envoyer(get(BASE + "/batches"), superadmin)));
        superAdmin.set("POST batches centerId=B", rendu(envoyerJson(post(BASE + "/batches"), superadmin,
                "{'libelle':'Lot B','centerId':'" + CENTRE_B + "'}")));
        r.set("superadmin", superAdmin);
        golden("droits_et_garde_de_centre", r);
    }

    @Test
    void l_annulation_supprime_ce_que_le_lot_a_cree_et_conserve_ce_qu_il_a_seulement_mis_a_jour() throws Exception {
        UUID lot = ouvrirLot(adminA());
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, created_at) VALUES ('ZR1', ?, 'EXISTANT', CURRENT_TIMESTAMP)",
                CENTRE_A);
        viderLesCaches();
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A1;ZR1;MIS-A-JOUR;;;;;;;", "A2;ZR2;NOUVEAU;Karim;M;;;;;"));
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS,
                "P1;ZR111;BENALI;Karim;M;1962-03-15;2019-01-02;;Fils;ZR2",
                "P2;ZR222;KACI;Nadia;F;1970-05-20;2020-06-01;;Assuré;"));
        importerOk(lot, "affectations", fichier(ENTETE_AFFECTATIONS, ";P1;ZR1;2018-01-01;2018-12-31;non"));
        importerOk(lot, "soldes-ouverture", fichier(ENTETE_SOLDES, "F1;P1;2026-01-31;;;1000;200;;"));
        ObjectNode r = mapper.createObjectNode();
        r.set("0_etat_avant", etat());
        r.set("0_factures_avant", etatFactures(CENTRE_A));
        r.set("1_annulation", rendu(annuler(lot, adminA())));
        r.set("1_etat", etat());
        r.set("1_factures", etatFactures(CENTRE_A));
        r.set("1_lots", lots(CENTRE_A));
        // Un nouveau lot peut reprendre les memes fichiers (caches vides apres l'annulation).
        UUID lot2 = ouvrirLot(adminA());
        r.set("2_reprise_apres_annulation_assures", rendu(importer(lot2, "assures",
                fichier(ENTETE_ASSURES, "A2;ZR2;NOUVEAU;Karim;M;;;;;"), false)));
        r.set("2_reprise_apres_annulation_patients", rendu(importer(lot2, "patients",
                fichier(ENTETE_PATIENTS, "P1;ZR111;BENALI;Karim;M;1962-03-15;2019-01-02;;Fils;ZR2"), false)));
        r.set("2_etat", etat());
        golden("annulation_supprime_ce_que_le_lot_a_cree", r);
    }

    @Test
    void l_annulation_est_refusee_tant_que_des_donnees_reprises_sont_utilisees_ailleurs() throws Exception {
        UUID lot = ouvrirLot(adminA());
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A1;ZB1;BENALI;Ahmed;M;;;;;"));
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS, "P1;ZB111;BENALI;Karim;M;1962-03-15;2019-01-02;;Fils;ZB1"));
        UUID patient = jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? AND numero_assurance = 'ZB111'", UUID.class,
                CENTRE_A);
        ObjectNode r = mapper.createObjectNode();
        // (a) un dossier saisi dans la plateforme pour le patient repris.
        UUID pec = UUID.fromString("99997800-0000-0000-0000-00000000c001");
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, statut) VALUES (?, ?, ?, 'VALIDEE')", pec, CENTRE_A, patient);
        r.set("a_prise_en_charge_du_patient", rendu(annuler(lot, adminA())));
        r.set("a_etat", etat());
        jdbc.update("DELETE FROM prise_en_charge WHERE id = ?", pec);
        // (b) un patient saisi dans la plateforme rattache a l'assure repris.
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "etat_patient, assure_numero_assurance, created_at) VALUES (?, ?, 'EXTERNE', 'Paul', 'M', DATE '2018-01-01', "
                        + "'ZB-EXT', 'NON_VACANCIER', 'PERMANENT', 'ZB1', CURRENT_TIMESTAMP)",
                UUID.fromString("99997800-0000-0000-0000-00000000c002"), CENTRE_A);
        viderLesCaches();
        r.set("b_assure_rattache_a_un_patient_existant", rendu(annuler(lot, adminA())));
        r.set("b_etat", etat());
        jdbc.update("DELETE FROM patients WHERE numero_assurance = 'ZB-EXT' AND center_id = ?", CENTRE_A);
        viderLesCaches();
        // (c) plus rien ne bloque ; un lot annule ou inconnu ne s'annule pas.
        r.set("c_annulation", rendu(annuler(lot, adminA())));
        r.set("c_etat", etat());
        r.set("c_lots", lots(CENTRE_A));
        r.set("d_annuler_un_lot_annule", rendu(annuler(lot, adminA())));
        r.set("d_cloturer_un_lot_annule", rendu(envoyer(post(BASE + "/batches/{id}/close", lot), adminA())));
        r.set("d_annuler_un_lot_inconnu", rendu(annuler(UUID.fromString("99997800-0000-0000-0000-00000000dead"), adminA())));
        golden("annulation_refusee_si_donnees_utilisees", r);
    }

    @Test
    void les_erreurs_generales_de_fichier_et_de_lot_sont_rapportees_sans_rien_ecrire() throws Exception {
        UUID lot = ouvrirLot(adminA());
        ObjectNode r = mapper.createObjectNode();
        r.set("fichier_vide", rendu(importerFichier(lot, "patients", "vide.csv", new byte[0], false, adminA(), null)));
        r.set("en_tetes_seuls", rendu(importer(lot, "patients", ENTETE_PATIENTS + "\n", false)));
        r.set("colonnes_obligatoires_absentes", rendu(importer(lot, "patients", fichier("legacyId;nom", "P1;BENALI"), false)));
        r.set("colonne_inconnue_ignoree", rendu(importer(lot, "assures", fichier(ENTETE_ASSURES + ";colonneInconnue",
                "A1;ZG1;BENALI;Ahmed;M;;;;;;x"), true)));
        r.set("donnee_inconnue", rendu(importer(lot, "inconnu", fichier(ENTETE_ASSURES, "A1;ZG1;BENALI;Ahmed;M;;;;;"), false)));
        r.set("extension_txt", rendu(importerFichier(lot, "assures", "import.txt",
                fichier(ENTETE_ASSURES, "A1;ZG1;BENALI;Ahmed;M;;;;;").getBytes(StandardCharsets.UTF_8), true, adminA(), null)));
        StringBuilder gros = new StringBuilder(ENTETE_ASSURES).append('\n');
        for (int i = 1; i <= 5001; i++) gros.append("A").append(i).append(";Z").append(i).append(";N;;;;;;;\n");
        r.set("trop_de_lignes", rendu(importer(lot, "assures", gros.toString(), true)));
        r.set("lot_liste", rendu(envoyer(get(BASE + "/batches"), adminA())));
        r.set("lot_deja_ouvert", rendu(envoyerJson(post(BASE + "/batches"), adminA(), "{'libelle':'Deuxieme'}")));
        r.set("lot_sans_libelle", rendu(envoyerJson(post(BASE + "/batches"), adminA(), "{'libelle':' '}")));
        r.set("lot_cloture_puis_import", mapper.createObjectNode()
                .set("close", rendu(envoyer(post(BASE + "/batches/{id}/close", lot), adminA()))));
        ((ObjectNode) r.get("lot_cloture_puis_import")).set("import", rendu(importer(lot, "assures",
                fichier(ENTETE_ASSURES, "A1;ZG1;BENALI;Ahmed;M;;;;;"), false)));
        r.set("etat_final", etat());
        r.set("lots_finaux", lots(CENTRE_A));
        golden("fichier_et_lot_erreurs_generales", r);
    }

    @Test
    void les_correspondances_de_valeurs_refusent_les_colonnes_et_cibles_invalides_et_les_modeles_de_fichier_sont_servis() throws Exception {
        ouvrirLot(adminA());
        ObjectNode r = mapper.createObjectNode();
        r.set("colonne_non_enumeree", rendu(envoyerJson(put(BASE + "/value-mappings"), adminA(),
                "{'column':'nom','source':'X','target':'Y'}")));
        r.set("cible_invalide", rendu(envoyerJson(put(BASE + "/value-mappings"), adminA(),
                "{'column':'etatPatient','source':'D','target':'XYZ'}")));
        r.set("source_sans_caractere_utile", rendu(envoyerJson(put(BASE + "/value-mappings"), adminA(),
                "{'column':'etatPatient','source':'---','target':'DECEDE'}")));
        r.set("corps_incomplet", rendu(envoyerJson(put(BASE + "/value-mappings"), adminA(), "{'column':'etatPatient'}")));
        r.set("enregistrement_normalise_la_source", rendu(envoyerJson(put(BASE + "/value-mappings"), adminA(),
                "{'column':'qualiteAssure','source':'  Ayant droit (fils) ','target':'ENFANT'}")));
        r.set("liste", rendu(envoyer(get(BASE + "/value-mappings"), adminA())));
        for (String modele : new String[]{"assures", "patients", "affectations", "soldes-ouverture"}) {
            r.set("modele_csv_" + modele, rendu(envoyer(get(BASE + "/entities/" + modele + "/template").param("format", "csv"),
                    adminA())));
        }
        golden("correspondances_de_valeurs_et_modeles", r);
    }
}
