package com.hemodialyse.backend.characterization.dossiermedical.dm9;

import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacydossiermedical.RisquesInfectieuxJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM9 : le lecteur SQL de la Planification sur {@code serologies_patient}, {@code RisquesInfectieuxJdbc} (RG-PLN-012), observé par ses deux
 * points d'entrée de production : le bean publié {@code RisquesInfectieux.de(centre, patient)} (placement d'un patient,
 * {@code PlanificationConfiguration:603}) et la lecture groupée {@code patientsARisque(jdbc, centre)} (chargement des places, séjours, séances
 * réalisées : {@code PlacesDuCentreJdbc:51,65}, {@code SejoursDesPatientsJdbc:41}, {@code PlanificationConfiguration:713}). DM4
 * ({@code dossiermedical/dm4/planification}) fige la règle de positivité par marqueur et par résultat vue au travers d'une écriture HTTP ;
 * ici le centre vide, les égalités de dates départagées par la date de saisie, le résultat douteux ou en cours plus récent qu'un positif,
 * la casse, et le cloisonnement (sérologie posée dans un autre centre que la fiche, fiche absente). Aucune écriture HTTP : la table est
 * posée directement, le bean est appelé tel quel (aucun mock).
 */
class LeRisqueInfectieuxLuParLaPlanificationCharacterizationIntegrationTest extends Dm9CharacterizationSupport {

    @Autowired
    private RisquesInfectieux risques;

    @Override
    protected String dossier() {
        return "planification";
    }

    private String categorie(UUID centre, UUID patient) {
        return risques.de(new CentreId(centre), new PatientId(patient)).name();
    }

    private Object patientsARisque(UUID centre) {
        return aliasTries(RisquesInfectieuxJdbc.patientsARisque(jdbc, centre));
    }

