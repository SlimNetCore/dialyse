package com.hemodialyse.backend.characterization.dossiermedical.dm7;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM7, lot 6 : lecteurs du bilan. {@code greffe/kdigo} (recalcul à la demande : PRA du bilan, dernière créatinine, sérologies) et
 * {@code greffe/export-pdf} (dossier de synthèse : bilan, décisions, étapes, sérologies, antécédents actifs, donneurs). Aucun de
 * ces deux lecteurs n'écrit.
 */
class LesEvaluationsKdigoEtLExportPdfDuDossierDeGreffeCharacterizationIntegrationTest extends Dm7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecteurs";
    }

    private String kdigo(UUID patient) throws Exception {
        Resp r = doGet(greffe(patient, "kdigo"), medecinA());
        if (r.status() != 200) return refus(r);
        JsonNode c = corps(r);
        List<String> alertes = new ArrayList<>();
        c.path("alertesSerologiques").forEach(a -> alertes.add(a.path("marqueur").asString() + "=" + a.path("resultat").asString()));
        alertes.sort(String::compareTo);
        return "risque " + resume(c.path("risqueImmunologique"), "code", "valeur", "unite", "niveau") + " ; egfr "
                + resume(c.path("fonctionRenale"), "egfrMlMin173m2", "stade") + " ; alertes " + alertes;
    }

    @Test
    void la_forme_complete_de_la_reponse_kdigo_et_l_absence_d_ecriture() throws Exception {
        LocalDate naissance = LocalDate.now().minusYears(50).minusDays(30);
        UUID patient = patientDm7(CENTRE_A, "PAT_A", "M", naissance, "A+");
        bilanEnBase(CENTRE_A, patient, "BIL", "ELIGIBLE", null, null, null, null, null, "15", "85", null, null);
        creatinineEnBase(CENTRE_A, patient, "CREAT", fixe(10), "1.2");
        serologieEnBase(CENTRE_A, patient, "VIH", "VIH_AC", "POSITIF", fixe(5));
        String avant = empreinteGreffe();
        Resp r = doGet(greffe(patient, "kdigo"), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse", reponse(r));
        m.put("02 en-tete Cache-Control", r.cacheControl());
        m.put("03 aucune ecriture", empreinteGreffe().equals(avant));
        UUID sansBilan = patientDm7(CENTRE_A, "PAT_VIDE");
        m.put("04 patient sans bilan ni donnees", kdigo(sansBilan));
        m.put("05 la lecture n'ouvre pas de bilan", nombreDe("bilans_pre_greffe", CENTRE_A));
        m.put("06 patient inconnu", kdigo(idStable("patient", "inconnu")));
        // KB-MED-86 : patient inconnu 404 PATIENT_INTROUVABLE ; calculs inchangés ; legacy archivé dans lecteurs/_ecarts-legacy
        arbre("forme_nouveau_code", m);
    }

    @Test
    void le_risque_immunologique_retient_le_pire_des_deux_pra_avec_les_seuils_vingt_et_quatre_vingts() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] cas = {{null, null}, {"0", "0"}, {"19.99", null}, {"20", null}, {"80", null}, {"80.01", null}, {"10", "90"}, {null, "50"},
                {"100", "5"}, {"20", "19.99"}};
        int i = 0;
        for (String[] pra : cas) {
            nettoyerDm7();
            UUID patient = patientDm7(CENTRE_A, "PAT_A");
            bilanEnBase(CENTRE_A, patient, "BIL", "ELIGIBLE", null, null, null, null, null, pra[0], pra[1], null, null);
            m.put(String.format("%02d PRA I=%s II=%s", ++i, pra[0], pra[1]), kdigo(patient));
        }
        arbre("risque_immunologique", m);
    }

    @Test
    void la_fonction_renale_utilise_la_derniere_creatinine_datee_l_age_et_le_sexe_du_patient() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate naissance = LocalDate.now().minusYears(50).minusDays(30);
        Object[][] cas = {
                {"homme 50 ans, creatinine 1.0", "M", naissance, new String[][]{{"10", "1.0"}}},
                {"femme 50 ans, creatinine 1.0", "F", naissance, new String[][]{{"10", "1.0"}}},
                {"femme (FEMININ) 50 ans, creatinine 1.0", "FEMININ", naissance, new String[][]{{"10", "1.0"}}},
                {"sexe inconnu (X) : formule masculine", "X", naissance, new String[][]{{"10", "1.0"}}},
                {"homme 50 ans, creatinine 12 (dialyse)", "M", naissance, new String[][]{{"10", "12"}}},
                {"homme 50 ans, creatinine 0.5", "M", naissance, new String[][]{{"10", "0.5"}}},
                {"homme 50 ans, creatinine 2.0", "M", naissance, new String[][]{{"10", "2.0"}}},
                {"homme 50 ans, creatinine 0 : non evaluable", "M", naissance, new String[][]{{"10", "0"}}},
                {"homme 50 ans, creatinine negative : non evaluable", "M", naissance, new String[][]{{"10", "-1"}}},
                {"homme 50 ans, aucune creatinine", "M", naissance, new String[][]{}},
                {"homme sans date de naissance", "M", null, new String[][]{{"10", "1.0"}}},
                {"la plus recente par date gagne, pas par saisie", "M", naissance, new String[][]{{"30", "1.0"}, {"20", "9.0"}}},
                {"une analyse recente sans creatinine est ignoree", "M", naissance, new String[][]{{"20", "2.0"}, {"40", null}}},
        };
        int i = 0;
        for (Object[] c : cas) {
            nettoyerDm7();
            UUID patient = patientDm7(CENTRE_A, "PAT_A", (String) c[1], (LocalDate) c[2], "A+");
            int k = 0;
            for (String[] analyse : (String[][]) c[3]) {
                creatinineEnBase(CENTRE_A, patient, "CREAT" + (++k), fixe(Integer.parseInt(analyse[0])), analyse[1]);
            }
            m.put(String.format("%02d %s", ++i, c[0]), kdigo(patient));
        }
        arbre("fonction_renale", m);
    }

    @Test
    void les_alertes_serologiques_ne_retiennent_que_cinq_marqueurs_positifs_et_le_resultat_le_plus_ancien_l_emporte() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] marqueurs = {"VIH_AC", "AG_HBS", "AC_HBS", "AC_HBC", "AC_VHC", "ARN_VHC", "TPHA", "CMV_IGG", "CMV_IGM", "TOXO_IGG", "TOXO_IGM", "EBV_IGG", "EBV_IGM"};
        for (String marqueur : marqueurs) {
            nettoyerDm7();
            UUID p = patientDm7(CENTRE_A, "PAT_A");
            serologieEnBase(CENTRE_A, p, "S", marqueur, "POSITIF", fixe(5));
            m.put("positif " + marqueur, kdigo(p));
        }
        nettoyerDm7();
        UUID p = patientDm7(CENTRE_A, "PAT_A");
        for (String resultat : new String[]{"NEGATIF", "DOUTEUX", "EN_COURS"}) {
            serologieEnBase(CENTRE_A, p, "R_" + resultat, "VIH_AC", resultat, fixe(5));
            m.put("VIH_AC " + resultat + " seul", kdigo(p));
            jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", CENTRE_A);
        }
        serologieEnBase(CENTRE_A, p, "ANCIEN_POSITIF", "VIH_AC", "POSITIF", fixe(5));
        serologieEnBase(CENTRE_A, p, "RECENT_NEGATIF", "VIH_AC", "NEGATIF", fixe(50));
        m.put("VIH_AC positif ancien puis negatif recent : l'alerte demeure (KB)", kdigo(p));
        jdbc.update("DELETE FROM serologies_patient WHERE center_id = ?", CENTRE_A);
        serologieEnBase(CENTRE_A, p, "ANCIEN_NEGATIF", "VIH_AC", "NEGATIF", fixe(5));
        serologieEnBase(CENTRE_A, p, "RECENT_POSITIF", "VIH_AC", "POSITIF", fixe(50));
        m.put("VIH_AC negatif ancien puis positif recent : pas d'alerte (KB)", kdigo(p));
        serologieEnBase(CENTRE_A, p, "HBS", "AG_HBS", "POSITIF", fixe(6));
        serologieEnBase(CENTRE_A, p, "VHC", "AC_VHC", "POSITIF", fixe(7));
        serologieEnBase(CENTRE_A, p, "TPHA", "TPHA", "POSITIF", fixe(8));
        serologieEnBase(CENTRE_B, p, "AUTRE_CENTRE", "ARN_VHC", "POSITIF", fixe(8));
        m.put("plusieurs alertes (triees pour la lecture) ; la serologie ARN_VHC du centre B pour le meme patient est ignoree", kdigo(p));
        arbre("serologies", m);
    }

    @Test
    void l_export_pdf_complet_reprend_bilan_decisions_etapes_serologies_antecedents_actifs_et_donneurs() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A", "M", LocalDate.of(1970, 5, 17), "A+");
        UUID bilan = bilanEnBase(CENTRE_A, patient, "BIL", "ELIGIBLE", fixe(2), null, null, "A+", "A1 B8 DR3", "12.5", "85", "Aucune connue", "Candidat favorable");
        decisionEnBase(bilan, "DEC_1", fixe(10), "AJOURNE", "Complements demandes", fixe(40));
        decisionEnBase(bilan, "DEC_2", fixe(30), "FAVORABLE", null, null);
        etapeEnBase(CENTRE_A, patient, "E1", "VIROLOGIQUE", "Serologie CMV", "FAIT", fixe(12), "Negatif");
        etapeEnBase(CENTRE_A, patient, "E2", "CARDIOLOGIQUE", "ECG", "A_FAIRE", null, null);
        etapeEnBase(CENTRE_A, patient, "E3", "VIROLOGIQUE", "Serologie VIH", "PLANIFIE", null, null);
        etapeEnBase(CENTRE_A, patient, "E4", "AUTRE", "Etape <b>speciale</b> & \"guillemets\"", "NON_APPLICABLE", null, "Resultat <i>libre</i>");
        serologieEnBase(CENTRE_A, patient, "S1", "VIH_AC", "NEGATIF", fixe(11));
        serologieEnBase(CENTRE_A, patient, "S2", "AG_HBS", "POSITIF", fixe(20));
        antecedentEnBase(CENTRE_A, patient, "A1", "Hypertension arterielle", "ACTIF");
        antecedentEnBase(CENTRE_A, patient, "A2", "Appendicectomie", "RESOLU");
        donneurEnBase(CENTRE_A, patient, "D1", "Donneur Un", "PARENT", "COMPATIBLE", "NEGATIF", 0);
        donneurEnBase(CENTRE_A, patient, "D2", "Donneur <Deux> & Cie", "NON_APPARENTE", "CANDIDAT", "NON_FAIT", 10);
        // Autre centre et autre patient : ne doivent pas apparaître.
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        donneurEnBase(CENTRE_A, autre, "D3", "Donneur Etranger", "PARENT", "CANDIDAT", "NON_FAIT", 5);
        String avant = empreinteGreffe();
        MockHttpServletResponse r = binaire(greffe(patient, "export-pdf"), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut", r.getStatus());
        m.put("02 type", r.getContentType());
        m.put("03 en-tete de telechargement", r.getHeader("Content-Disposition"));
        m.put("04 signature du fichier", new String(r.getContentAsByteArray(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1));
        m.put("05 texte", texte(r.getContentAsByteArray()));
        m.put("06 aucune ecriture", empreinteGreffe().equals(avant));
        arbre("pdf_complet", m);
    }

    @Test
    void l_export_pdf_d_un_patient_sans_bilan_ni_donnees_et_d_un_patient_inconnu() throws Exception {
        UUID vide = patientDm7(CENTRE_A, "PAT_VIDE");
        Map<String, Object> m = new LinkedHashMap<>();
        MockHttpServletResponse r = binaire(greffe(vide, "export-pdf"), medecinA());
        m.put("01 patient sans donnees : statut", r.getStatus());
        m.put("02 patient sans donnees : texte", texte(r.getContentAsByteArray()));
        MockHttpServletResponse inconnu = binaire(greffe(idStable("patient", "inconnu"), "export-pdf"), medecinA());
        m.put("03 patient inconnu : statut", inconnu.getStatus());
        // KB-MED-86 / 97 : patient inconnu 404 PATIENT_INTROUVABLE sans PDF ; legacy archivé dans lecteurs/_ecarts-legacy
        m.put("04 patient inconnu : corps", inconnu.getContentAsString());
        m.put("05 aucun bilan ouvert par l'export", nombreDe("bilans_pre_greffe", CENTRE_A));
        arbre("pdf_vide_nouveau_code", m);
    }

    @Test
    void l_export_pdf_a_une_ligne_illisible_echoue() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A");
        etapeEnBase(CENTRE_A, patient, "ILLISIBLE", "CATEGORIE_DISPARUE", "Etape ancienne", "A_FAIRE", null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 etape illisible", refus(doGet(greffe(patient, "export-pdf"), medecinA())));
        UUID autre = patientDm7(CENTRE_A, "PAT_B");
        bilanSimple(CENTRE_A, autre, "BIL", "STATUT_DISPARU");
        m.put("02 bilan illisible", refus(doGet(greffe(autre, "export-pdf"), medecinA())));
        m.put("03 KDIGO du bilan illisible", refus(doGet(greffe(autre, "kdigo"), medecinA())));
        // DM9 (ADR 0101, écart assumé KB-MED-110) : le legacy répondait 400 avec le nom qualifié d'une énumération de bc-medical (archivé dans
        // _ecarts-legacy/lecteurs/pdf_ligne_illisible_avant_dm9.legacy.json) ; le contexte ignore l'étape illisible (200) et refuse le bilan
        // illisible en 422 BILAN_PRE_GREFFE_ILLISIBLE (règles DM7).
        arbre("pdf_ligne_illisible_nouveau_code", m);
    }

    @Test
    void une_serologie_ou_un_antecedent_illisible_est_ignore_par_le_pdf_et_par_kdigo() throws Exception {
        UUID patient = patientDm7(CENTRE_A, "PAT_A", "M", LocalDate.of(1970, 5, 17), "A+");
        bilanEnBase(CENTRE_A, patient, "BIL", "ELIGIBLE", null, null, null, null, null, "15", "85", null, null);
        serologieEnBase(CENTRE_A, patient, "S_LISIBLE", "AG_HBS", "POSITIF", fixe(5));
        serologieEnBase(CENTRE_A, patient, "S_MARQUEUR", "MARQ_DISPARU", "POSITIF", fixe(6));
        serologieEnBase(CENTRE_A, patient, "S_RESULTAT", "VIH_AC", "RES_DISPARU", fixe(7));
        antecedentEnBase(CENTRE_A, patient, "A_LISIBLE", "Hypertension arterielle", "ACTIF");
        antecedentEnBase(CENTRE_A, patient, "A_STATUT", "Antecedent ancien", "STATUT_DISPARU");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 KDIGO", kdigo(patient));
        MockHttpServletResponse r = binaire(greffe(patient, "export-pdf"), medecinA());
        m.put("02 PDF statut", r.getStatus());
        m.put("03 PDF texte", texte(r.getContentAsByteArray()));
        m.put("04 lignes en base", nombreDe("serologies_patient", CENTRE_A) + " serologies, " + nombreDe("antecedents_medicaux", CENTRE_A) + " antecedents");
        // DM9 (ADR 0101, KB-MED-110 étendu, suites de la revue du 2026-11-09) : le legacy (SerologieRepositoryAdapter.toDomain:70-71,
        // AntecedentRepositoryAdapter.toDomain:67-69 à 7a16c21a, valueOf) répondait 400 avec le nom qualifié d'une énumération de bc-medical
        // dès qu'une sérologie ou un antécédent du patient était illisible ; sans golden legacy (cas non caractérisé avant le retrait).
        // Le contexte ignore la ligne illisible (filtre LISIBLES de SerologiesJdbc / AntecedentsJdbc, WARN avec son identifiant) : 200.
        arbre("serologie_et_antecedent_illisibles_nouveau_code", m);
    }

    private List<String> texte(byte[] pdf) throws Exception {
        List<String> lignes = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (String ligne : new PDFTextStripper().getText(doc).split("\\R")) {
                if (!ligne.isBlank()) lignes.add(ligne.strip());
            }
        }
        return lignes;
    }
}
