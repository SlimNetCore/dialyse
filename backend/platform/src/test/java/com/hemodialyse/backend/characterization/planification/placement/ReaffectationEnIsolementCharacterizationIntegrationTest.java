package com.hemodialyse.backend.characterization.planification.placement;

import com.hemodialyse.backend.characterization.planification.PlacementCharacterizationSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;

/**
 * Filet de caractérisation P5 : réaffectation en isolement après l'enregistrement d'une sérologie
 * ({@code POST} et {@code PUT /api/v1/patients/{id}/serologies}, {@code PlacementPatientService.reaffecterSiRisque}). L'appel suit
 * l'enregistrement de la sérologie, hors de sa transaction (B10) : un échec de la réaffectation ne serait pas observable sans seam
 * (limite). Les réponses sérologiques portent des dates : elles sont résumées (statut, marqueur, résultat).
 */
class ReaffectationEnIsolementCharacterizationIntegrationTest extends PlacementCharacterizationSupport {

    @Override
    protected String theme() {
        return "p5-placement/isolement";
    }

    private String resume(Resp r) {
        if (r.status() != 200) return statutEtCode(r);
        JsonNode c = corps(r);
        return "200 " + c.path("marqueur").asString() + " " + c.path("resultat").asString();
    }

    private String positive(UUID patient, String marqueur) throws Exception {
        return resume(doPost(PATIENTS + "/" + patient + "/serologies", corpsSerologie(CENTRE_A, marqueur, "POSITIF"), medecin()));
    }

