package com.hemodialyse.backend.characterization.patientcouverture.pc2;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PC2 : {@code POST /api/v1/patients/{id}/assures/{numero}/affecter}, {@code GET .../assures/history},
 * {@code PUT .../assures/assignments/{id}} : règle R1 (un seul assuré principal actif), refus, dates, historique, partage d'un assuré
 * entre patients, concurrence. Droits de rôles et garde de centre : déjà verrouillés par PC1 ({@code autorisations}).
 */
class AffectationDeLAssurePrincipalCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc2/";
    }

    @Override
    protected String dossier() {
        return "affectation";
    }

    @Test
    void affecter_un_assure_au_patient_ayant_droit_le_rend_principal_actif_a_compter_d_aujourd_hui() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        Resp r = affecter(patient, "PC2-AS-1", adminA());
        ecriture("reponse_de_l_affectation", r);
        etatDesAffectations("affectation_ecrite");
        lecture("historique_du_patient", historique(patient, adminA()));
    }

    @Test
    void affecter_un_second_assure_ferme_le_principal_precedent_a_la_meme_date_un_seul_principal_actif_reste() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        assure(CENTRE_A, "PC2-AS-2", "Martin", "Paul");
        affecter(patient, "PC2-AS-1", adminA());
        pause();
        ecriture("deuxieme_affectation", affecter(patient, "PC2-AS-2", adminA()));
        etatDesAffectations("apres_deux_affectations");
        pause();
        // le même assuré de nouveau : une troisième ligne, les précédentes restent fermées
        ecriture("retour_au_premier_assure", affecter(patient, "PC2-AS-1", adminA()));
        etatDesAffectations("apres_trois_affectations");
        lecture("historique_la_plus_recente_d_abord", historique(patient, adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("principaux actifs", jdbc.queryForObject("SELECT COUNT(*) FROM assure_patient WHERE patient_id = ? AND is_primary = TRUE "
                + "AND date_fin_affectation IS NULL", Integer.class, patient));
        arbre("nombre_de_principaux_actifs", m);
    }

    @Test
    void la_meme_affectation_deux_fois_dans_la_journee_ferme_la_premiere_et_en_rouvre_une_identique() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        affecter(patient, "PC2-AS-1", adminA());
        pause();
        ecriture("seconde_identique", affecter(patient, "PC2-AS-1", adminA()));
        etatDesAffectations("deux_lignes_dont_une_fermee_le_jour_meme");
    }

    @Test
    void l_affectation_est_refusee_pour_un_patient_assure_lui_meme_un_assure_inconnu_ou_un_patient_inconnu() throws Exception {
        UUID ayantDroit = patientAyantDroit("ENFANT_1");
        UUID lui = patientCouvert(CENTRE_A, "LUI", false, "ASSURE_LUI_MEME");
        UUID sansQualite = patientCouvert(CENTRE_A, "SANS", false, null);
        UUID voisin = patientCouvert(CENTRE_A, "VOISIN", false, "VOISIN");
        UUID chezB = patientCouvert(CENTRE_B, "CHEZ_B", false, "ENFANT");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        assure(CENTRE_B, "PC2-AS-B", "Autre", "Centre");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient assuré lui-même", refus(affecter(lui, "PC2-AS-1", adminA())));
        m.put("qualité d'assuré nulle", refus(affecter(sansQualite, "PC2-AS-1", adminA())));
        m.put("qualité inconnue (VOISIN)", refus(affecter(voisin, "PC2-AS-1", adminA())));
        m.put("assuré inconnu", refus(affecter(ayantDroit, "PC2-INCONNU", adminA())));
        m.put("numéro en minuscules", refus(affecter(ayantDroit, "pc2-as-1", adminA())));
        m.put("numéro entouré d'espaces", refus(affecter(ayantDroit, "%20PC2-AS-1%20", adminA())));
        m.put("numéro d'un assuré d'un autre centre", refus(affecter(ayantDroit, "PC2-AS-B", adminA())));
        m.put("patient inconnu", refus(affecter(UUID.nameUUIDFromBytes("inconnu".getBytes()), "PC2-AS-1", adminA())));
        m.put("patient d'un autre centre", refus(affecter(chezB, "PC2-AS-1", adminA())));
        m.put("centre absent", refus(doPost(PATIENTS + "/" + ayantDroit + "/assures/PC2-AS-1/affecter", "", adminA())));
        m.put("centre illisible", refus(doPost(PATIENTS + "/" + ayantDroit + "/assures/PC2-AS-1/affecter?centerId=zzz", "", adminA())));
        m.put("identifiant de patient illisible", refus(doPost(PATIENTS + "/zzz/assures/PC2-AS-1/affecter?centerId=" + CENTRE_A, "", adminA())));
        arbre("refus", m);
        etatDesAffectations("etat_apres_les_refus");
    }

    @Test
    void la_correction_des_dates_d_une_affectation_exige_une_fin_posterieure_au_debut_et_accepte_les_dates_nulles() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        UUID autre = patientAyantDroit("ENFANT_2");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        UUID id = affectationEnBase(CENTRE_A, "AFF_1", patient, "PC2-AS-1", true, jour(-30), null, 1);
        String base = PATIENTS + "/" + patient + "/assures/assignments/" + id + "?centerId=" + CENTRE_A;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fin après le début", refus(doPut(base, json("{'dateDebutAffectation':'" + jour(-20) + "','dateFinAffectation':'" + jour(-10) + "'}"), adminA())));
        etatDesAffectations("apres_correction_nominale");
        m.put("fin le jour du début", refus(doPut(base, json("{'dateDebutAffectation':'" + jour(-10) + "','dateFinAffectation':'" + jour(-10) + "'}"), adminA())));
        m.put("fin avant le début", refus(doPut(base, json("{'dateDebutAffectation':'" + jour(-10) + "','dateFinAffectation':'" + jour(-11) + "'}"), adminA())));
        m.put("début et fin nuls", refus(doPut(base, json("{'dateDebutAffectation':null,'dateFinAffectation':null}"), adminA())));
        m.put("fin sans début", refus(doPut(base, json("{'dateDebutAffectation':null,'dateFinAffectation':'" + jour(-5) + "'}"), adminA())));
        m.put("début futur sans fin", refus(doPut(base, json("{'dateDebutAffectation':'" + jour(5) + "','dateFinAffectation':null}"), adminA())));
        m.put("corps vide", refus(doPut(base, json("{}"), adminA())));
        m.put("date illisible", refus(doPut(base, json("{'dateDebutAffectation':'pas-une-date','dateFinAffectation':null}"), adminA())));
        m.put("corps absent", refus(doPut(base, "", adminA())));
        m.put("affectation inconnue", refus(doPut(PATIENTS + "/" + patient + "/assures/assignments/" + UUID.nameUUIDFromBytes("x".getBytes())
                + "?centerId=" + CENTRE_A, json("{'dateDebutAffectation':null,'dateFinAffectation':null}"), adminA())));
        m.put("affectation d'un autre patient", refus(doPut(PATIENTS + "/" + autre + "/assures/assignments/" + id + "?centerId=" + CENTRE_A,
                json("{'dateDebutAffectation':null,'dateFinAffectation':null}"), adminA())));
        arbre("refus_et_acceptations", m);
        etatDesAffectations("etat_final");
        lecture("historique_apres_corrections", historique(patient, adminA()));
    }

    @Test
    void fermer_une_affectation_par_sa_date_de_fin_la_laisse_principale_mais_inactive_et_une_nouvelle_affectation_ne_la_touche_pas() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        assure(CENTRE_A, "PC2-AS-2", "Martin", "Paul");
        UUID id = affectationEnBase(CENTRE_A, "AFF_1", patient, "PC2-AS-1", true, jour(-30), null, 1);
        ecriture("fin_posee", doPut(PATIENTS + "/" + patient + "/assures/assignments/" + id + "?centerId=" + CENTRE_A,
                json("{'dateDebutAffectation':'" + jour(-30) + "','dateFinAffectation':'" + jour(-3) + "'}"), adminA()));
        lecture("historique_principale_inactive", historique(patient, adminA()));
        affecter(patient, "PC2-AS-2", adminA());
        etatDesAffectations("apres_nouvelle_affectation");
    }

    @Test
    void un_assure_peut_etre_affecte_a_plusieurs_patients_et_chaque_historique_reste_propre_au_patient() throws Exception {
        UUID frere = patientAyantDroit("FRERE");
        UUID soeur = patientAyantDroit("SOEUR");
        assure(CENTRE_A, "PC2-PARENT", "Parent", "Commun");
        affecter(frere, "PC2-PARENT", adminA());
        affecter(soeur, "PC2-PARENT", adminA());
        etatDesAffectations("deux_patients_un_assure");
        lecture("historique_du_frere", historique(frere, adminA()));
        lecture("historique_de_la_soeur", historique(soeur, adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero d'assurance du patient inchangé (frère)", jdbc.queryForObject("SELECT numero_assurance FROM patients WHERE id = ?", String.class, frere));
        m.put("assure_numero_assurance du patient (frère)", String.valueOf(jdbc.queryForObject("SELECT assure_numero_assurance FROM patients WHERE id = ?", String.class, frere)));
        arbre("le_patient_n_est_pas_modifie_par_l_affectation", m);
    }

    @Test
    void l_historique_d_un_patient_sans_affectation_est_vide_et_un_assure_disparu_est_rendu_sans_identite() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        UUID sans = patientAyantDroit("SANS_AFFECTATION");
        lecture("historique_vide", historique(sans, adminA()));
        affectationEnBase(CENTRE_A, "AFF_FANTOME", patient, "PC2-DISPARU", true, jour(-3), null, 1);
        affectationEnBase(CENTRE_A, "AFF_SECONDAIRE", patient, "PC2-DISPARU", false, jour(-9), jour(-4), 0);
        lecture("historique_d_un_assure_disparu", historique(patient, adminA()));
    }

    @Test
    void l_affectation_ne_lit_que_l_affectation_principale_active_pour_la_fermer_et_laisse_les_secondaires() throws Exception {
        UUID patient = patientAyantDroit("ENFANT_1");
        assure(CENTRE_A, "PC2-AS-1", "Dupont", "Jean");
        assure(CENTRE_A, "PC2-AS-2", "Martin", "Paul");
        affectationEnBase(CENTRE_A, "AFF_SECONDAIRE", patient, "PC2-AS-1", false, jour(-20), null, 0);
        affectationEnBase(CENTRE_A, "AFF_PRINCIPALE_1", patient, "PC2-AS-1", true, jour(-10), null, 1);
        affectationEnBase(CENTRE_A, "AFF_PRINCIPALE_2", patient, "PC2-AS-2", true, jour(-5), null, 2);
        ecriture("affectation_avec_deux_principaux_actifs_herites", affecter(patient, "PC2-AS-2", adminA()));
        etatDesAffectations("deux_principaux_herites_tous_fermes");
    }
}
