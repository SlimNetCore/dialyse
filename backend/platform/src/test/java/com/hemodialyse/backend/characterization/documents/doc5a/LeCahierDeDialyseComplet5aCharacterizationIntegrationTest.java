package com.hemodialyse.backend.characterization.documents.doc5a;

import com.hemodialyse.backend.characterization.seance.se7e.Se7eCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DOC5a : filet de rendu du cahier de dialyse, TOUT le texte du PDF (pas seulement les lignes des séances), avant la conversion du
 * gabarit {@code cahier_dialyse.jrxml} de son SQL embarqué vers un jeu de données fourni par la Séance. Observé par
 * {@code GET /api/v1/cahier-dialyse/{patientId}/imprimer} avec un patient riche : cinq séances (dont une non imprimable), volets
 * paramédical et médical, consommables valorisés (un article à deux prix, une entrée qui annule une sortie), forfait de la prise en
 * charge, surcharge de forfait avec et sans code, dossier médical, prescriptions en vigueur à la date de chaque séance (EPO et fer),
 * puis le même cahier demandé par un profil sans accès au volet médical. Le texte doit rester identique après la conversion.
 * Date d'édition masquée. DONNÉES FICTIVES.
 */
class LeCahierDeDialyseComplet5aCharacterizationIntegrationTest extends Se7eCharacterizationSupport {

    @Override
    protected String tranche() {
        return "doc5a";
    }

