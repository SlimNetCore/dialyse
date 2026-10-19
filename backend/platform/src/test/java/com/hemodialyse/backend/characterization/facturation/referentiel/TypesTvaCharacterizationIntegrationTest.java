package com.hemodialyse.backend.characterization.facturation.referentiel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation F1 — types de TVA : {@code GET|POST /tva-types}, {@code PUT|DELETE /tva-types/{id}} (table
 * {@code tva_types}, propriété de la Comptabilité selon l'ADR 0030). Point d'observation : HTTP et état SQL. Écrit sur le
 * legacy, rejoué sur la façade du contexte {@code comptabilite} ({@code TypesTvaControleur}) ; les écarts décidés
 * (KB-FAC-01 à 05) sont verrouillés par les goldens {@code _nouveau_code}.
 */
class TypesTvaCharacterizationIntegrationTest extends ReferentielFacturationCharacterizationSupport {

    private static String type(UUID centre, String libelle, String taux, String prestation, String exonere,
                               String debut, String fin, String texte, String userId) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("'centerId':").append(centre == null ? "null" : "'" + centre + "'");
        sb.append(",'libelle':").append(libelle == null ? "null" : "'" + libelle + "'");
        sb.append(",'taux':").append(taux);
        sb.append(",'typePrestation':").append(prestation == null ? "null" : "'" + prestation + "'");
        if (exonere != null) sb.append(",'exonere':").append(exonere);
        sb.append(",'dateDebutValidite':").append(debut == null ? "null" : "'" + debut + "'");
        if (fin != null) sb.append(",'dateFinValidite':'").append(fin).append("'");
        if (texte != null) sb.append(",'texteReference':'").append(texte).append("'");
        sb.append(",'userId':").append(userId == null ? "null" : "'" + userId + "'");
        return json(sb.append("}").toString());
    }

    private static String nominal(UUID centre) {
        return type(centre, "TVA hémodialyse", "19.00", "HEMODIALYSE", "false", "2026-01-01", null, null, "admin");
    }

    /**
     * Un taux sur son propre mois : deux types ouverts du même type se chevaucheraient (règle unique, ADR 0030).
     */
    private static String avecTaux(String taux, String debut, String fin) {
        return type(CENTRE_A, "T " + taux, taux, "HEMODIALYSE", "false", debut, fin, null, "admin");
    }

    private static String avecDates(String debut, String fin) {
        return type(CENTRE_A, "T", "19.00", "HEMODIALYSE", "false", debut, fin, null, "admin");
    }

    @Override
    protected String theme() {
        return "types-tva";
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void un_centre_sans_type_lit_une_page_vide() throws Exception {
        golden("lecture_aucun_type_200", doGet("/tva-types?centerId=" + CENTRE_A, adminA()));
    }

    @Test
    void la_liste_est_paginee_et_triee_par_debut_decroissant_puis_libelle_tous_etats_confondus() throws Exception {
        typeTva(CENTRE_A, "HEMODIALYSE", "19.00", false, "2024-01-01", "2025-12-31", true);
        typeTva(CENTRE_A, "HEMODIALYSE", "9.00", false, "2026-01-01", null, true);
        typeTva(CENTRE_A, "MEDICAMENT", "0.00", true, "2025-06-01", null, true);
        typeTva(CENTRE_A, "HEMODIALYSE", "7.00", false, "2023-01-01", "2023-12-31", false);
        typeTva(CENTRE_B, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("lecture_liste_triee_200", doGet("/tva-types?centerId=" + CENTRE_A, adminA()));
        golden("lecture_page_2_taille_2_200", doGet("/tva-types?centerId=" + CENTRE_A + "&page=1&size=2", adminA()));
    }

    @Test
    void la_lecture_d_un_autre_centre_que_celui_de_la_session_est_refusee_nouveau_code() throws Exception {
        // KB-FAC-01 corrigé : 403 (le legacy rendait la liste du centre A à un administrateur du centre B)
        typeTva(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("lecture_centre_A_par_admin_B_403_nouveau_code", doGet("/tva-types?centerId=" + CENTRE_A, adminB()));
    }

    @Test
    void la_lecture_sans_centre() throws Exception {
        golden("lecture_sans_centre", doGet("/tva-types", adminA()));
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_creation_nominale_repond_201_avec_le_type_cree_actif() throws Exception {
        golden("creation_nominale_201", doPost("/tva-types", nominal(CENTRE_A), adminA()));
        goldenTypes("creation_nominale_etat_base", CENTRE_A);
    }

    @Test
    void la_creation_complete_porte_la_fin_le_texte_et_l_exoneration() throws Exception {
        golden("creation_complete_201", doPost("/tva-types", type(CENTRE_A, "Exonération", "0.00", "HEMODIALYSE",
                "true", "2026-01-01", "2026-12-31", "Loi de finances 2026", "admin"), adminA()));
        goldenTypes("creation_complete_etat_base", CENTRE_A);
    }

    @Test
    void les_bornes_du_taux_nouveau_code() throws Exception {
        // KB-FAC-05 : taux de 0 à 100 (reproduit) ; chaque taux sur son mois (le chevauchement est refusé, KB-FAC-04)
        golden("taux_0_201_nouveau_code", doPost("/tva-types", avecTaux("0", "2026-01-01", "2026-01-31"), adminA()));
        golden("taux_100_201_nouveau_code", doPost("/tva-types", avecTaux("100", "2026-02-01", "2026-02-28"), adminA()));
        golden("taux_100_01_400", doPost("/tva-types", avecTaux("100.01", "2026-03-01", "2026-03-31"), adminA()));
        golden("taux_negatif_400", doPost("/tva-types", avecTaux("-1", "2026-04-01", "2026-04-30"), adminA()));
        golden("taux_trois_decimales_201_nouveau_code", doPost("/tva-types", avecTaux("19.999", "2026-05-01", "2026-05-31"),
                adminA()));
        goldenTypes("taux_bornes_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void les_dates_de_validite_nouveau_code() throws Exception {
        // KB-FAC-04 corrigé (ADR 0030) : fin égale au début acceptée, fin antérieure refusée avec le message de la règle unique
        golden("dates_fin_egale_au_debut_201_nouveau_code", doPost("/tva-types", avecDates("2026-01-01", "2026-01-01"), adminA()));
        golden("dates_fin_avant_debut_400_nouveau_code", doPost("/tva-types", avecDates("2026-03-02", "2026-03-01"), adminA()));
        golden("dates_fin_lendemain_201_nouveau_code", doPost("/tva-types", avecDates("2026-02-01", "2026-02-02"), adminA()));
        golden("dates_debut_absent", doPost("/tva-types", avecDates(null, null), adminA()));
        goldenTypes("dates_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void deux_types_actifs_du_meme_centre_et_du_meme_type_ne_peuvent_plus_se_chevaucher_nouveau_code() throws Exception {
        // KB-FAC-04 corrigé (ADR 0030) : le second type est refusé (400), rien n'est écrit
        golden("chevauchement_1_201", doPost("/tva-types", avecDates("2026-01-01", null), adminA()));
        golden("chevauchement_2_400_nouveau_code", doPost("/tva-types", avecDates("2026-06-01", "2026-12-31"), adminA()));
        goldenTypes("chevauchement_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void les_champs_obligatoires_et_le_corps() throws Exception {
        golden("libelle_vide_400", doPost("/tva-types", type(CENTRE_A, "", "19.00", "HEMODIALYSE", "false",
                "2026-01-01", null, null, "admin"), adminA()));
        golden("prestation_absente_400", doPost("/tva-types", type(CENTRE_A, "T", "19.00", null, "false",
                "2026-01-01", null, null, "admin"), adminA()));
        golden("utilisateur_absent_400", doPost("/tva-types", type(CENTRE_A, "T", "19.00", "HEMODIALYSE", "false",
                "2026-01-01", null, null, null), adminA()));
        golden("centre_absent_400", doPost("/tva-types", type(null, "T", "19.00", "HEMODIALYSE", "false",
                "2026-01-01", null, null, "admin"), adminA()));
        golden("exonere_absent", doPost("/tva-types", type(CENTRE_A, "T", "19.00", "HEMODIALYSE", null,
                "2026-01-01", null, null, "admin"), adminA()));
        golden("corps_absent", doPostSansCorps("/tva-types", adminA()));
        goldenTypes("champs_obligatoires_etat_base", CENTRE_A);
    }

    @Test
    void la_creation_pour_un_autre_centre_que_celui_de_la_session_est_refusee_nouveau_code() throws Exception {
        // KB-FAC-01 corrigé : 403, rien n'est écrit pour le centre B
        golden("creation_centre_B_par_admin_A_403_nouveau_code", doPost("/tva-types", nominal(CENTRE_B), adminA()));
        goldenTypes("creation_centre_B_par_admin_A_etat_base_nouveau_code", CENTRE_B);
    }

    // ------------------------------------------------------------------ modification et désactivation

    @Test
    void la_modification_remplace_les_valeurs_sans_reactiver_ni_changer_l_auteur_nouveau_code() throws Exception {
        // KB-FAC-03 corrigé : le type désactivé le reste, created_by reste celui d'origine
        UUID id = typeTva(CENTRE_A, "HEMODIALYSE", "7.00", false, "2023-01-01", "2023-12-31", false);
        golden("modification_type_desactive_200_nouveau_code", doPut("/tva-types/" + id, type(CENTRE_A, "Modifié", "9.00",
                "HEMODIALYSE", "false", "2023-01-01", "2023-12-31", null, "editeur"), adminA()));
        goldenTypes("modification_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void la_modification_d_un_identifiant_inconnu_repond_404_nouveau_code() throws Exception {
        // KB-FAC-02 corrigé : 404, rien n'est inséré (le legacy créait le type)
        golden("modification_inconnu_404_nouveau_code", doPut("/tva-types/" + UUID.randomUUID(), nominal(CENTRE_A), adminA()));
        goldenTypes("modification_inconnu_etat_base_nouveau_code", CENTRE_A);
    }

    @Test
    void la_modification_d_un_type_d_un_autre_centre_repond_404_nouveau_code() throws Exception {
        // KB-FAC-02 corrigé : 404 (le legacy répondait 500 sur la clé primaire), rien de modifié
        UUID id = typeTva(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("modification_type_A_par_admin_B_centre_B_404_nouveau_code", doPut("/tva-types/" + id, type(CENTRE_B,
                "Pris par B", "5.00", "HEMODIALYSE", "false", "2026-01-01", null, null, "intrus"), adminB()));
        goldenTypes("modification_autre_centre_etat_base_A", CENTRE_A);
        goldenTypes("modification_autre_centre_etat_base_B", CENTRE_B);
    }

    @Test
    void la_desactivation_est_logique_et_idempotente() throws Exception {
        UUID id = typeTva(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("desactivation_204", doDelete("/tva-types/" + id + "?centerId=" + CENTRE_A, adminA()));
        golden("desactivation_deuxieme_fois", doDelete("/tva-types/" + id + "?centerId=" + CENTRE_A, adminA()));
        golden("desactivation_inconnu", doDelete("/tva-types/" + UUID.randomUUID() + "?centerId=" + CENTRE_A, adminA()));
        goldenTypes("desactivation_etat_base", CENTRE_A);
    }

    @Test
    void la_desactivation_d_un_type_d_un_autre_centre_nouveau_code() throws Exception {
        // KB-FAC-01 corrigé : demander le centre A depuis une session du centre B est refusé (403) ; le type reste actif
        UUID id = typeTva(CENTRE_A, "HEMODIALYSE", "19.00", false, "2026-01-01", null, true);
        golden("desactivation_type_A_avec_centre_B", doDelete("/tva-types/" + id + "?centerId=" + CENTRE_B, adminB()));
        golden("desactivation_type_A_avec_centre_A_par_admin_B_403_nouveau_code", doDelete("/tva-types/" + id + "?centerId="
                + CENTRE_A, adminB()));
        goldenTypes("desactivation_autre_centre_etat_base_nouveau_code", CENTRE_A);
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void autorisations_par_profil() throws Exception {
        String liste = "/tva-types?centerId=" + CENTRE_A;
        assertEquals(200, doGet(liste, as(CENTRE_A, "MEDECIN")).status());
        assertEquals(200, doGet(liste, as(CENTRE_A, "SECRETAIRE")).status());
        golden("autorisation_infirmier_lecture", doGet(liste, as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_secretaire_creation", doPost("/tva-types", nominal(CENTRE_A), as(CENTRE_A, "SECRETAIRE")));
        golden("autorisation_medecin_creation", doPost("/tva-types", nominal(CENTRE_A), as(CENTRE_A, "MEDECIN")));
        golden("autorisation_anonyme_lecture", doGetAnonymous(liste));
        assertEquals(0, count("tva_types", CENTRE_A));
    }

    // ------------------------------------------------------------------ table partagée avec la Comptabilité

    @Test
    void un_type_cree_ici_est_lu_par_les_regles_de_tva_comptables_et_inversement() throws Exception {
        assertEquals(201, doPost("/tva-types", nominal(CENTRE_A), adminA()).status());
        golden("partage_regles_tva_comptables_apres_creation", doGet("/comptabilite/regles-tva", adminA()));
        assertEquals(200, doPost("/comptabilite/regles-tva", json("{'typePrestation':'MEDICAMENT','tauxApplique':0,"
                + "'exonere':true,'dateDebutValidite':'2026-01-01'}"), adminA()).status());
        golden("partage_liste_apres_ajout_comptable_200", doGet("/tva-types?centerId=" + CENTRE_A, adminA()));
    }
}
