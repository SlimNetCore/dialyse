package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet RP3a (ADR 0171) : l'import des attestations de droits et des prises en charge de la reprise historique
 * ({@code POST /api/v1/admin/migration/batches/{id}/import/{attestations|prises-en-charge}}) et leur annulation, AVANT que
 * {@code HistoricalRecordJdbcAdapter} et {@code MigrationRollbackJdbcAdapter} passent par le langage publie de Patient et couverture.
 * Aucun golden n'existait pour ces deux donnees (seuls les patients, assures, affectations et soldes etaient caracterises) : controles et
 * formats limites du fichier, doublons, rejeu et mise a jour, ligne d'un autre centre, roles (403 avant 400), annulation. Observation :
 * reponses HTTP et etat des tables {@code attestation_droit} et {@code prise_en_charge}. Schema de test H2 uniquement.
 */
class CouvertureHistoriqueRp3aCharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final String ENTETE_ATTESTATIONS = "legacyId;patient;dateDebut;dateFin";
    private static final String ENTETE_PEC =
            "legacyId;patient;dateDebutDemande;dateFinDemande;forfaitDemande;statut;dateDebutEffectif;dateFinEffectif;forfaitEffectif";
    private static final UUID FORFAIT_A1 = UUID.fromString("99997800-0000-0000-0000-00000000f0a1");
    private static final UUID FORFAIT_A2 = UUID.fromString("99997800-0000-0000-0000-00000000f0a2");
    private static final UUID FORFAIT_B1 = UUID.fromString("99997800-0000-0000-0000-00000000f0b1");
    private static final UUID PATIENT_B = UUID.fromString("99997800-0000-0000-0000-00000000e0b1");

    @BeforeEach
    void partirSansCouverture() {
        nettoyerLaCouverture();
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'HD-CONV', 'Hemodialyse', 5600)", FORFAIT_A1, CENTRE_A);
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'HD-NUIT', 'Nuit', 6000)", FORFAIT_A2, CENTRE_A);
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, 'HD-B', 'Autre centre', 7000)", FORFAIT_B1, CENTRE_B);
        nommer(FORFAIT_A1, "<FORFAIT_HD_CONV>");
        nommer(FORFAIT_A2, "<FORFAIT_HD_NUIT>");
        nommer(FORFAIT_B1, "<FORFAIT_HD_B>");
        viderLesCaches();
    }

    @AfterEach
    void nettoyerLaCouverture() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", centre);
        }
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_B);
    }

    // ------------------------------------------------------------------ fixtures

    private UUID ouvrirLotDepuis(String dateDebutReprise, RequestPostProcessor who) throws Exception {
        Resp r = envoyerJson(post(BASE + "/batches"), who,
                "{'libelle':'Reprise RP3a','sourceSystem':'AncienLogiciel','dateDebutReprise':'" + dateDebutReprise + "'}");
        if (r.status() != 201) throw new IllegalStateException("ouverture du lot : " + r.status() + " " + r.raw());
        UUID lot = UUID.fromString(mapper.readTree(r.raw()).path("id").asString());
        nommer(lot, "<LOT>");
        return lot;
    }

    private UUID lotAvecDeuxPatients() throws Exception {
        UUID lot = ouvrirLotDepuis("2023-09-30", adminA());
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS,
                "P1;ZC111;BENALI;Karim;M;1962-03-15;2019-01-02;;;",
                "P2;ZC222;KACI;Nadia;F;1970-05-20;2020-06-01;;;"));
        return lot;
    }

    private UUID patient(String numero) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? AND numero_assurance = ?", UUID.class, CENTRE_A, numero);
    }

    private void insererPatientDuCentreB() {
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                + "etat_patient, created_at) VALUES (?, ?, 'VOISIN', 'Paul', 'M', DATE '2018-01-01', 'ZC-B', 'NON_VACANCIER', "
                + "'PERMANENT', CURRENT_TIMESTAMP)", PATIENT_B, CENTRE_B);
    }

    private void insererAttestation(UUID patient, UUID centre, String debut, String fin) {
        jdbc.update("INSERT INTO attestation_droit (id, center_id, patient_id, date_debut, date_fin, created_at) VALUES (?, ?, ?, DATE '"
                + debut + "', DATE '" + fin + "', CURRENT_TIMESTAMP)", UUID.randomUUID(), centre, patient);
    }

    private void insererPriseEnCharge(UUID patient, UUID centre, String debut, String statut) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, statut, created_at) VALUES (?, ?, ?, DATE '"
                + debut + "', ?, CURRENT_TIMESTAMP)", UUID.randomUUID(), centre, patient, statut);
    }

    // ------------------------------------------------------------------ observation

    private ObjectNode couverture(UUID centre) {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        ArrayNode attestations = lignes("SELECT patient_id, date_debut, date_fin, created_at IS NOT NULL AS cree_horodate "
                + "FROM attestation_droit WHERE center_id = ? ORDER BY (SELECT p.numero_assurance FROM patients p WHERE p.id = patient_id), "
                + "date_debut, date_fin", centre);
        if (!attestations.isEmpty()) o.set("attestation_droit", attestations);
        ArrayNode pecs = lignes("SELECT e.patient_id, e.statut, e.date_debut_demande, e.date_fin_demande, e.date_debut_effectif, "
                + "e.date_fin_effectif, e.forfait_demande_id, e.forfait_effectif_id, e.created_at IS NOT NULL AS cree_horodate "
                + "FROM prise_en_charge e WHERE e.center_id = ? ORDER BY (SELECT p.numero_assurance FROM patients p WHERE p.id = e.patient_id), "
                + "e.date_debut_demande, e.statut", centre);
        if (!pecs.isEmpty()) o.set("prise_en_charge", pecs);
        o.set("correspondances", lignes("SELECT entity, operation FROM migration_id_map WHERE center_id = ? AND entity IN "
                + "('ATTESTATIONS', 'PRISES_EN_CHARGE', 'attestations', 'prises-en-charge') ORDER BY entity, operation", centre));
        return o;
    }

    private ObjectNode importerRendu(UUID lot, String entite, String contenu, boolean dryRun) throws Exception {
        return rendu(importer(lot, entite, contenu, dryRun));
    }

    // ------------------------------------------------------------------ attestations

    @Test
    void les_attestations_sont_controlees_puis_ecrites_sans_doublon_et_rejouees_en_mise_a_jour() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_patient_inconnu", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;INCONNU;2026-01-01;2026-12-31"), false));
        r.set("0_patient_vide", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;;2026-01-01;2026-12-31"), false));
        r.set("0_fin_avant_debut", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;P1;2026-12-31;2026-01-01"), false));
        r.set("0_date_obligatoire_absente", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;P1;2026-01-01;"), false));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS,
                "A1;P1;2026-01-01;2026-12-31", "A2;P1;2026-01-01;2027-12-31", "A3;P2;2026-01-01;2026-12-31"), false));
        r.set("0_anterieure_a_la_periode_reprise", importerRendu(lot, "attestations",
                fichier(ENTETE_ATTESTATIONS, "A1;P1;2022-01-01;2023-06-30"), false));
        r.set("0_etat_sans_ecriture", couverture(CENTRE_A));

        ObjectNode formats = mapper.createObjectNode();
        for (String date : new String[]{"2026-01-01", "01/01/2026", "1/1/2026", "01-01-2026", "2026/01/01", "20260101", "01.01.2026",
                "2026-1-1", "31/02/2026", "2026-13-01", "  01/01/2026  ", "abc", "01/01/26"}) {
            formats.set("dateDebut [" + date + "]", importerRendu(lot, "attestations",
                    fichier(ENTETE_ATTESTATIONS, "A1;P1;" + date + ";2030-12-31"), true));
        }
        r.set("1_formats_de_date_en_simulation", formats);
        r.set("1_etat_apres_simulations", couverture(CENTRE_A));

        r.set("2_import_nominal", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS,
                "A1;P1;2026-01-01;2026-12-31", "A2;P1;01/01/2025;31/12/2025", "A3;P2;2026-03-01;2027-02-28"), false));
        r.set("2_etat", couverture(CENTRE_A));
        r.set("3_rejeu_identique", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS,
                "A1;P1;2026-01-01;2026-12-31", "A2;P1;2025-01-01;2025-12-31", "A3;P2;2026-03-01;2027-02-28"), false));
        r.set("3_etat", couverture(CENTRE_A));
        r.set("4_rejeu_avec_nouvelle_fin_et_une_nouvelle_ligne", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS,
                "A1;P1;2026-01-01;2027-06-30", "A4;P2;2024-01-01;2024-12-31"), false));
        r.set("4_etat", couverture(CENTRE_A));

        // une attestation saisie dans la plateforme pour le meme debut est reprise en mise a jour (la cle est patient + debut)
        UUID p1 = patient("ZC111");
        insererAttestation(p1, CENTRE_A, "2019-05-01", "2019-12-31");
        envoyer(post(BASE + "/batches/{id}/close", lot), adminA());
        UUID lot2 = ouvrirLotDepuis("2018-01-01", adminA());
        r.set("5_attestation_de_la_plateforme_au_meme_debut", importerRendu(lot2, "attestations",
                fichier(ENTETE_ATTESTATIONS, "A9;P1;2019-05-01;2020-04-30"), false));
        r.set("5_etat", couverture(CENTRE_A));
        golden("import_attestations_controles_formats_et_rejeu_rp3a", r);
    }

    @Test
    void les_attestations_d_un_autre_centre_sont_ignorees_et_l_import_est_reserve_aux_administrateurs_du_centre() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        insererPatientDuCentreB();
        UUID p1 = patient("ZC111");
        ObjectNode r = mapper.createObjectNode();
        // meme patient et meme debut, mais au centre B : ne compte pas comme deja presente au centre A
        insererAttestation(p1, CENTRE_B, "2026-01-01", "2026-06-30");
        insererAttestation(PATIENT_B, CENTRE_B, "2026-01-01", "2026-06-30");
        r.set("0_etat_centre_B_avant", couverture(CENTRE_B));
        r.set("1_import_au_centre_A", importerRendu(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;P1;2026-01-01;2026-12-31"), false));
        r.set("1_etat_centre_A", couverture(CENTRE_A));
        r.set("1_etat_centre_B_intact", couverture(CENTRE_B));

        String valide = fichier(ENTETE_ATTESTATIONS, "A2;P2;2026-02-01;2026-12-31");
        String invalide = fichier(ENTETE_ATTESTATIONS, "A2;INCONNU;2026-13-45;");
        ObjectNode roles = mapper.createObjectNode();
        for (String role : new String[]{"TECHNICIEN", "INFIRMIER", "MEDECIN", "SECRETAIRE", "SUPERADMIN"}) {
            RequestPostProcessor who = qui(CENTRE_A, TECH, role);
            roles.set(role + " fichier valide", rendu(importer(lot, "attestations", valide, false, who, null)));
            roles.set(role + " fichier invalide (403 avant 400)", rendu(importer(lot, "attestations", invalide, false, who, null)));
        }
        r.set("2_roles", roles);
        r.set("2_etat_apres_refus", couverture(CENTRE_A));

        ObjectNode centres = mapper.createObjectNode();
        centres.set("admin A vers le centre B (valide)", rendu(importer(lot, "attestations", valide, false, adminA(), CENTRE_B)));
        centres.set("admin B sur le lot de A (valide)", rendu(importer(lot, "attestations", valide, false, adminB(), null)));
        centres.set("admin B sur le lot de A (invalide)", rendu(importer(lot, "attestations", invalide, false, adminB(), null)));
        centres.set("lot inconnu", rendu(importer(UUID.fromString("99997800-0000-0000-0000-00000000dead"), "attestations", valide, false)));
        r.set("3_autres_centres", centres);
        r.set("3_etat_centre_A", couverture(CENTRE_A));
        r.set("3_etat_centre_B", couverture(CENTRE_B));
        golden("import_attestations_autre_centre_et_roles_rp3a", r);
    }

    // ------------------------------------------------------------------ prises en charge

    @Test
    void les_prises_en_charge_sont_controlees_puis_ecrites_avec_les_valeurs_par_defaut_de_la_demande_et_rejouees() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        insererPatientDuCentreB();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_forfait_inconnu", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-INCONNU;VALIDEE;;;"), false));
        r.set("0_forfait_d_un_autre_centre", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-B;VALIDEE;;;"), false));
        r.set("0_forfait_effectif_inconnu", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;;;XXX"), false));
        r.set("0_fin_demandee_avant_debut", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-06-30;2026-01-01;HD-CONV;VALIDEE;;;"), false));
        r.set("0_fin_effective_avant_debut", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;2026-05-01;2026-02-01;"), false));
        r.set("0_statut_inconnu", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;BIZARRE;;;"), false));
        r.set("0_dates_et_forfait_obligatoires_absents", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;;;;VALIDEE;;;"), false));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;;;", "C2;P1;2026-01-01;2026-12-31;HD-NUIT;VALIDEE;;;"), false));
        r.set("0_anterieure_a_la_periode_reprise", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2022-01-01;2023-06-30;HD-CONV;VALIDEE;;;"), false));
        r.set("0_etat_sans_ecriture", couverture(CENTRE_A));

        r.set("1_import_nominal", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;;;",               // accordee : effectif = demande
                "C2;P1;01/07/2026;31/12/2026;hd-nuit ;CREE;;;",                 // creee : pas d'effectif ; code en minuscules et espace
                "C3;P2;2026-01-01;2026-12-31;HD-CONV;CLOTUREE;2026-02-01;2026-09-30;HD-NUIT"), false));   // effectif explicite
        r.set("1_etat", couverture(CENTRE_A));
        r.set("2_rejeu_identique", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;;;"), false));
        r.set("2_etat", couverture(CENTRE_A));
        r.set("3_rejeu_avec_changement_de_statut_de_forfait_et_de_dates", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-09-30;HD-NUIT;CLOTUREE;;;", "C4;P2;2025-01-01;2025-12-31;HD-CONV;VALIDEE;;;"), false));
        r.set("3_etat", couverture(CENTRE_A));

        // meme debut de demande pour le meme patient au centre B : jamais vu depuis le centre A
        insererPriseEnCharge(patient("ZC111"), CENTRE_B, "2024-02-01", "VALIDEE");
        r.set("4_meme_debut_dans_un_autre_centre", importerRendu(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C5;P1;2024-02-01;2024-12-31;HD-CONV;VALIDEE;;;"), false));
        r.set("4_etat_centre_A", couverture(CENTRE_A));
        r.set("4_etat_centre_B", couverture(CENTRE_B));

        ObjectNode roles = mapper.createObjectNode();
        String valide = fichier(ENTETE_PEC, "C6;P2;2024-03-01;2024-12-31;HD-CONV;VALIDEE;;;");
        String invalide = fichier(ENTETE_PEC, "C6;INCONNU;abc;;ZZZ;BIZARRE;;;");
        for (String role : new String[]{"TECHNICIEN", "INFIRMIER"}) {
            RequestPostProcessor who = qui(CENTRE_A, TECH, role);
            roles.set(role + " fichier valide", rendu(importer(lot, "prises-en-charge", valide, false, who, null)));
            roles.set(role + " fichier invalide (403 avant 400)", rendu(importer(lot, "prises-en-charge", invalide, false, who, null)));
        }
        roles.set("admin B sur le lot de A", rendu(importer(lot, "prises-en-charge", valide, false, adminB(), null)));
        r.set("5_roles", roles);
        r.set("5_etat_final", couverture(CENTRE_A));
        golden("import_prises_en_charge_controles_et_rejeu_rp3a", r);
    }

    // ------------------------------------------------------------------ annulation

    @Test
    void l_annulation_supprime_les_attestations_et_les_prises_en_charge_du_lot_et_laisse_les_voisines() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        insererPatientDuCentreB();
        importerOk(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;P1;2026-01-01;2026-12-31", "A2;P2;2026-01-01;2026-12-31"));
        importerOk(lot, "prises-en-charge", fichier(ENTETE_PEC,
                "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;;;", "C2;P2;2026-01-01;2026-06-30;HD-NUIT;CREE;;;"));
        // voisines : attestation et prise en charge d'un patient d'un autre centre (que le lot ne connait pas)
        insererAttestation(PATIENT_B, CENTRE_B, "2026-01-01", "2026-12-31");
        insererPriseEnCharge(PATIENT_B, CENTRE_B, "2026-01-01", "VALIDEE");
        ObjectNode r = mapper.createObjectNode();
        r.set("0_etat_centre_A", couverture(CENTRE_A));
        r.set("0_etat_centre_B", couverture(CENTRE_B));
        r.set("1_annulation", rendu(annuler(lot, adminA())));
        r.set("1_etat_centre_A", couverture(CENTRE_A));
        r.set("1_etat_centre_B_intact", couverture(CENTRE_B));
        r.set("1_patients_restants", lignes("SELECT numero_assurance FROM patients WHERE center_id = ? ORDER BY numero_assurance", CENTRE_A));
        golden("annulation_attestations_et_prises_en_charge_rp3a", r);
    }

    @Test
    void l_annulation_est_refusee_si_une_attestation_ou_une_prise_en_charge_reprise_en_mise_a_jour_reste_au_patient() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        UUID p1 = patient("ZC111");
        // saisies dans la plateforme avant l'import : le lot les met a jour (meme debut) sans les creer
        insererAttestation(p1, CENTRE_A, "2026-01-01", "2026-03-31");
        insererPriseEnCharge(p1, CENTRE_A, "2026-01-01", "CREE");
        importerOk(lot, "attestations", fichier(ENTETE_ATTESTATIONS, "A1;P1;2026-01-01;2026-12-31", "A2;P1;2025-01-01;2025-12-31"));
        importerOk(lot, "prises-en-charge", fichier(ENTETE_PEC, "C1;P1;2026-01-01;2026-06-30;HD-CONV;VALIDEE;;;"));
        ObjectNode r = mapper.createObjectNode();
        r.set("0_etat", couverture(CENTRE_A));
        r.set("1_annulation_bloquee", rendu(annuler(lot, adminA())));
        r.set("1_etat", couverture(CENTRE_A));
        // la plateforme retire ses lignes : plus rien ne bloque, les lignes creees par le lot disparaissent
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", CENTRE_A);
        jdbc.update("DELETE FROM attestation_droit WHERE center_id = ? AND date_debut = DATE '2026-01-01'", CENTRE_A);
        r.set("2_annulation_apres_retrait", rendu(annuler(lot, adminA())));
        r.set("2_etat", couverture(CENTRE_A));
        golden("annulation_couverture_refusee_si_ligne_de_la_plateforme_rp3a", r);
    }
}