    @Test
    void un_centre_sans_serologie_n_a_personne_a_risque_et_seul_un_positif_d_un_marqueur_a_risque_compte() throws Exception {
        UUID p1 = patientDm9(CENTRE_A, "P1_AG_HBS_POSITIF");
        UUID p2 = patientDm9(CENTRE_A, "P2_TPHA_POSITIF");
        UUID p3 = patientDm9(CENTRE_A, "P3_VIH_DOUTEUX");
        UUID p4 = patientDm9(CENTRE_A, "P4_ARN_VHC_EN_COURS");
        UUID p5 = patientDm9(CENTRE_A, "P5_AC_VHC_NEGATIF");
        UUID p6 = patientDm9(CENTRE_A, "P6_RESULTAT_EN_MINUSCULES");
        UUID p7 = patientDm9(CENTRE_A, "P7_MARQUEUR_EN_MINUSCULES");
        UUID p8 = patientDm9(CENTRE_A, "P8_AG_HBS_NEGATIF_ET_VIH_POSITIF");
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 centre sans aucune serologie : ensemble", patientsARisque(CENTRE_A));
        m.put("02 centre sans aucune serologie : P1", categorie(CENTRE_A, p1));
        serologieEnBase(CENTRE_A, p1, "S1", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p2, "S2", "TPHA", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p3, "S3", "VIH_AC", "DOUTEUX", fixe(10));
        serologieEnBase(CENTRE_A, p4, "S4", "ARN_VHC", "EN_COURS", fixe(10));
        serologieEnBase(CENTRE_A, p5, "S5", "AC_VHC", "NEGATIF", fixe(10));
        serologieEnBase(CENTRE_A, p6, "S6", "AG_HBS", "positif", fixe(10));
        serologieEnBase(CENTRE_A, p7, "S7", "ag_hbs", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p8, "S8a", "AG_HBS", "NEGATIF", fixe(10));
        serologieEnBase(CENTRE_A, p8, "S8b", "VIH_AC", "POSITIF", fixe(10));
        String avant = empreinteLecteurs();
        m.put("03 ensemble des patients a risque du centre A", patientsARisque(CENTRE_A));
        m.put("04 P1 AG_HBS POSITIF", categorie(CENTRE_A, p1));
        m.put("05 P2 TPHA POSITIF (marqueur hors risque)", categorie(CENTRE_A, p2));
        m.put("06 P3 VIH_AC DOUTEUX", categorie(CENTRE_A, p3));
        m.put("07 P4 ARN_VHC EN_COURS", categorie(CENTRE_A, p4));
        m.put("08 P5 AC_VHC NEGATIF", categorie(CENTRE_A, p5));
        m.put("09 P6 resultat 'positif' en minuscules", categorie(CENTRE_A, p6));
        m.put("10 P7 marqueur 'ag_hbs' en minuscules", categorie(CENTRE_A, p7));
        m.put("11 P8 AG_HBS negatif mais VIH_AC positif (regle par marqueur)", categorie(CENTRE_A, p8));
        m.put("12 patient inconnu", categorie(CENTRE_A, inconnu));
        m.put("13 centre B (aucune serologie)", patientsARisque(CENTRE_B));
        m.put("14 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("marqueurs_et_resultats", m);
    }

    @Test
    void seule_la_derniere_serologie_de_chaque_marqueur_compte_departagee_par_la_date_de_saisie_a_date_de_prelevement_egale() throws Exception {
        UUID p1 = patientDm9(CENTRE_A, "P1_POSITIF_PUIS_NEGATIF");
        UUID p2 = patientDm9(CENTRE_A, "P2_NEGATIF_PUIS_POSITIF");
        UUID p3 = patientDm9(CENTRE_A, "P3_MEME_JOUR_NEGATIF_SAISI_APRES");
        UUID p4 = patientDm9(CENTRE_A, "P4_MEME_JOUR_POSITIF_SAISI_APRES");
        UUID p5 = patientDm9(CENTRE_A, "P5_MEME_JOUR_MEME_SAISIE");
        UUID p6 = patientDm9(CENTRE_A, "P6_POSITIF_PUIS_DOUTEUX");
        UUID p7 = patientDm9(CENTRE_A, "P7_POSITIF_PUIS_EN_COURS");
        UUID p8 = patientDm9(CENTRE_A, "P8_POSITIF_AG_HBS_PUIS_NEGATIF_AC_VHC");
        serologieEnBase(CENTRE_A, p1, "S1a", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p1, "S1b", "AG_HBS", "NEGATIF", fixe(20));
        serologieEnBase(CENTRE_A, p2, "S2a", "AG_HBS", "NEGATIF", fixe(10));
        serologieEnBase(CENTRE_A, p2, "S2b", "AG_HBS", "POSITIF", fixe(20));
        serologieEnBase(CENTRE_A, p3, "S3a", "VIH_AC", "POSITIF", fixe(10), 10);
        serologieEnBase(CENTRE_A, p3, "S3b", "VIH_AC", "NEGATIF", fixe(10), 11);
        serologieEnBase(CENTRE_A, p4, "S4a", "VIH_AC", "NEGATIF", fixe(10), 10);
        serologieEnBase(CENTRE_A, p4, "S4b", "VIH_AC", "POSITIF", fixe(10), 11);
        serologieEnBase(CENTRE_A, p5, "S5a", "AC_VHC", "POSITIF", fixe(10), 10);
        serologieEnBase(CENTRE_A, p5, "S5b", "AC_VHC", "NEGATIF", fixe(10), 10);
        serologieEnBase(CENTRE_A, p6, "S6a", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p6, "S6b", "AG_HBS", "DOUTEUX", fixe(20));
        serologieEnBase(CENTRE_A, p7, "S7a", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p7, "S7b", "AG_HBS", "EN_COURS", fixe(20));
        serologieEnBase(CENTRE_A, p8, "S8a", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, p8, "S8b", "AC_VHC", "NEGATIF", fixe(20));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 ensemble des patients a risque du centre A", patientsARisque(CENTRE_A));
        m.put("02 P1 positif <D+10> puis negatif <D+20>", categorie(CENTRE_A, p1));
        m.put("03 P2 negatif <D+10> puis positif <D+20>", categorie(CENTRE_A, p2));
        m.put("04 P3 meme jour : positif saisi a 10 h, negatif saisi a 11 h", categorie(CENTRE_A, p3));
        m.put("05 P4 meme jour : negatif saisi a 10 h, positif saisi a 11 h", categorie(CENTRE_A, p4));
        m.put("06 P5 meme jour, meme heure de saisie : positif et negatif indepartageables", categorie(CENTRE_A, p5));
        m.put("07 P6 positif <D+10> puis DOUTEUX <D+20> (le douteux leve le risque)", categorie(CENTRE_A, p6));
        m.put("08 P7 positif <D+10> puis EN_COURS <D+20> (le controle en attente leve le risque)", categorie(CENTRE_A, p7));
        m.put("09 P8 positif AG_HBS <D+10>, negatif AC_VHC <D+20> (autre marqueur : sans effet)", categorie(CENTRE_A, p8));
        arbre("derniere_serologie", m);
    }

    @Test
    void le_risque_est_lu_par_centre_une_serologie_posee_dans_un_autre_centre_compte_pour_ce_centre_et_une_negative_ailleurs_ne_leve_rien() throws Exception {
        UUID ficheA1 = patientDm9(CENTRE_A, "FICHE_A1_POSITIF_POSE_AU_CENTRE_B");
        UUID ficheA2 = patientDm9(CENTRE_A, "FICHE_A2_NEGATIF_EN_A_POSITIF_PLUS_ANCIEN_EN_B");
        UUID ficheA3 = patientDm9(CENTRE_A, "FICHE_A3_POSITIF_EN_A_NEGATIF_PLUS_RECENT_EN_B");
        UUID ficheB = patientDm9(CENTRE_B, "FICHE_B_POSITIF_AU_CENTRE_B");
        UUID sansFiche = idStable("patient", "sans_fiche");
        aliasNommes.put(sansFiche.toString(), "<SANS_FICHE>");
        serologieEnBase(CENTRE_B, ficheA1, "S1", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, ficheA2, "S2a", "VIH_AC", "NEGATIF", fixe(20));
        serologieEnBase(CENTRE_B, ficheA2, "S2b", "VIH_AC", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, ficheA3, "S3a", "AC_VHC", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_B, ficheA3, "S3b", "AC_VHC", "NEGATIF", fixe(20));
        serologieEnBase(CENTRE_B, ficheB, "S4", "AG_HBS", "POSITIF", fixe(10));
        serologieEnBase(CENTRE_A, sansFiche, "S5", "AG_HBS", "POSITIF", fixe(10));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patients a risque lus au centre A", patientsARisque(CENTRE_A));
        m.put("02 patients a risque lus au centre B", patientsARisque(CENTRE_B));
        m.put("03 FICHE_A1 vue du centre A (son positif est pose au centre B)", categorie(CENTRE_A, ficheA1));
        m.put("04 FICHE_A1 vue du centre B", categorie(CENTRE_B, ficheA1));
        m.put("05 FICHE_A2 vue du centre A (negatif recent en A)", categorie(CENTRE_A, ficheA2));
        m.put("06 FICHE_A2 vue du centre B (positif ancien en B, le negatif de A n'est pas vu)", categorie(CENTRE_B, ficheA2));
        m.put("07 FICHE_A3 vue du centre A (positif en A, le negatif plus recent de B n'est pas vu)", categorie(CENTRE_A, ficheA3));
        m.put("08 FICHE_A3 vue du centre B", categorie(CENTRE_B, ficheA3));
        m.put("09 FICHE_B vue du centre A", categorie(CENTRE_A, ficheB));
        m.put("10 patient sans fiche, serologie positive au centre A", categorie(CENTRE_A, sansFiche));
        arbre("cloisonnement", m);
    }
}
