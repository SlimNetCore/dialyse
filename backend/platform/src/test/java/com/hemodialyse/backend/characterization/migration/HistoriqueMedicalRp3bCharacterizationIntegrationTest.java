package com.hemodialyse.backend.characterization.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet RP3b (ADR 0172) : l'import de l'historique medical de la reprise (dossiers medicaux, antecedents, serologies, abords vasculaires,
 * analyses : {@code POST /api/v1/admin/migration/batches/{id}/import/{nature}}) et le controle {@code bons_sortie} de l'annulation d'un lot,
 * AVANT que {@code HistoricalRecordJdbcAdapter} et {@code MigrationRollbackJdbcAdapter} passent par les langages publies du Dossier medical
 * et du Stock. Aucun golden n'existait pour l'import de ces cinq natures (seule leur annulation etait caracterisee par RP2) : controles et
 * formats limites du fichier, doublons, rejeu (mise a jour), cle nulle, ligne d'un autre centre, roles (403 avant 400), bons de sortie.
 * Observation : reponses HTTP et etat des cinq tables. Schema de test H2 uniquement.
 */
class HistoriqueMedicalRp3bCharacterizationIntegrationTest extends HistoriquePatientSupport {

    private static final String ENTETE_DOSSIERS =
            "patient;dateMiseEnDialyse;nephropathieInitiale;hepatiteBStatut;hepatiteCStatut;observationGlobale;conclusionMedicale";
    private static final String ENTETE_ANTECEDENTS =
            "legacyId;patient;typeAntecedent;libelle;codeCim10;dateDebut;dateFin;statutClinique;severite;note";
    private static final String ENTETE_SEROLOGIES = "legacyId;patient;marqueur;resultat;datePrelevement;titre;unite;laboratoire";
    private static final String ENTETE_ABORDS = "legacyId;patient;typeAbord;cote;localisation;dateCreation;dateFin;actif;complications";
    private static final String ENTETE_ANALYSES = "legacyId;patient;datePrelevement;hbGDl;htPct;plaquettes;ferritineNgMl;creatinineMgDl;crpMgL";

    private static final UUID PATIENT_B = UUID.fromString("99997800-0000-0000-0000-00000000e0c1");
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-01-01T10:00:00Z");

