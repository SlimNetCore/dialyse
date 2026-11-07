package com.hemodialyse.backend.characterization.dossiermedical.dm4;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM4, lot 1 (enregistrement) : {@code POST /api/v1/patients/{id}/serologies}. Données fictives, dates nommées.
 */
class LEnregistrementDUneSerologieCharacterizationIntegrationTest extends SerologieCharacterizationSupport {

    @Override
    protected String dossier() {
        return "enregistrement";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm4(CENTRE_A, "PAT_A");
        patientB = patientDm4(CENTRE_B, "PAT_B");
    }

    @Test
    void une_serologie_enregistree_garde_tous_ses_champs_et_n_est_pas_modifiee() throws Exception {
        deuxCentres();
        Resp r = doPost(serologiesApi(patientA), corpsComplet(CENTRE_A, "AG_HBS", "NEGATIF", "0.125", "UI/mL", fixe(3),
                "Laboratoire fictif", fixe(93), null), medecinA());
        aliasNommes.put(idDe(r), "<SERO>");
        ecriture("reponse", r);
        etatDesSerologies("serologies");
    }

    @Test
    void un_resultat_positif_impose_une_conduite_a_tenir_les_autres_resultats_non() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        int i = 0;
        for (String resultat : new String[]{"POSITIF", "NEGATIF", "DOUTEUX", "EN_COURS"}) {
            for (String conduite : new String[]{null, "", "   ", CONDUITE}) {
                String marqueur = new String[]{"VIH_AC", "AG_HBS", "AC_VHC", "ARN_VHC"}[i++ % 4];
                cas.put(resultat + " / conduite " + (conduite == null ? "absente" : "'" + conduite + "'"),
                        refus(doPost(serologiesApi(patientA), corps(CENTRE_A, marqueur, resultat, fixe(5 + i), conduite), medecinA())));
            }
        }
        arbre("conduite_requise", cas);
        etatDesSerologies("serologies");
    }

    @Test
    void les_treize_marqueurs_sont_acceptes_et_toute_autre_valeur_est_refusee() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        for (String marqueur : new String[]{"VIH_AC", "AG_HBS", "AC_HBS", "AC_HBC", "AC_VHC", "ARN_VHC", "TPHA", "CMV_IGG", "CMV_IGM",
                "TOXO_IGG", "TOXO_IGM", "EBV_IGG", "EBV_IGM", "vih_ac", "HIV", "TOXO", ""}) {
            cas.put("marqueur '" + marqueur + "'", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, marqueur, "NEGATIF", fixe(1), null),
                    medecinA())));
        }
        cas.put("marqueur absent", statutEtCode(doPost(serologiesApi(patientA), corps(CENTRE_A, null, "NEGATIF", fixe(1), null), medecinA())));
        for (String resultat : new String[]{"negatif", "INDETERMINE", "POSITIVE", ""}) {
            cas.put("resultat '" + resultat + "'", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "TPHA", resultat, fixe(2), CONDUITE),
                    medecinA())));
        }
        cas.put("resultat absent", statutEtCode(doPost(serologiesApi(patientA), corps(CENTRE_A, "TPHA", null, fixe(2), null), medecinA())));
        // KB-MED-47 et 48 corrigés : marqueur ou résultat absent = 400 (avant : 500), détail sans nom de classe interne. Legacy archivé dans dm4/_ecarts-legacy.
        arbre("valeurs_nouveau_code", cas);
        etatDesSerologies("serologies");
    }

    @Test
    void la_date_de_prelevement_par_defaut_est_le_jour_de_la_jvm_et_le_futur_est_refuse() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        Resp defaut = doPost(serologiesApi(patientA), corps(CENTRE_A, "VIH_AC", "NEGATIF", null, null), medecinA());
        relatif(0);
        relatif(1);
        relatif(-1);
        cas.put("sans date", refus(defaut) + " -> " + corps(defaut).path("datePrelevement").asString());
        cas.put("aujourd'hui (doublon avec le defaut)", refus(doPost(serologiesApi(patientA),
                corps(CENTRE_A, "VIH_AC", "NEGATIF", relatif(0), null), medecinA())));
        cas.put("aujourd'hui, autre marqueur", refus(doPost(serologiesApi(patientA),
                corps(CENTRE_A, "AG_HBS", "NEGATIF", relatif(0), null), medecinA())));
        cas.put("hier", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", relatif(-1), null), medecinA())));
        cas.put("demain", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", relatif(1), null), medecinA())));
        cas.put("1900", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "TPHA", "NEGATIF", java.time.LocalDate.of(1900, 1, 1), null),
                medecinA())));
        cas.put("2056", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "TPHA", "NEGATIF", java.time.LocalDate.of(2056, 1, 1), null),
                medecinA())));
        cas.put("date illisible", statutEtCode(doPost(serologiesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"datePrelevement\":\"demain\"}", medecinA())));
        cas.put("date impossible (30 fevrier)", statutEtCode(doPost(serologiesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"datePrelevement\":\"2026-02-30\"}", medecinA())));
        alias(java.time.LocalDate.of(1900, 1, 1), "<1900>");
        // KB-MED-47 corrigé : date illisible ou impossible = 400 (avant : 500). Legacy archivé dans dm4/_ecarts-legacy.
        arbre("dates_nouveau_code", cas);
        etatDesSerologies("serologies");
    }

    @Test
    void un_marqueur_ne_peut_etre_saisi_qu_une_fois_par_patient_et_par_date_de_prelevement() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("premiere saisie", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(10), null), medecinA())));
        cas.put("meme marqueur, meme date, meme resultat", refus(doPost(serologiesApi(patientA),
                corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(10), null), medecinA())));
        cas.put("meme marqueur, meme date, autre resultat", refus(doPost(serologiesApi(patientA),
                corps(CENTRE_A, "AG_HBS", "DOUTEUX", fixe(10), null), medecinA())));
        cas.put("meme marqueur, autre date", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(11), null), medecinA())));
        cas.put("autre marqueur, meme date", refus(doPost(serologiesApi(patientA), corps(CENTRE_A, "AC_HBS", "NEGATIF", fixe(10), null), medecinA())));
        UUID autre = patientDm4(CENTRE_A, "PAT_A2");
        cas.put("autre patient du centre, meme marqueur et meme date", refus(doPost(serologiesApi(autre),
                corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(10), null), medecinA())));
        arbre("doublons", cas);
        etatDesSerologies("serologies");
    }

    @Test
    void les_champs_facultatifs_ont_des_bornes_de_colonne_non_controlees_par_l_application() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("titre a trois decimales", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "AC_HBS", "POSITIF", "12.345", "mUI/mL", fixe(1), null, null, CONDUITE), medecinA())));
        cas.put("titre a quatre decimales", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "AC_HBC", "NEGATIF", "12.3456", null, fixe(1), null, null, null), medecinA())));
        cas.put("titre negatif", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "TPHA", "NEGATIF", "-1", null, fixe(1), null, null, null), medecinA())));
        cas.put("titre trop grand (8 chiffres entiers)", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "CMV_IGG", "NEGATIF", "12345678", null, fixe(1), null, null, null), medecinA())));
        cas.put("unite de 21 caracteres", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "CMV_IGM", "NEGATIF", null, "u".repeat(21), fixe(1), null, null, null), medecinA())));
        cas.put("unite de 20 caracteres", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "TOXO_IGG", "NEGATIF", null, "u".repeat(20), fixe(1), null, null, null), medecinA())));
        cas.put("laboratoire de 256 caracteres", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "TOXO_IGM", "NEGATIF", null, null, fixe(1), "l".repeat(256), null, null), medecinA())));
        cas.put("prochain controle avant le prelevement", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "EBV_IGG", "NEGATIF", null, null, fixe(20), null, fixe(2), null), medecinA())));
        cas.put("conduite tres longue (5000 caracteres)", refus(doPost(serologiesApi(patientA),
                corpsComplet(CENTRE_A, "EBV_IGM", "POSITIF", null, null, fixe(1), null, null, "c".repeat(5000)), medecinA())));
        // KB-MED-52 corrigé : unité, laboratoire ou titre hors borne de colonne = 400 stable (avant : 500). Legacy archivé dans dm4/_ecarts-legacy.
        arbre("bornes_nouveau_code", cas);
        sql("serologies", "SELECT s.marqueur, s.titre, LENGTH(s.unite) AS longueur_unite, LENGTH(s.laboratoire) AS longueur_labo, "
                + "LENGTH(s.conduite_a_tenir) AS longueur_conduite, s.date_prochain_controle FROM serologies_patient s "
                + "WHERE s.center_id = '" + CENTRE_A + "' ORDER BY s.marqueur");
    }

    @Test
    void un_corps_absent_vide_ou_illisible_et_un_patient_illisible_sont_refuses_sans_ecriture() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps vide {}", refus(doPost(serologiesApi(patientA), "{}", medecinA())));
        cas.put("corps absent", refus(doPost(serologiesApi(patientA), "", medecinA())));
        cas.put("corps illisible", refus(doPost(serologiesApi(patientA), "{pas du json", medecinA())));
        cas.put("corps tableau", refus(doPost(serologiesApi(patientA), "[]", medecinA())));
        cas.put("titre texte", refus(doPost(serologiesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"titre\":\"abc\"}", medecinA())));
        cas.put("centerId illisible", refus(doPost(serologiesApi(patientA),
                "{\"centerId\":\"pas-un-uuid\",\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\"}", medecinA())));
        cas.put("patient illisible", refus(doPost(PATIENTS_API + "/pas-un-uuid/serologies", corps(CENTRE_A, "TPHA", "NEGATIF", fixe(1), null),
                medecinA())));
        cas.put("champ inconnu ignore", refus(doPost(serologiesApi(patientA),
                "{\"centerId\":\"" + CENTRE_A + "\",\"marqueur\":\"TPHA\",\"resultat\":\"NEGATIF\",\"datePrelevement\":\"" + fixe(1)
                        + "\",\"inconnu\":1}", medecinA())));
        // KB-MED-47 corrigé : corps absent, illisible, tableau, {} ou identifiants illisibles = 400 (avant : 500). Legacy archivé dans dm4/_ecarts-legacy.
        arbre("corps_nouveau_code", cas);
        assertThat(nombreDeSerologies()).isEqualTo(1);
    }

    @Test
    void a_l_enregistrement_un_patient_inconnu_ou_d_un_autre_centre_est_refuse_en_422_par_la_planification_et_rien_n_est_ecrit() throws Exception {
        deuxCentres();
        UUID inconnu = UUID.nameUUIDFromBytes("dm4-inconnu".getBytes());
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("negative, patient inconnu", refus(doPost(serologiesApi(inconnu), corps(CENTRE_A, "TPHA", "NEGATIF", fixe(1), null), medecinA())));
        cas.put("negative, patient du centre B (session A)", refus(doPost(serologiesApi(patientB),
                corps(CENTRE_A, "TPHA", "NEGATIF", fixe(1), null), medecinA())));
        cas.put("douteuse, patient du centre B (session A)", refus(doPost(serologiesApi(patientB),
                corps(CENTRE_A, "AG_HBS", "DOUTEUX", fixe(1), null), medecinA())));
        arbre("patients_non_verifies", cas);
        etatDesSerologies("serologies");
    }
}
