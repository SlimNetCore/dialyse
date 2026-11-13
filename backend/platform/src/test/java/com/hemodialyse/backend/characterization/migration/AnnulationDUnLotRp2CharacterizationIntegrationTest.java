package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet RP2 (ADR 0170) : l'annulation d'un lot de reprise ({@code POST /api/v1/admin/migration/batches/{id}/cancel}) AVANT que la lecture
 * « volet complete » (Seance) et la suppression / le controle des donnees du dossier medical et des analyses passent par leurs langages
 * publies. Complete les goldens S6a, RP1 et SE7e (nominal, ordre des controles pour le dossier, autre centre, roles, seance facturee ou
 * completee par le volet paramedical) par les cas que RP2 deplace : donnees du lot supprimees par nature (dossier, antecedents,
 * serologies, abords, analyses, seances) et donnees voisines conservees (autre patient du centre, autre centre) ; donnees medicales
 * hors lot qui bloquent, ordre de la liste ; volet medical ou deux volets sur une seance reprise. Schema de test H2 uniquement.
 */
class AnnulationDUnLotRp2CharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final UUID EXTERNE = UUID.fromString("99997800-0000-0000-0000-00000000d101");
    private static final UUID EXTERNE_B = UUID.fromString("99997800-0000-0000-0000-00000000d102");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T10:00:00Z");

    private static final String ENTETE_DOSSIERS = "patient;dateMiseEnDialyse;nephropathieInitiale";
    private static final String ENTETE_ANTECEDENTS = "legacyId;patient;typeAntecedent;libelle;dateDebut";
    private static final String ENTETE_SEROLOGIES = "legacyId;patient;marqueur;resultat;datePrelevement";
    private static final String ENTETE_ABORDS = "legacyId;patient;typeAbord;cote";
    private static final String ENTETE_ANALYSES = "legacyId;patient;datePrelevement;hbGDl";
    private static final String ENTETE_SEANCES = "legacyId;patient;dateSeance;facturee";

    private static final List<String> TABLES_DU_JEU = List.of("volet_medical", "volet_paramedical", "seances", "dossier_medical_patient",
            "antecedents_medicaux", "serologies_patient", "abords_vasculaires", "resultats_analyses", "allergies_patient");

    /** Le contexte Spring et la base H2 sont partages avec d'autres tests qui laissent des volets ou des analyses sous ces centres. */
    @BeforeEach
    void partirSansDonneesMedicales() {
        nettoyerLesDonneesMedicales();
    }

    @AfterEach
    void nettoyerLesDonneesMedicales() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            for (String table : TABLES_DU_JEU) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    // ------------------------------------------------------------------ fixtures

    private UUID patient(String numero) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? AND numero_assurance = ?", UUID.class, CENTRE_A, numero);
    }

    private void lotAvecUnPatient(UUID lot) throws Exception {
        importerOk(lot, "assures", fichier(ENTETE_ASSURES, "A1;ZM1;BENALI;Ahmed;M;;;;;"));
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS, "P1;ZM111;BENALI;Karim;M;1962-03-15;2019-01-02;;Fils;ZM1"));
    }

    private void donneesMedicalesDuLot(UUID lot) throws Exception {
        importerOk(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS, "P1;2018-06-15;Nephropathie diabetique"));
        importerOk(lot, "antecedents", fichier(ENTETE_ANTECEDENTS, "AN1;P1;MEDICAL;Diabete de type 2;2010-01-01"));
        importerOk(lot, "serologies", fichier(ENTETE_SEROLOGIES, "SE1;P1;AG_HBS;NEGATIF;2026-01-15"));
        importerOk(lot, "abords-vasculaires", fichier(ENTETE_ABORDS, "AB1;P1;FAV;GAUCHE"));
        importerOk(lot, "analyses", fichier(ENTETE_ANALYSES, "AL1;P1;2026-01-05;10,8"));
    }

    private void insererPatientExterne(UUID id, UUID centre, String numero) {
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                + "etat_patient, created_at) VALUES (?, ?, 'EXTERNE', 'Paul', 'M', DATE '2018-01-01', ?, 'NON_VACANCIER', "
                + "'PERMANENT', CURRENT_TIMESTAMP)", id, centre, numero);
    }

    private void insererAnalyse(UUID patient, UUID centre, String date) {
        jdbc.update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, hb_g_dl, created_at) VALUES (?, ?, ?, "
                + "DATE '" + date + "', 9.5, ?)", UUID.randomUUID(), patient, centre, T0);
    }

    private void insererAntecedent(UUID patient, UUID centre) {
        jdbc.update("INSERT INTO antecedents_medicaux (id, center_id, created_at, date_debut, libelle_libre, patient_id, statut_clinique, "
                + "type_antecedent, updated_at) VALUES (?, ?, ?, DATE '2005-01-01', 'HORS LOT', ?, 'ACTIF', 'MEDICAL', ?)",
                UUID.randomUUID(), centre, T0, patient, T0);
    }

    private void insererAllergie(UUID patient, UUID centre) {
        jdbc.update("INSERT INTO allergies_patient (id, categorie, center_id, created_at, criticite, date_constatation, patient_id, "
                + "statut_verification, substance_code, type_reaction, updated_at) VALUES (?, 'MEDICAMENT', ?, ?, 'FAIBLE', "
                + "DATE '2020-01-01', ?, 'CONFIRMEE', 'PENICILLINE', 'ALLERGIE', ?)", UUID.randomUUID(), centre, T0, patient, T0);
    }

    private void insererSerologie(UUID patient, UUID centre) {
        jdbc.update("INSERT INTO serologies_patient (id, center_id, created_at, date_prelevement, marqueur, patient_id, resultat, "
                + "updated_at) VALUES (?, ?, ?, DATE '2020-01-01', 'AC_HBS', ?, 'POSITIF', ?)", UUID.randomUUID(), centre, T0, patient, T0);
    }

    private void insererDossier(UUID patient, UUID centre) {
        jdbc.update("INSERT INTO dossier_medical_patient (id, center_id, patient_id, created_at) VALUES (?, ?, ?, ?)", UUID.randomUUID(),
                centre, patient, T0);
    }

    private void insererVolet(String table, UUID seance, UUID centre) {
        jdbc.update("INSERT INTO " + table + " (id, seance_id, center_id, created_at) VALUES (?, ?, ?, ?)", UUID.randomUUID(), seance,
                centre, T0);
    }

    private UUID seanceDuLot(String date) {
        return jdbc.queryForObject("SELECT id FROM seances WHERE center_id = ? AND date_seance = DATE '" + date + "'", UUID.class, CENTRE_A);
    }

    // ------------------------------------------------------------------ observation

    private static void ajouter(ObjectNode o, String cle, ArrayNode lignes) {
        if (!lignes.isEmpty()) o.set(cle, lignes);
    }

    /** Donnees medicales et seances d'un centre (tout ce que l'annulation peut supprimer), sans identifiant propre. */
    private ObjectNode medical(UUID centre) {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        // tri par numero d'assurance du patient (les identifiants de patient sont tires au hasard)
        String parPatient = "ORDER BY (SELECT p.numero_assurance FROM patients p WHERE p.id = patient_id)";
        ajouter(o, "dossier_medical_patient", lignes("SELECT patient_id, date_mise_en_dialyse FROM dossier_medical_patient WHERE center_id = ? "
                + parPatient, centre));
        ajouter(o, "antecedents_medicaux", lignes("SELECT patient_id, libelle_libre, type_antecedent FROM antecedents_medicaux "
                + "WHERE center_id = ? " + parPatient + ", libelle_libre", centre));
        ajouter(o, "allergies_patient", lignes("SELECT patient_id, substance_code FROM allergies_patient WHERE center_id = ? "
                + parPatient, centre));
        ajouter(o, "serologies_patient", lignes("SELECT patient_id, marqueur, resultat FROM serologies_patient WHERE center_id = ? "
                + parPatient + ", marqueur", centre));
        ajouter(o, "abords_vasculaires", lignes("SELECT patient_id, type_abord, cote FROM abords_vasculaires WHERE center_id = ? "
                + parPatient + ", type_abord", centre));
        ajouter(o, "resultats_analyses", lignes("SELECT patient_id, date_prelevement, hb_g_dl FROM resultats_analyses WHERE center_id = ? "
                + parPatient + ", date_prelevement", centre));
        ajouter(o, "seances", lignes("SELECT patient_id, date_seance, statut FROM seances WHERE center_id = ? "
                + parPatient + ", date_seance", centre));
        ajouter(o, "volets", lignes("SELECT 'medical' AS volet, COUNT(*) AS lignes FROM volet_medical WHERE center_id = ? HAVING COUNT(*) > 0 "
                + "UNION ALL SELECT 'paramedical', COUNT(*) FROM volet_paramedical WHERE center_id = ? HAVING COUNT(*) > 0", centre, centre));
        return o;
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    void l_annulation_supprime_les_donnees_medicales_et_les_seances_du_lot_et_laisse_celles_des_autres_patients_et_centres()
            throws Exception {
        UUID lot = ouvrirLot(adminA());
        lotAvecUnPatient(lot);
        donneesMedicalesDuLot(lot);
        importerOk(lot, "seances", fichier(ENTETE_SEANCES, "S1;P1;2026-01-05;non", "S2;P1;2026-01-07;non"));
        // Donnees voisines : un patient du centre A hors lot et un patient du centre B portent les memes natures de donnees.
        insererPatientExterne(EXTERNE, CENTRE_A, "ZM-EXT-A");
        insererPatientExterne(EXTERNE_B, CENTRE_B, "ZM-EXT-B");
        for (UUID[] p : List.of(new UUID[]{EXTERNE, CENTRE_A}, new UUID[]{EXTERNE_B, CENTRE_B})) {
            insererDossier(p[0], p[1]);
            insererAntecedent(p[0], p[1]);
            insererAllergie(p[0], p[1]);
            insererSerologie(p[0], p[1]);
            insererAnalyse(p[0], p[1], "2026-02-02");
            jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at) VALUES (?, ?, ?, DATE '2026-03-03', "
                    + "'VALIDEE', ?)", UUID.randomUUID(), p[0], p[1], T0);
        }
        viderLesCaches();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_avant_centre_A", medical(CENTRE_A));
        r.set("0_avant_centre_B", medical(CENTRE_B));
        r.set("1_annulation", rendu(annuler(lot, adminA())));
        r.set("1_apres_centre_A", medical(CENTRE_A));
        r.set("1_apres_centre_B", medical(CENTRE_B));
        r.set("1_lots", lots(CENTRE_A));
        golden("annulation_rp2_donnees_du_lot_supprimees_et_donnees_voisines", r);
    }

    @Test
    void l_annulation_est_refusee_par_une_donnee_medicale_hors_lot_et_nomme_la_premiere_nature_controlee() throws Exception {
        UUID lot = ouvrirLot(adminA());
        lotAvecUnPatient(lot);
        donneesMedicalesDuLot(lot);
        UUID patient = patient("ZM111");
        ObjectNode r = mapper.createObjectNode();
        r.set("0_avant", medical(CENTRE_A));

        // une analyse posee dans la plateforme pour le patient repris : seule, elle bloque ; celles du lot ne bloquent pas
        insererAnalyse(patient, CENTRE_A, "2026-04-04");
        r.set("a_analyse_hors_lot", rendu(annuler(lot, adminA())));
        r.set("a_etat", medical(CENTRE_A));

        // plusieurs natures hors lot : le refus nomme la premiere de la liste (antecedents avant analyses), pas la derniere
        insererAntecedent(patient, CENTRE_A);
        r.set("b_antecedent_et_analyse_hors_lot", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM antecedents_medicaux WHERE center_id = ? AND libelle_libre = 'HORS LOT'", CENTRE_A);

        insererAllergie(patient, CENTRE_A);
        insererSerologie(patient, CENTRE_A);
        r.set("c_allergie_serologie_et_analyse_hors_lot", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM allergies_patient WHERE center_id = ?", CENTRE_A);
        r.set("d_serologie_et_analyse_hors_lot", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM serologies_patient WHERE center_id = ? AND marqueur = 'AC_HBS'", CENTRE_A);
        r.set("e_analyse_hors_lot_seule", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ? AND date_prelevement = DATE '2026-04-04'", CENTRE_A);

        // la meme donnee posee au centre B pour le meme identifiant de patient n'est pas controlee autrement : elle bloque aussi (KB-S6a-9)
        insererAnalyse(patient, CENTRE_B, "2026-04-05");
        r.set("f_analyse_du_meme_patient_posee_dans_un_autre_centre", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ?", CENTRE_B);
        viderLesCaches();
        r.set("g_annulation_une_fois_le_patient_libre", rendu(annuler(lot, adminA())));
        r.set("g_apres", medical(CENTRE_A));
        r.set("g_lots", lots(CENTRE_A));
        golden("annulation_rp2_donnees_medicales_hors_lot_et_ordre_des_controles", r);
    }

    @Test
    void l_annulation_est_refusee_par_un_volet_medical_ou_deux_volets_d_une_seance_reprise_et_nomme_facturee_avant_completee()
            throws Exception {
        UUID lot = ouvrirLot(adminA());
        lotAvecUnPatient(lot);
        importerOk(lot, "seances", fichier(ENTETE_SEANCES, "S1;P1;2026-01-05;non", "S2;P1;2026-01-07;non", "S3;P1;2026-01-09;non"));
        UUID medicale = seanceDuLot("2026-01-05");
        UUID deuxVolets = seanceDuLot("2026-01-07");
        UUID libre = seanceDuLot("2026-01-09");
        nommer(medicale, "<SEANCE_VOLET_MEDICAL>");
        nommer(deuxVolets, "<SEANCE_DEUX_VOLETS_ET_FACTUREE>");
        nommer(libre, "<SEANCE_LIBRE>");
        ObjectNode r = mapper.createObjectNode();

        insererVolet("volet_medical", medicale, CENTRE_A);
        r.set("a_volet_medical", rendu(annuler(lot, adminA())));

        // un seul bloquant a la fois : l'ordre de plusieurs bloquants depend de l'ordre des correspondances (sans departage)
        jdbc.update("DELETE FROM volet_medical WHERE seance_id = ?", medicale);
        insererVolet("volet_medical", deuxVolets, CENTRE_A);
        insererVolet("volet_paramedical", deuxVolets, CENTRE_A);
        r.set("b_une_seance_aux_deux_volets", rendu(annuler(lot, adminA())));

        // une seance facturee ET completee : le motif est « facturee »
        jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", UUID.fromString("99997800-0000-0000-0000-00000000d201"), deuxVolets);
        r.set("c_seance_facturee_et_completee", rendu(annuler(lot, adminA())));

        // un volet d'une seance d'un autre centre pour un identifiant sans rapport ne bloque pas
        insererVolet("volet_medical", UUID.randomUUID(), CENTRE_B);
        jdbc.update("DELETE FROM volet_paramedical WHERE seance_id = ?", deuxVolets);
        jdbc.update("DELETE FROM volet_medical WHERE seance_id = ?", deuxVolets);
        jdbc.update("UPDATE seances SET facture_id = NULL WHERE id = ?", deuxVolets);
        r.set("d_avant_annulation", medical(CENTRE_A));
        viderLesCaches();
        r.set("d_annulation_une_fois_les_volets_retires", rendu(annuler(lot, adminA())));
        r.set("d_apres_centre_A", medical(CENTRE_A));
        r.set("d_apres_centre_B", medical(CENTRE_B));
        r.set("d_lots", lots(CENTRE_A));
        golden("annulation_rp2_volet_complete_et_seance_facturee", r);
    }
}