    private static final List<String> TABLES_DU_JEU = List.of("dossier_medical_patient", "antecedents_medicaux", "serologies_patient",
            "abords_vasculaires", "resultats_analyses", "bilans_pre_greffe", "bons_sortie");

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
        jdbc.update("DELETE FROM patients WHERE id = ?", PATIENT_B);
    }

    // ------------------------------------------------------------------ fixtures

    private UUID ouvrirLotDepuis(String dateDebutReprise) throws Exception {
        Resp r = envoyerJson(post(BASE + "/batches"), adminA(),
                "{'libelle':'Reprise RP3b','sourceSystem':'AncienLogiciel','dateDebutReprise':'" + dateDebutReprise + "'}");
        if (r.status() != 201) throw new IllegalStateException("ouverture du lot : " + r.status() + " " + r.raw());
        UUID lot = UUID.fromString(mapper.readTree(r.raw()).path("id").asString());
        nommer(lot, "<LOT>");
        return lot;
    }

    private UUID lotAvecDeuxPatients() throws Exception {
        UUID lot = ouvrirLotDepuis("2023-09-30");
        importerOk(lot, "patients", fichier(ENTETE_PATIENTS,
                "P1;ZH111;BENALI;Karim;M;1962-03-15;2019-01-02;;;",
                "P2;ZH222;KACI;Nadia;F;1970-05-20;2020-06-01;;;"));
        return lot;
    }

    private UUID patient(String numero) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE center_id = ? AND numero_assurance = ?", UUID.class, CENTRE_A, numero);
    }

    private void insererPatientDuCentreB() {
        jdbc.update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                + "etat_patient, created_at) VALUES (?, ?, 'VOISIN', 'Paul', 'M', DATE '2018-01-01', 'ZH-B', 'NON_VACANCIER', "
                + "'PERMANENT', CURRENT_TIMESTAMP)", PATIENT_B, CENTRE_B);
    }

    private void insererDossier(UUID patient, UUID centre, String nephropathie) {
        jdbc.update("INSERT INTO dossier_medical_patient (id, center_id, patient_id, nephropathie_initiale, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), centre, patient, nephropathie, T0, T0);
    }

    private void insererAntecedent(UUID patient, UUID centre, String libelle, String debut) {
        jdbc.update("INSERT INTO antecedents_medicaux (id, center_id, created_at, date_debut, libelle_libre, patient_id, statut_clinique, "
                + "type_antecedent, updated_at) VALUES (?, ?, ?, DATE '" + debut + "', ?, ?, 'ACTIF', 'MEDICAL', ?)",
                UUID.randomUUID(), centre, T0, libelle, patient, T0);
    }

    private void insererSerologie(UUID patient, UUID centre, String marqueur, String date) {
        jdbc.update("INSERT INTO serologies_patient (id, center_id, created_at, date_prelevement, marqueur, patient_id, resultat, "
                + "updated_at) VALUES (?, ?, ?, DATE '" + date + "', ?, ?, 'POSITIF', ?)", UUID.randomUUID(), centre, T0, marqueur,
                patient, T0);
    }

    private void insererAbord(UUID patient, UUID centre, String type, String creation) {
        jdbc.update("INSERT INTO abords_vasculaires (id, actif, center_id, created_at, date_creation, patient_id, type_abord) VALUES "
                + "(?, TRUE, ?, ?, " + (creation == null ? "NULL" : "DATE '" + creation + "'") + ", ?, ?)", UUID.randomUUID(), centre, T0,
                patient, type);
    }

    private void insererAnalyse(UUID patient, UUID centre, String date) {
        jdbc.update("INSERT INTO resultats_analyses (id, patient_id, center_id, date_prelevement, hb_g_dl, created_at, updated_at) VALUES "
                + "(?, ?, ?, DATE '" + date + "', 9.5, ?, ?)", UUID.randomUUID(), patient, centre, T0, T0);
    }

    private void insererBilan(UUID patient, UUID centre) {
        jdbc.update("INSERT INTO bilans_pre_greffe (id, center_id, created_at, patient_id, statut, updated_at) VALUES (?, ?, ?, ?, "
                + "'EN_COURS', ?)", UUID.randomUUID(), centre, T0, patient, T0);
    }

    private void insererBonDeSortie(UUID patient, UUID centre, String reference) {
        jdbc.update("INSERT INTO bons_sortie (id, center_id, created_at, date_sortie, patient_id, reference, seance_id) VALUES (?, ?, ?, "
                + "DATE '2026-02-02', ?, ?, ?)", UUID.randomUUID(), centre, T0, patient, reference, UUID.randomUUID());
    }

    // ------------------------------------------------------------------ observation

    private static String parPatient(String suite) {
        return " ORDER BY (SELECT p.numero_assurance FROM patients p WHERE p.id = patient_id)" + suite;
    }

    private static void ajouter(ObjectNode o, String cle, ArrayNode lignes) {
        if (!lignes.isEmpty()) o.set(cle, lignes);
    }

    /** Les cinq natures du lot, colonnes ecrites par l'import comprises, et indicateurs de creation / modification. */
    private ObjectNode medical(UUID centre) {
        nommerLesIdentifiants();
        ObjectNode o = mapper.createObjectNode();
        ajouter(o, "dossier_medical_patient", lignes("SELECT patient_id, date_mise_en_dialyse, nephropathie_initiale, hepatite_b_statut, "
                + "hepatite_c_statut, observation_globale, conclusion_medicale, created_at IS NOT NULL AS cree, "
                + "updated_at IS NOT NULL AS modifie FROM dossier_medical_patient WHERE center_id = ?" + parPatient(""), centre));
        ajouter(o, "antecedents_medicaux", lignes("SELECT patient_id, type_antecedent, libelle_libre, diagnostic_code_system, "
                + "diagnostic_code, date_debut, date_fin, statut_clinique, severite, note, created_at IS NOT NULL AS cree, "
                + "updated_at > created_at AS modifie_apres_creation FROM antecedents_medicaux WHERE center_id = ?"
                + parPatient(", type_antecedent, libelle_libre, date_debut"), centre));
        ajouter(o, "serologies_patient", lignes("SELECT patient_id, marqueur, resultat, date_prelevement, titre, unite, laboratoire, "
                + "created_at IS NOT NULL AS cree, updated_at > created_at AS modifie_apres_creation FROM serologies_patient "
                + "WHERE center_id = ?" + parPatient(", marqueur, date_prelevement"), centre));
        ajouter(o, "abords_vasculaires", lignes("SELECT patient_id, type_abord, cote, localisation, date_creation, date_fin, actif, "
                + "complications, created_at IS NOT NULL AS cree FROM abords_vasculaires WHERE center_id = ?"
                + parPatient(", type_abord, date_creation, localisation"), centre));
        ajouter(o, "resultats_analyses", lignes("SELECT patient_id, date_prelevement, hb_g_dl, ht_pct, plaquettes, ferritine_ng_ml, "
                + "creatinine_mg_dl, crp_mg_l, created_at IS NOT NULL AS cree, updated_at > created_at AS modifie_apres_creation "
                + "FROM resultats_analyses WHERE center_id = ?" + parPatient(", date_prelevement"), centre));
        ajouter(o, "correspondances", lignes("SELECT entity, operation FROM migration_id_map WHERE center_id = ? AND entity IN "
                + "('DOSSIERS_MEDICAUX', 'ANTECEDENTS', 'SEROLOGIES', 'ABORDS_VASCULAIRES', 'ANALYSES') ORDER BY entity, operation, "
                + "legacy_id", centre));
        return o;
    }

    /**
     * L'horloge systeme de Windows avance par paliers (jusqu'a ~16 ms) : deux imports rapides d'une JVM chaude peuvent lire le MEME
     * instant, et {@code updated_at > created_at} (indicateur {@code modifie_apres_creation}) vaudrait alors false pour une ligne
     * rejouee. Chaque import attend donc que l'horloge ait depasse l'instant de la fin de l'import precedent.
     */
    private OffsetDateTime finDeLImportPrecedent = OffsetDateTime.MIN;

    private ObjectNode importerRendu(UUID lot, String entite, String contenu, boolean dryRun) throws Exception {
        while (!OffsetDateTime.now().isAfter(finDeLImportPrecedent)) Thread.onSpinWait();
        ObjectNode rendu = rendu(importer(lot, entite, contenu, dryRun));
        finDeLImportPrecedent = OffsetDateTime.now();
        return rendu;
    }

    // ------------------------------------------------------------------ dossiers medicaux

    @Test
    void les_dossiers_medicaux_sont_controles_puis_ecrits_un_par_patient_et_rejoues_en_mise_a_jour() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_patient_inconnu", importerRendu(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS, "INCONNU;2018-06-15;;;;;"), false));
        r.set("0_patient_vide", importerRendu(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS, ";2018-06-15;;;;;"), false));
        r.set("0_date_future", importerRendu(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS, "P1;2999-01-01;;;;;"), false));
        r.set("0_texte_trop_long", importerRendu(lot, "dossiers-medicaux",
                fichier(ENTETE_DOSSIERS, "P1;2018-06-15;" + "N".repeat(300) + ";;;;"), false));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "dossiers-medicaux",
                fichier(ENTETE_DOSSIERS, "P1;2018-06-15;Diabete;;;;", "P1;2019-01-01;Hypertension;;;;"), false));
        r.set("0_etat_sans_ecriture", medical(CENTRE_A));

        ObjectNode formats = mapper.createObjectNode();
        for (String date : new String[]{"2018-06-15", "15/06/2018", "5/6/2018", "15-06-2018", "2018/06/15", "20180615", "31/02/2018",
                "2018-13-01", "  15/06/2018  ", "abc", "15/06/18"}) {
            formats.set("dateMiseEnDialyse [" + date + "]", importerRendu(lot, "dossiers-medicaux",
                    fichier(ENTETE_DOSSIERS, "P1;" + date + ";;;;;"), true));
        }
        r.set("1_formats_de_date_en_simulation", formats);
        r.set("1_etat_apres_simulations", medical(CENTRE_A));

        // un dossier cree dans la plateforme pour P2 avant l'import : le lot le met a jour (cle = patient seul)
        insererDossier(patient("ZH222"), CENTRE_A, "Saisie plateforme");
        r.set("2_import_nominal", importerRendu(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS,
                "P1;15/06/2018;Nephropathie diabetique;NEGATIF;POSITIF;Observation;Conclusion",
                "P2;2020-01-10;;;;;"), false));
        r.set("2_etat", medical(CENTRE_A));
        r.set("3_rejeu_identique", importerRendu(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS,
                "P1;2018-06-15;Nephropathie diabetique;NEGATIF;POSITIF;Observation;Conclusion"), false));
        r.set("3_etat", medical(CENTRE_A));
        r.set("4_rejeu_avec_nouvelles_valeurs_et_champs_vides", importerRendu(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS,
                "P1;2017-01-01;Glomerulonephrite;;POSITIF;;", "P2;;Lupus;POSITIF;;;"), false));
        r.set("4_etat", medical(CENTRE_A));
        golden("import_dossiers_medicaux_controles_et_rejeu_rp3b", r);
    }

    // ------------------------------------------------------------------ antecedents et serologies

    @Test
    void les_antecedents_sont_controles_puis_ecrits_avec_leur_cle_et_rejoues_en_mise_a_jour() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_patient_inconnu", importerRendu(lot, "antecedents",
                fichier(ENTETE_ANTECEDENTS, "A1;INCONNU;MEDICAL;Diabete;;2010-01-01;;;;"), false));
        r.set("0_libelle_et_date_obligatoires_absents", importerRendu(lot, "antecedents",
                fichier(ENTETE_ANTECEDENTS, "A1;P1;MEDICAL;;;;;;;"), false));
        r.set("0_fin_avant_debut", importerRendu(lot, "antecedents",
                fichier(ENTETE_ANTECEDENTS, "A1;P1;MEDICAL;Diabete;;2010-01-01;2009-01-01;;;"), false));
        r.set("0_debut_dans_le_futur", importerRendu(lot, "antecedents",
                fichier(ENTETE_ANTECEDENTS, "A1;P1;MEDICAL;Diabete;;2999-01-01;;;;"), false));
        r.set("0_type_et_statut_inconnus", importerRendu(lot, "antecedents",
                fichier(ENTETE_ANTECEDENTS, "A1;P1;BIZARRE;Diabete;;2010-01-01;;INCONNU;;"), false));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "antecedents", fichier(ENTETE_ANTECEDENTS,
                "A1;P1;MEDICAL;Diabete;;2010-01-01;;;;", "A2;P1;medical;DIABETE;;01/01/2010;;;;"), false));
        r.set("0_etat_sans_ecriture", medical(CENTRE_A));

        r.set("1_import_nominal", importerRendu(lot, "antecedents", fichier(ENTETE_ANTECEDENTS,
                "A1;P1;MEDICAL;Diabete de type 2;E11;2010-01-01;;ACTIF;Moderee;Sous insuline",
                "A2;P1;CHIRURGICAL;Appendicectomie;;15/03/1998;15/03/1998;RESOLU;;",
                "A3;P1;MEDICAL;Diabete de type 2;;2015-05-05;;EN_COURS;;",
                "A4;P2;FAMILIAL;Hypertension;I10;2000-01-01;;GUERI;;Mere"), false));
        r.set("1_etat", medical(CENTRE_A));
        r.set("2_rejeu_identique", importerRendu(lot, "antecedents", fichier(ENTETE_ANTECEDENTS,
                "A1;P1;MEDICAL;Diabete de type 2;E11;2010-01-01;;ACTIF;Moderee;Sous insuline"), false));
        r.set("3_rejeu_avec_nouvelles_valeurs_et_une_nouvelle_ligne", importerRendu(lot, "antecedents", fichier(ENTETE_ANTECEDENTS,
                "A1;P1;MEDICAL;Diabete de type 2;E10;2010-01-01;2024-01-01;INACTIF;Severe;", "A5;P2;COMORBIDITE;Obesite;;2005-01-01;;;;"),
                false));
        r.set("3_etat", medical(CENTRE_A));

        // un antecedent saisi dans la plateforme avec la meme cle : mis a jour (cle = patient + type + libelle + debut)
        insererAntecedent(patient("ZH222"), CENTRE_A, "Goutte", "2012-02-02");
        r.set("4_antecedent_de_la_plateforme_a_la_meme_cle", importerRendu(lot, "antecedents", fichier(ENTETE_ANTECEDENTS,
                "A6;P2;MEDICAL;Goutte;;2012-02-02;;;;Reprise", "A7;P2;MEDICAL;Goutte;;2012-02-03;;;;"), false));
        r.set("4_etat", medical(CENTRE_A));
        golden("import_antecedents_controles_cle_et_rejeu_rp3b", r);
    }

    @Test
    void les_serologies_sont_controlees_puis_ecrites_avec_leur_cle_et_rejouees_en_mise_a_jour() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_patient_inconnu", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;INCONNU;AG_HBS;NEGATIF;2026-01-15;;;"), false));
        r.set("0_marqueur_resultat_et_date_obligatoires_absents", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;P1;;;;;;"), false));
        r.set("0_marqueur_et_resultat_inconnus", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;P1;XYZ;PEUT-ETRE;2026-01-15;;;"), false));
        r.set("0_date_dans_le_futur", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;P1;AG_HBS;NEGATIF;2999-01-01;;;"), false));
        r.set("0_titre_illisible", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;P1;AG_HBS;NEGATIF;2026-01-15;abc;;"), false));
        r.set("0_anterieure_a_la_periode_reprise", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;P1;AG_HBS;NEGATIF;2022-01-15;;;"), true));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "serologies", fichier(ENTETE_SEROLOGIES,
                "S1;P1;AG_HBS;NEGATIF;2026-01-15;;;", "S2;P1;ag_hbs;POSITIF;15/01/2026;;;"), false));
        r.set("0_etat_sans_ecriture", medical(CENTRE_A));

        r.set("1_import_nominal", importerRendu(lot, "serologies", fichier(ENTETE_SEROLOGIES,
                "S1;P1;AG_HBS;NEGATIF;2026-01-15;0,5;UI/L;Labo central",
                "S2;P1;AC_HBS;POS;16/01/2026;120;mUI/mL;",
                "S3;P1;AG_HBS;NEG;2026-03-15;;;",
                "S4;P2;AC_VHC;DOUTE;2026-02-01;;;"), false));
        r.set("1_etat", medical(CENTRE_A));
        r.set("2_rejeu_identique", importerRendu(lot, "serologies", fichier(ENTETE_SEROLOGIES,
                "S1;P1;AG_HBS;NEGATIF;2026-01-15;0,5;UI/L;Labo central"), false));
        r.set("3_rejeu_avec_nouveau_resultat_et_une_nouvelle_ligne", importerRendu(lot, "serologies", fichier(ENTETE_SEROLOGIES,
                "S1;P1;AG_HBS;POSITIF;2026-01-15;1,5;UI/L;Labo B", "S5;P2;TPHA;NEGATIF;2026-04-01;;;"), false));
        r.set("3_etat", medical(CENTRE_A));

        insererSerologie(patient("ZH222"), CENTRE_A, "CMV_IGG", "2026-05-05");
        r.set("4_serologie_de_la_plateforme_a_la_meme_cle", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S6;P2;CMV_IGG;NEGATIF;2026-05-05;;;Reprise"), false));
        r.set("4_etat", medical(CENTRE_A));
        golden("import_serologies_controles_cle_et_rejeu_rp3b", r);
    }

    // ------------------------------------------------------------------ abords et analyses

    @Test
    void les_abords_vasculaires_sont_controles_puis_ecrits_sans_date_de_modification_et_rejoues() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_patient_inconnu", importerRendu(lot, "abords-vasculaires",
                fichier(ENTETE_ABORDS, "B1;INCONNU;FAV;GAUCHE;;;;;"), false));
        r.set("0_type_obligatoire_absent_et_inconnu", importerRendu(lot, "abords-vasculaires",
                fichier(ENTETE_ABORDS, "B1;P1;;GAUCHE;;;;;", "B2;P1;TUBE;CENTRE;;;;;"), false));
        r.set("0_fin_avant_creation", importerRendu(lot, "abords-vasculaires",
                fichier(ENTETE_ABORDS, "B1;P1;FAV;GAUCHE;;2018-03-10;2017-01-01;;"), false));
        r.set("0_actif_avec_fin_passee_en_simulation", importerRendu(lot, "abords-vasculaires",
                fichier(ENTETE_ABORDS, "B1;P1;FAV;GAUCHE;;2018-03-10;2019-01-01;oui;"), true));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "abords-vasculaires", fichier(ENTETE_ABORDS,
                "B1;P1;FAV;GAUCHE;;2018-03-10;;;", "B2;P1;fav;DROIT;;10/03/2018;;;"), false));
        r.set("0_etat_sans_ecriture", medical(CENTRE_A));

        r.set("1_import_nominal", importerRendu(lot, "abords-vasculaires", fichier(ENTETE_ABORDS,
                "B1;P1;FAV;G;Radio-cephalique;2018-03-10;;oui;",
                "B2;P1;KT_TUNNELISE;DROITE;Jugulaire;10/03/2020;2022-01-01;non;Infection",
                "B3;P1;PTFE;;;;;;",
                "B4;P2;KT_AIGU;D;;2024-01-01;;oui;"), false));
        r.set("1_booleen_illisible", importerRendu(lot, "abords-vasculaires", fichier(ENTETE_ABORDS,
                "B5;P2;KT_AIGU;D;;2024-02-02;;en service;"), false));
        r.set("1_etat", medical(CENTRE_A));
        r.set("2_rejeu_identique_dont_la_cle_sans_date_de_creation", importerRendu(lot, "abords-vasculaires", fichier(ENTETE_ABORDS,
                "B1;P1;FAV;G;Radio-cephalique;2018-03-10;;oui;", "B3;P1;PTFE;;;;;;"), false));
        r.set("3_rejeu_avec_nouvelles_valeurs", importerRendu(lot, "abords-vasculaires", fichier(ENTETE_ABORDS,
                "B1;P1;FAV;DROIT;Humero-basilique;2018-03-10;2025-06-30;non;Thrombose", "B3;P1;PTFE;GAUCHE;Cuisse;;;oui;"), false));
        r.set("3_etat", medical(CENTRE_A));

        insererAbord(patient("ZH222"), CENTRE_A, "FAV", null);
        r.set("4_abord_de_la_plateforme_sans_date_de_creation", importerRendu(lot, "abords-vasculaires",
                fichier(ENTETE_ABORDS, "B5;P2;FAV;GAUCHE;Reprise;;;;"), false));
        r.set("4_etat", medical(CENTRE_A));
        golden("import_abords_vasculaires_controles_et_rejeu_rp3b", r);
    }

    @Test
    void les_analyses_sont_controlees_puis_ecrites_avec_leur_date_et_rejouees_en_mise_a_jour() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_patient_inconnu", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES, "L1;INCONNU;2026-01-05;10,8;;;;;"), false));
        r.set("0_date_obligatoire_absente", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES, "L1;P1;;10,8;;;;;"), false));
        r.set("0_aucun_resultat", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES, "L1;P1;2026-01-05;;;;;;"), false));
        r.set("0_date_dans_le_futur", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES, "L1;P1;2999-01-01;10,8;;;;;"), false));
        r.set("0_valeurs_illisibles", importerRendu(lot, "analyses",
                fichier(ENTETE_ANALYSES, "L1;P1;2026-01-05;abc;;12.5;;;", "L2;P1;2026-01-06;10,8;;1,5;;;"), false));
        r.set("0_anterieure_a_la_periode_reprise", importerRendu(lot, "analyses",
                fichier(ENTETE_ANALYSES, "L1;P1;2022-01-05;10,8;;;;;"), true));
        r.set("0_doublon_dans_le_fichier", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES,
                "L1;P1;2026-01-05;10,8;;;;;", "L2;P1;05/01/2026;11,0;;;;;"), false));
        r.set("0_etat_sans_ecriture", medical(CENTRE_A));

        r.set("1_import_nominal", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES,
                "L1;P1;2026-01-05;10,8;33;210000;350;8,5;4",
                "L2;P1;12/02/2026;11.2;;;;;",
                "L3;P2;2026-01-05;;;180000;;;12,25"), false));
        r.set("1_etat", medical(CENTRE_A));
        r.set("2_rejeu_identique", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES,
                "L1;P1;2026-01-05;10,8;33;210000;350;8,5;4"), false));
        r.set("3_rejeu_avec_nouvelles_valeurs_et_une_nouvelle_ligne", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES,
                "L1;P1;2026-01-05;12,0;;;;;", "L4;P2;2026-03-01;9,9;;;;;"), false));
        r.set("3_etat", medical(CENTRE_A));

        insererAnalyse(patient("ZH222"), CENTRE_A, "2026-06-06");
        r.set("4_analyse_de_la_plateforme_a_la_meme_date", importerRendu(lot, "analyses",
                fichier(ENTETE_ANALYSES, "L5;P2;2026-06-06;13,3;;;;;"), false));
        r.set("4_etat", medical(CENTRE_A));
        golden("import_analyses_controles_et_rejeu_rp3b", r);
    }

    // ------------------------------------------------------------------ autre centre et roles

    @Test
    void les_donnees_medicales_d_un_autre_centre_sont_ignorees_et_l_import_est_reserve_aux_administrateurs_du_centre() throws Exception {
        UUID lot = lotAvecDeuxPatients();
        insererPatientDuCentreB();
        UUID p1 = patient("ZH111");
        ObjectNode r = mapper.createObjectNode();
        // meme patient et memes cles dans le centre B : ne comptent pas comme deja presentes au centre A
        insererAntecedent(p1, CENTRE_B, "Diabete", "2010-01-01");
        insererSerologie(p1, CENTRE_B, "AG_HBS", "2026-01-15");
        insererAbord(p1, CENTRE_B, "FAV", "2018-03-10");
        insererAnalyse(p1, CENTRE_B, "2026-01-05");
        insererAntecedent(PATIENT_B, CENTRE_B, "Goutte", "2012-02-02");
        r.set("0_centre_B_avant", medical(CENTRE_B));
        r.set("1_antecedents", importerRendu(lot, "antecedents",
                fichier(ENTETE_ANTECEDENTS, "A1;P1;MEDICAL;Diabete;;2010-01-01;;;;"), false));
        r.set("1_serologies", importerRendu(lot, "serologies",
                fichier(ENTETE_SEROLOGIES, "S1;P1;AG_HBS;NEGATIF;2026-01-15;;;"), false));
        r.set("1_abords", importerRendu(lot, "abords-vasculaires",
                fichier(ENTETE_ABORDS, "B1;P1;FAV;GAUCHE;;2018-03-10;;;"), false));
        r.set("1_analyses", importerRendu(lot, "analyses", fichier(ENTETE_ANALYSES, "L1;P1;2026-01-05;10,8;;;;;"), false));
        r.set("1_etat_centre_A", medical(CENTRE_A));
        r.set("1_etat_centre_B_intact", medical(CENTRE_B));

        ObjectNode roles = mapper.createObjectNode();
        String[][] fichiers = {
                {"dossiers-medicaux", fichier(ENTETE_DOSSIERS, "P2;2020-01-10;;;;;"), fichier(ENTETE_DOSSIERS, "INCONNU;abc;;;;;")},
                {"antecedents", fichier(ENTETE_ANTECEDENTS, "A9;P2;MEDICAL;Asthme;;2001-01-01;;;;"),
                        fichier(ENTETE_ANTECEDENTS, "A9;INCONNU;BIZARRE;;;abc;;;;")},
                {"serologies", fichier(ENTETE_SEROLOGIES, "S9;P2;TPHA;NEGATIF;2026-02-02;;;"),
                        fichier(ENTETE_SEROLOGIES, "S9;INCONNU;XYZ;?;abc;;;")},
                {"abords-vasculaires", fichier(ENTETE_ABORDS, "B9;P2;FAV;GAUCHE;;2019-01-01;;;"),
                        fichier(ENTETE_ABORDS, "B9;INCONNU;TUBE;;;abc;;;")},
                {"analyses", fichier(ENTETE_ANALYSES, "L9;P2;2026-02-02;10,0;;;;;"), fichier(ENTETE_ANALYSES, "L9;INCONNU;abc;;;;;;")}};
        for (String[] f : fichiers) {
            for (String role : new String[]{"TECHNICIEN", "INFIRMIER", "MEDECIN", "SECRETAIRE", "SUPERADMIN"}) {
                RequestPostProcessor who = qui(CENTRE_A, TECH, role);
                roles.set(f[0] + " " + role + " fichier valide", rendu(importer(lot, f[0], f[1], false, who, null)));
                roles.set(f[0] + " " + role + " fichier invalide (403 avant 400)", rendu(importer(lot, f[0], f[2], false, who, null)));
            }
        }
        r.set("2_roles", roles);
        r.set("2_etat_apres_refus", medical(CENTRE_A));

        ObjectNode centres = mapper.createObjectNode();
        for (String[] f : fichiers) {
            centres.set(f[0] + " admin A vers le centre B (valide)", rendu(importer(lot, f[0], f[1], false, adminA(), CENTRE_B)));
            centres.set(f[0] + " admin B sur le lot de A (valide)", rendu(importer(lot, f[0], f[1], false, adminB(), null)));
            centres.set(f[0] + " admin B sur le lot de A (invalide)", rendu(importer(lot, f[0], f[2], false, adminB(), null)));
        }
        centres.set("lot inconnu", rendu(importer(UUID.fromString("99997800-0000-0000-0000-00000000dead"), "analyses",
                fichier(ENTETE_ANALYSES, "L9;P2;2026-02-02;10,0;;;;;"), false)));
        r.set("3_autres_centres", centres);
        r.set("3_etat_centre_A", medical(CENTRE_A));
        r.set("3_etat_centre_B", medical(CENTRE_B));
        golden("import_medical_autre_centre_et_roles_rp3b", r);
    }

    // ------------------------------------------------------------------ annulation : bons de sortie

    @Test
    void l_annulation_est_refusee_par_un_bon_de_sortie_du_patient_dans_n_importe_quel_centre_et_nomme_les_bons_de_sortie_apres_le_medical()
            throws Exception {
        UUID lot = lotAvecDeuxPatients();
        importerOk(lot, "dossiers-medicaux", fichier(ENTETE_DOSSIERS, "P1;2018-06-15;Diabete;;;;"));
        importerOk(lot, "analyses", fichier(ENTETE_ANALYSES, "L1;P1;2026-01-05;10,8;;;;;"));
        UUID p1 = patient("ZH111");
        insererPatientDuCentreB();
        ObjectNode r = mapper.createObjectNode();
        r.set("0_avant", medical(CENTRE_A));

        // le bon de sortie d'un patient qui n'est pas dans le lot ne bloque pas ; celui du patient repris bloque
        insererBonDeSortie(PATIENT_B, CENTRE_A, "BS-AUTRE-PATIENT");
        insererBonDeSortie(null, CENTRE_A, "BS-SANS-PATIENT");
        insererBonDeSortie(p1, CENTRE_A, "BS-P1");
        r.set("a_bon_de_sortie_du_patient", rendu(annuler(lot, adminA())));
        r.set("a_etat", medical(CENTRE_A));

        // une donnee medicale hors lot est controlee AVANT les bons de sortie : le refus nomme la donnee medicale
        insererAnalyse(p1, CENTRE_A, "2026-04-04");
        r.set("b_analyse_hors_lot_et_bon_de_sortie", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM resultats_analyses WHERE center_id = ? AND date_prelevement = DATE '2026-04-04'", CENTRE_A);

        // un bilan pre-greffe est controle APRES les bons de sortie : le refus nomme les bons de sortie
        insererBilan(p1, CENTRE_A);
        r.set("c_bilan_pre_greffe_et_bon_de_sortie", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM bons_sortie WHERE center_id = ? AND reference = 'BS-P1'", CENTRE_A);
        r.set("d_bilan_pre_greffe_seul", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM bilans_pre_greffe WHERE center_id = ?", CENTRE_A);

        // le bon de sortie du meme identifiant de patient dans un autre centre bloque aussi (aucun centre dans le controle)
        insererBonDeSortie(p1, CENTRE_B, "BS-P1-B");
        r.set("e_bon_de_sortie_d_un_autre_centre", rendu(annuler(lot, adminA())));
        jdbc.update("DELETE FROM bons_sortie WHERE center_id = ? AND reference = 'BS-P1-B'", CENTRE_B);
        viderLesCaches();
        r.set("f_annulation_une_fois_le_patient_libre", rendu(annuler(lot, adminA())));
        r.set("f_apres", medical(CENTRE_A));
        r.set("f_bons_de_sortie_restants", lignes("SELECT reference FROM bons_sortie WHERE center_id = ? ORDER BY reference", CENTRE_A));
        r.set("f_lots", lots(CENTRE_A));
        golden("annulation_bons_de_sortie_et_ordre_des_controles_rp3b", r);
    }
}
