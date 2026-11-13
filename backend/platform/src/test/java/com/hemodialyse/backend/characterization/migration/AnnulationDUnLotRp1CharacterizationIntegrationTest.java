package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Filet RP1 (ADR 0169) : l'annulation d'un lot de reprise ({@code POST /api/v1/admin/migration/batches/{id}/cancel}) AVANT que la
 * suppression de {@code patients}, {@code assure} et {@code assure_patient} passe par le langage publie de Patient et couverture.
 * Complete les goldens S6a (nominal, lot inconnu ou deja annule, autre centre, role, prise en charge, assure rattache a un patient
 * existant) par les cas que RP1 deplace : donnees hors lot qui bloquent (ordre des controles), patient modifie depuis l'import,
 * assure rattache par une affectation, donnees voisines intactes. Schema de test H2 uniquement.
 */
class AnnulationDUnLotRp1CharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final UUID EXTERNE = UUID.fromString("99997800-0000-0000-0000-00000000c101");
    private static final UUID EXTERNE_B = UUID.fromString("99997800-0000-0000-0000-00000000c102");

    @AfterEach
    void nettoyerLesDonneesVoisines() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : List.of("seances", "attestation_droit", "dossier_medical_patient", "allergies_patient")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    private UUID patient(String numero) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? AND numero_assurance = ?", UUID.class, CENTRE_A,
                numero);
    }

    private void lotAvecUnPatient(UUID lot) throws Exception {
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A1;ZP1;BENALI;Ahmed;M;;;;;"));
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS, "P1;ZP111;BENALI;Karim;M;1962-03-15;2019-01-02;;Fils;ZP1"));
    }

    private ObjectNode tenterEtRetirer(String cle, ObjectNode r, UUID lot, Runnable retrait) throws Exception {
        r.set(cle, rendu(annuler(lot, adminA())));
        retrait.run();
        viderLesCaches();
        return r;
    }

    private void insererSeance(UUID id, UUID patient) {
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, DATE '2020-01-02', "
                + "'PLANIFIEE', ?)", id, patient, CENTRE_A, OffsetDateTime.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void l_annulation_est_refusee_pour_un_patient_repris_qui_a_des_attestations_des_seances_ou_un_dossier() throws Exception {
        UUID lot = ouvrirLot(adminA());
        lotAvecUnPatient(lot);
        UUID patient = patient("ZP111");
        ObjectNode r = mapper.createObjectNode();

        UUID attestation = UUID.fromString("99997800-0000-0000-0000-00000000c201");
        jdbc.update("INSERT INTO attestation_droit (id, center_id, date_debut, date_fin, patient_id) VALUES (?, ?, DATE '2020-01-01', "
                + "DATE '2020-12-31', ?)", attestation, CENTRE_A, patient);
        tenterEtRetirer("a_attestation_hors_lot", r, lot,
                () -> jdbc.update("DELETE FROM attestation_droit WHERE id = ?", attestation));

        UUID seance = UUID.fromString("99997800-0000-0000-0000-00000000c202");
        insererSeance(seance, patient);
        tenterEtRetirer("b_seance_du_patient", r, lot, () -> jdbc.update("DELETE FROM seances WHERE id = ?", seance));

        UUID dossier = UUID.fromString("99997800-0000-0000-0000-00000000c203");
        jdbc.update("INSERT INTO dossier_medical_patient (id, center_id, patient_id, created_at) VALUES (?, ?, ?, ?)", dossier, CENTRE_A,
                patient, OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        tenterEtRetirer("c_dossier_medical", r, lot, () -> { });

        // Plusieurs donnees a la fois : le message nomme la premiere table controlee (ordre de la liste), pas la derniere.
        UUID pec = UUID.fromString("99997800-0000-0000-0000-00000000c204");
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, statut) VALUES (?, ?, ?, 'VALIDEE')", pec, CENTRE_A, patient);
        insererSeance(seance, patient);
        tenterEtRetirer("d_prise_en_charge_seance_et_dossier", r, lot, () -> { });
        r.set("d_etat", etat());
        jdbc.update("DELETE FROM prise_en_charge WHERE id = ?", pec);
        tenterEtRetirer("e_seance_et_dossier", r, lot, () -> jdbc.update("DELETE FROM seances WHERE id = ?", seance));
        tenterEtRetirer("f_dossier_seul", r, lot, () -> jdbc.update("DELETE FROM dossier_medical_patient WHERE id = ?", dossier));
        viderLesCaches();
        r.set("g_annulation_une_fois_le_patient_libre", rendu(annuler(lot, adminA())));
        r.set("g_etat", etat());
        r.set("g_lots", lots(CENTRE_A));
        golden("annulation_rp1_donnees_hors_lot_et_ordre_des_controles", r);
    }

    @Test
    void l_annulation_supprime_un_patient_repris_meme_modifie_depuis_l_import_et_laisse_les_donnees_voisines() throws Exception {
        UUID lot = ouvrirLot(adminA());
        lotAvecUnPatient(lot);
        // Donnees voisines : un patient et un assure du centre A hors lot, un patient et un assure du centre B.
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, created_at) VALUES ('ZV-A', ?, 'VOISIN-A', CURRENT_TIMESTAMP)",
                CENTRE_A);
        jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, created_at) VALUES ('ZV-B', ?, 'VOISIN-B', CURRENT_TIMESTAMP)",
                CENTRE_B);
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "etat_patient, created_at) VALUES (?, ?, 'EXTERNE', 'Paul', 'M', DATE '2018-01-01', 'ZV-EXT-A', "
                        + "'NON_VACANCIER', 'PERMANENT', CURRENT_TIMESTAMP)", EXTERNE, CENTRE_A);
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "etat_patient, created_at) VALUES (?, ?, 'EXTERNE', 'Luc', 'M', DATE '2018-01-01', 'ZV-EXT-B', "
                        + "'NON_VACANCIER', 'PERMANENT', CURRENT_TIMESTAMP)", EXTERNE_B, CENTRE_B);
        // Le patient repris est modifie dans la plateforme apres l'import : identite, etat, sommeil, place.
        jdbc.update("UPDATE patients SET nom = 'MODIFIE', prenom = 'Karima', etat_patient = 'DECEDE', en_sommeil = true, "
                + "sous_kt = true, observation = 'Saisie dans la plateforme' WHERE center_id = ? AND numero_assurance = 'ZP111'", CENTRE_A);
        viderLesCaches();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_etat_avant_centre_A", etat());
        r.set("0_etat_avant_centre_B", etat(CENTRE_B));
        r.set("1_annulation", rendu(annuler(lot, adminA())));
        r.set("1_etat_centre_A", etat());
        r.set("1_etat_centre_B", etat(CENTRE_B));
        r.set("1_lots", lots(CENTRE_A));
        golden("annulation_rp1_patient_modifie_depuis_l_import_et_donnees_voisines", r);
    }

    @Test
    void l_annulation_est_refusee_si_un_assure_repris_est_rattache_par_une_affectation_a_un_patient_hors_lot() throws Exception {
        UUID lot = ouvrirLot(adminA());
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A1;ZL1;BENALI;Ahmed;M;;;;;", "A2;ZL2;KACI;Samir;M;;;;;"));
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "etat_patient, created_at) VALUES (?, ?, 'EXTERNE', 'Paul', 'M', DATE '2018-01-01', 'ZL-EXT', "
                        + "'NON_VACANCIER', 'PERMANENT', CURRENT_TIMESTAMP)", EXTERNE, CENTRE_A);
        UUID affectation = UUID.fromString("99997800-0000-0000-0000-00000000c301");
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation) "
                + "VALUES (?, ?, 'ZL1', ?, true, CURRENT_TIMESTAMP)", affectation, EXTERNE, CENTRE_A);
        viderLesCaches();
        ObjectNode r = mapper.createObjectNode();
        r.set("a_assure_rattache_par_une_affectation_hors_lot", rendu(annuler(lot, adminA())));
        r.set("a_etat", etat());
        jdbc.update("DELETE FROM assure_patient WHERE id = ?", affectation);
        viderLesCaches();
        r.set("b_annulation_apres_retrait_de_l_affectation", rendu(annuler(lot, adminA())));
        r.set("b_etat", etat());
        golden("annulation_rp1_assure_rattache_par_une_affectation", r);
    }
}