    @Test
    void un_patient_place_devenu_positif_est_deplace_en_isolement_avec_ses_jours_et_son_creneau() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MERCREDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse", positive(p, "AG_HBS"));
        arbre("deplacement_vers_l_isolement", m);
        etatDesPlaces("deplacement_vers_l_isolement_etat");
        notifications("deplacement_vers_l_isolement_notifications");
        diffusionsCapturees("deplacement_vers_l_isolement_diffusions");
    }

    @Test
    void un_patient_deja_en_isolement_ou_non_place_n_est_pas_deplace_et_rien_n_est_notifie() throws Exception {
        decorDePlacement();
        UUID enIsolement = place(CENTRE_A, "PAT_EN_ISOLEMENT", salleIsolement, matin, giso, LUNDI);
        UUID nonPlace = patientPlace(CENTRE_A, "PAT_NON_PLACE", null, null);
        UUID sansJour = patientPlace(CENTRE_A, "PAT_SANS_JOUR", salle1, matin);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deja en isolement", positive(enIsolement, "VIH_AC"));
        m.put("non place", positive(nonPlace, "VIH_AC"));
        m.put("salle sans jour", positive(sansJour, "VIH_AC"));
        arbre("pas_de_deplacement", m);
        etatDesPlaces("pas_de_deplacement_etat");
        notifications("pas_de_deplacement_notifications");
    }

    @Test
    void les_resultats_negatifs_et_les_marqueurs_hors_risque_ne_deplacent_personne() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("AG_HBS negatif", resume(doPost(PATIENTS + "/" + p + "/serologies", corpsSerologie(CENTRE_A, "AG_HBS", "NEGATIF"), medecin())));
        m.put("AC_HBC positif (hors risque)", positive(p, "AC_HBC"));
        arbre("aucun_risque", m);
        etatDesPlaces("aucun_risque_etat");
        notifications("aucun_risque_notifications");
    }

    @Test
    void les_quatre_marqueurs_a_risque_declenchent_le_deplacement() throws Exception {
        decorDePlacement();
        Map<String, Object> m = new LinkedHashMap<>();
        int i = 0;
        for (String marqueur : new String[]{"AG_HBS", "AC_VHC", "ARN_VHC", "VIH_AC"}) {
            jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
            UUID p = place(CENTRE_A, "PAT_" + marqueur, salle1, matin, g1a, MARDI);
            m.put(marqueur, positive(p, marqueur) + " -> salle d'isolement : "
                    + salleIsolement.equals(jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, p)));
        }
        arbre("marqueurs_a_risque", m);
    }

    @Test
    void le_creneau_est_conserve_s_il_est_libre_sinon_le_patient_prend_un_autre_creneau_ou_reste_en_place() throws Exception {
        decorDePlacement();
        serologie(CENTRE_A, place(CENTRE_A, "PAT_ISO_OCCUPE", salleIsolement, matin, giso, LUNDI), "VIH_AC", "POSITIF", "2026-02-01");
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("creneau occupe en isolement", positive(p1, "AG_HBS"));
        UUID p2 = place(CENTRE_A, "PAT_2", salle2, apresMidi, g2a, LUNDI);
        serologie(CENTRE_A, place(CENTRE_A, "PAT_ISO_OCCUPE_AM", salleIsolement, apresMidi, giso, LUNDI), "VIH_AC", "POSITIF", "2026-02-01");
        m.put("isolement plein sur ses jours", positive(p2, "AG_HBS"));
        arbre("creneau_conserve_ou_change", m);
        etatDesPlaces("creneau_conserve_ou_change_etat");
        notifications("creneau_conserve_ou_change_notifications");
    }

    @Test
    void un_patient_sain_installe_en_isolement_prive_le_patient_positif_de_ce_generateur() throws Exception {
        decorDePlacement();
        place(CENTRE_A, "PAT_SAIN_EN_ISOLEMENT", salleIsolement, matin, giso, MARDI);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        arbre("generateur_d_isolement_pris_par_un_sain", Map.of("reponse", positive(p, "AG_HBS")));
        etatDesPlaces("generateur_d_isolement_pris_par_un_sain_etat");
        notifications("generateur_d_isolement_pris_par_un_sain_notifications");
    }

    @Test
    void sans_salle_d_isolement_le_patient_reste_en_place_et_l_administration_est_alertee() throws Exception {
        decorDePlacement();
        jdbc.update("UPDATE salle SET isolement = 'NON' WHERE center_id = ?", CENTRE_A);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        arbre("sans_salle_d_isolement", Map.of("reponse", positive(p, "ARN_VHC")));
        etatDesPlaces("sans_salle_d_isolement_etat");
        notifications("sans_salle_d_isolement_notifications");
        diffusionsCapturees("sans_salle_d_isolement_diffusions");
    }

    @Test
    void un_patient_sans_generateur_ou_sur_un_generateur_hors_service_est_deplace() throws Exception {
        decorDePlacement();
        UUID hs = generateur(CENTRE_A, "G1HS", salle1, "HORS_SERVICE");
        UUID sansGenerateur = patientPlace(CENTRE_A, "PAT_SANS_GENERATEUR", salle1, matin, MERCREDI);
        UUID surHs = place(CENTRE_A, "PAT_SUR_HS", salle2, apresMidi, hs, JEUDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sans generateur", positive(sansGenerateur, "AG_HBS"));
        m.put("generateur hors service", positive(surHs, "AG_HBS"));
        arbre("placement_heritee_incomplet", m);
        etatDesPlaces("placement_heritee_incomplet_etat");
    }

    @Test
    void la_modification_d_une_serologie_vers_positif_deplace_et_vers_negatif_ne_ramene_pas() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        serologie(CENTRE_A, p, "AG_HBS", "NEGATIF", "2026-02-01");
        UUID serologieId = jdbc.queryForObject("SELECT id FROM serologies_patient WHERE patient_id = ?", UUID.class, p);
        aliasNommes.put(serologieId.toString(), "<SEROLOGIE_1>");
        Map<String, Object> m = new LinkedHashMap<>();
        String corpsMaj = "{'centerId':'" + CENTRE_A + "','resultat':'POSITIF','conduiteATenir':'Isoler le patient'}";
        m.put("vers positif", resume(doPut(PATIENTS + "/" + p + "/serologies/" + serologieId, json(corpsMaj), medecin())));
        m.put("place apres positif", jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, p)
                .equals(salleIsolement) ? "salle d'isolement" : "autre");
        String corpsNeg = "{'centerId':'" + CENTRE_A + "','resultat':'NEGATIF'}";
        m.put("vers negatif", resume(doPut(PATIENTS + "/" + p + "/serologies/" + serologieId, json(corpsNeg), medecin())));
        m.put("place apres negatif", jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, p)
                .equals(salleIsolement) ? "salle d'isolement" : "autre");
        arbre("modification_de_serologie", m);
        notifications("modification_de_serologie_notifications");
    }

    @Test
    void la_suppression_d_une_serologie_ne_reaffecte_rien() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        serologie(CENTRE_A, p, "AG_HBS", "POSITIF", "2026-02-01");
        UUID serologieId = jdbc.queryForObject("SELECT id FROM serologies_patient WHERE patient_id = ?", UUID.class, p);
        Resp r = doDelete(PATIENTS + "/" + p + "/serologies/" + serologieId, medecin());
        arbre("suppression_de_serologie", Map.of("statut", r.status()));
        etatDesPlaces("suppression_de_serologie_etat");
        notifications("suppression_de_serologie_notifications");
    }

    @Test
    void deux_patients_positifs_se_partagent_les_places_d_isolement_et_le_second_peut_ne_plus_en_trouver() throws Exception {
        decorDePlacement();
        UUID p1 = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID p2 = place(CENTRE_A, "PAT_2", salle1, matin, g1b, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("premier", positive(p1, "AG_HBS"));
        m.put("second", positive(p2, "AG_HBS"));
        arbre("places_d_isolement_partagees", m);
        etatDesPlaces("places_d_isolement_partagees_etat");
        notifications("places_d_isolement_partagees_notifications");
    }

    @Test
    void la_sero_enregistree_pour_un_patient_inconnu_ou_d_un_autre_centre_et_les_requetes_invalides() throws Exception {
        decorDePlacement();
        UUID deB = patientPlace(CENTRE_B, "PAT_B", salleB, creneauB, LUNDI);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient inconnu", positive(UUID.nameUUIDFromBytes("inconnu".getBytes()), "AG_HBS"));
        m.put("patient d'un autre centre (centerId A)", positive(deB, "AG_HBS"));
        m.put("marqueur inconnu", statutEtCode(doPost(PATIENTS + "/" + p + "/serologies", corpsSerologie(CENTRE_A, "XYZ", "POSITIF"), medecin())));
        m.put("resultat inconnu", statutEtCode(doPost(PATIENTS + "/" + p + "/serologies", corpsSerologie(CENTRE_A, "AG_HBS", "PEUT_ETRE"), medecin())));
        // DM4 (KB-MED-47 corrigé, ADR 0083) : le corps vide de la sérologie répond 400 (avant : 500) ; le reste du comportement de P5 est inchangé.
        // Version précédente archivée dans p5-placement/_ecarts-legacy/isolement/requetes_et_perimetre_avant_dm4.legacy.json.
        m.put("corps vide", statutEtCode(doPost(PATIENTS + "/" + p + "/serologies", "{}", medecin())));
        m.put("centre de la session different", statutEtCode(doPost(PATIENTS + "/" + p + "/serologies",
                corpsSerologie(CENTRE_B, "AG_HBS", "POSITIF"), medecin())));
        arbre("requetes_et_perimetre", m);
        etatDesPlaces("requetes_et_perimetre_etat");
    }

    @Test
    void la_reaffectation_est_auditee_avec_la_serologie_et_la_fiche_elle_meme_n_est_pas_diffusee() throws Exception {
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        monterLAudit();
        positive(p, "AG_HBS");
        audit("audit_de_la_serologie");
        diffusionsCapturees("diffusions_de_la_serologie");
    }
}