    @Override
    protected String dossier() {
        return "cahier";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_C)) {
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM prescriptions_medicales WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM dossier_medical_patient WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM volet_medical WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM articles WHERE center_id = ?", centre);
        }
        super.nettoyerSe1();
    }

    private UUID article(UUID centre, String code, String libelle, String unite) {
        UUID id = idStable("article", code + "-" + centre);
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, active, gere_par_lot, seuil_alerte, stock_quantity) "
                + "VALUES (?,?,?,?,?,TRUE,FALSE,0,0)", id, centre, code, libelle, unite);
        return id;
    }

    private void mouvement(UUID centre, UUID article, UUID seance, String type, String quantite, String prix) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, seance_id, mouvement_type, quantite, prix_unitaire, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", UUID.randomUUID(), centre, article, seance, type,
                new BigDecimal(quantite), prix == null ? null : new BigDecimal(prix));
    }

    private void voletParamedical(UUID centre, UUID seance, String avant, String apres, String tAvant, String tApres, Integer duree,
                                  Integer debit, String uf, String dialysat, String anticoagulant, String incidents) {
        jdbc.update("INSERT INTO volet_paramedical (id, seance_id, center_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, duree_minutes, "
                        + "debit_sang_ml_min, ultrafiltration_ml, anticoagulant, type_dialysat, incidents) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), seance, centre, avant == null ? null : new BigDecimal(avant), apres == null ? null : new BigDecimal(apres),
                tAvant, tApres, duree, debit, uf == null ? null : new BigDecimal(uf), anticoagulant, dialysat, incidents);
    }

    private void voletMedical(UUID centre, UUID seance, String prescription, String tolerance, String examen, String resultats,
                              String ajustements, String conclusion) {
        jdbc.update("INSERT INTO volet_medical (id, seance_id, center_id, prescription, tolerance_seance, examen_clinique, "
                        + "resultats_biologiques, ajustements_therapeutiques, conclusion_medicale) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), seance, centre, prescription, tolerance, examen, resultats, ajustements, conclusion);
    }

    private void prescriptionDeTraitement(UUID centre, UUID patient, LocalDate jour, String poidsSec, UUID epo, Integer doseUi, String voieEpo,
                                          Integer frequenceEpo, String uniteEpo, UUID fer, Integer doseMg, String voieFer, Integer frequenceFer,
                                          String uniteFer) {
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, poids_sec_cible_kg, epo_article_id, "
                        + "epo_dose_ui, epo_voie, epo_frequence_valeur, epo_frequence_unite, fer_article_id, fer_dose_mg, fer_voie, "
                        + "fer_frequence_valeur, fer_frequence_unite, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), patient, centre, Date.valueOf(jour), poidsSec == null ? null : new BigDecimal(poidsSec), epo, doseUi, voieEpo,
                frequenceEpo, uniteEpo, fer, doseMg, voieFer, frequenceFer, uniteFer, java.sql.Timestamp.from(CREATION_FIXE.toInstant()));
    }

    private Map<String, Object> cahier(UUID patient, org.springframework.test.web.servlet.request.RequestPostProcessor qui) throws Exception {
        MockHttpServletResponse r = binaire("/api/v1/cahier-dialyse/" + patient + "/imprimer?tz=UTC", qui);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        if (r.getStatus() == 200) m.put("texte", texteNormalise(r.getContentAsByteArray()));
        return m;
    }

    @Test
    void le_cahier_complet_imprime_tout_le_texte_de_chaque_page_avec_et_sans_volet_medical() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PRICHE");
        UUID forfaitSurcharge = forfait(CENTRE_A, "FORFAIT_5A", "F5A", "175.50");
        UUID s1 = seanceEnBase(CENTRE_A, patient, "R1_VALIDEE_COMPLETE", fixe(31), "VALIDEE");
        UUID s2 = seanceEnBase(CENTRE_A, patient, "R2_SIGNEE_SURCHARGE_AVEC_CODE", fixe(32), "SIGNEE");
        UUID s3 = seanceEnBase(CENTRE_A, patient, "R3_FACTUREE_SANS_SAISIE", fixe(33), "FACTUREE");
        UUID s4 = seanceEnBase(CENTRE_A, patient, "R4_VALIDEE_SURCHARGE_SANS_CODE", fixe(34), "VALIDEE");
        seanceEnBase(CENTRE_A, patient, "R5_CREE", fixe(35), "CREE");
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = 'SUR5A', forfait_override_nom = 'Surcharge 5a', "
                + "forfait_override_prix = ? WHERE id = ?", forfaitSurcharge, new BigDecimal("200.25"), s2);
        jdbc.update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = NULL, forfait_override_nom = NULL, "
                + "forfait_override_prix = NULL WHERE id = ?", forfaitSurcharge, s4);

        voletParamedical(CENTRE_A, s1, "72.50", "70.00", "13/8", "12/7", 240, 300, "2500.00", "Bicarbonate", "Héparine", "Crampes");
        voletParamedical(CENTRE_A, s2, "80.125", "78", "  ", null, null, null, null, " ", null, "  ");
        voletMedical(CENTRE_A, s1, "Hémodialyse 4h", "Bonne", "RAS", "Hb 10,5", "Augmenter l'EPO", "Satisfaisante");
        voletMedical(CENTRE_A, s4, "  ", null, "Examen", null, null, null);

        UUID filtre = article(CENTRE_A, "FIL5A", "Dialyseur 5a", "boîte");
        UUID epo = article(CENTRE_A, "EPO5A", "Époétine 5a", "flacon");
        UUID fer = article(CENTRE_A, "FER5A", "Fer 5a", "ampoule");
        article(CENTRE_B, "FIL5A", "Dialyseur 5a (centre B)", "boîte");
        mouvement(CENTRE_A, filtre, s1, "SORTIE", "1", "1500.00");
        mouvement(CENTRE_A, epo, s1, "SORTIE", "2", "250.50");
        mouvement(CENTRE_A, epo, s1, "SORTIE", "1", "260.00");
        mouvement(CENTRE_A, epo, s1, "ENTREE", "1", "250.50");
        mouvement(CENTRE_A, filtre, s2, "SORTIE", "3", "10.00");
        mouvement(CENTRE_A, filtre, s2, "AJUSTEMENT", "1", "10.00");
        mouvement(CENTRE_A, fer, s4, "SORTIE", "2.5", null);
        mouvement(CENTRE_A, fer, s4, "TRANSFERT", "9", "1.00");

        jdbc.update("INSERT INTO dossier_medical_patient (id, patient_id, center_id, nephropathie_initiale, date_mise_en_dialyse, "
                        + "hepatite_b_statut, hepatite_c_statut, observation_globale, conclusion_medicale) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), patient, CENTRE_A, "Néphropathie 5a", Date.valueOf(fixe(-200)), "PORTEUR", "STATUT_LIBRE",
                "Observation 5a", "  ");
        prescriptionDeTraitement(CENTRE_A, patient, fixe(30), "68.00", epo, 4000, "SC", 1, "SEMAINE", fer, 100, "IV", 2, "MOIS");
        prescriptionDeTraitement(CENTRE_A, patient, fixe(33), "69.50", epo, 0, "SC", 3, "JOUR", null, null, null, null, null);
        prescriptionDeTraitement(CENTRE_A, patient, fixe(34), null, null, null, null, null, null, fer, 50, "SC", 1, "ANNEE");
        prescriptionDeTraitement(CENTRE_A, patient, fixe(80), "71.00", epo, 9000, "SC", 1, "HEURE", null, null, null, null, null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medecin (volet medical visible)", cahier(patient, medecinA()));
        m.put("infirmier (volet medical visible)", cahier(patient, infirmierA()));
        m.put("secretaire (volet medical masque)", cahier(patient, secretaireA()));
        arbre("cahier_complet", m);
    }

    @Test
    void le_cahier_d_un_patient_sans_dossier_ni_prescription_ni_consommable_imprime_des_tirets() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PNU");
        seanceEnBase(CENTRE_A, patient, "N1_VALIDEE", fixe(31), "VALIDEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medecin", cahier(patient, medecinA()));
        arbre("cahier_nu", m);
    }
}
