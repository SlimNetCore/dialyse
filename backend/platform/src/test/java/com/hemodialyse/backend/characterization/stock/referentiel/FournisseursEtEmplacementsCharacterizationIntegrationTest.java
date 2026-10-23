package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation S1 — fournisseurs et emplacements : {@code POST|GET /fournisseurs}, {@code POST|GET /emplacements}
 * (RG-STK-003, 004). Point d'observation : HTTP et état SQL de {@code fournisseurs} et {@code emplacements}.
 */
class FournisseursEtEmplacementsCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    @Override
    protected String theme() {
        return "fournisseurs-emplacements";
    }

    private void goldenFournisseurs(String name, java.util.UUID centre) {
        goldenSql(name, "SELECT code, raison_sociale, contact, telephone, email, actif FROM fournisseurs WHERE center_id = '"
                + centre + "' ORDER BY raison_sociale, code");
    }

    private void goldenEmplacements(String name, java.util.UUID centre) {
        goldenSql(name, "SELECT code, libelle, actif FROM emplacements WHERE center_id = '" + centre + "' ORDER BY libelle, code");
    }

    // ------------------------------------------------------------------ fournisseurs

    @Test
    void la_creation_d_un_fournisseur() throws Exception {
        golden("fournisseur_creation_200", doPost("/fournisseurs", json("{'centerId':'" + CENTRE_A + "','code':'F01',"
                + "'raisonSociale':' Pharma Sud ','contact':'M. Benali','telephone':'021000000','email':'contact@pharmasud.dz'}"), adminA()));
        golden("fournisseur_creation_minimale_200", doPost("/fournisseurs",
                json("{'centerId':'" + CENTRE_A + "','raisonSociale':'Medi Nord'}"), as(CENTRE_A, "PHARMACIEN")));
        goldenFournisseurs("fournisseur_creation_etat_base", CENTRE_A);
    }

    @Test
    void la_creation_d_un_fournisseur_exige_centre_et_raison_sociale() throws Exception {
        golden("refus_fournisseur_sans_raison_sociale", doPost("/fournisseurs", json("{'centerId':'" + CENTRE_A + "','code':'F'}"), adminA()));
        golden("refus_fournisseur_raison_sociale_blanche",
                doPost("/fournisseurs", json("{'centerId':'" + CENTRE_A + "','raisonSociale':'   '}"), adminA()));
        golden("refus_fournisseur_sans_centre", doPost("/fournisseurs", json("{'raisonSociale':'X'}"), adminA()));
        assertEquals(0, count("fournisseurs", CENTRE_A));
    }

    @Test
    void la_creation_d_un_fournisseur_pour_un_autre_centre_que_la_session() throws Exception {
        // KB-STK-04 corrigé : le centre du corps est confronté à celui de la session (403, rien n'est créé)
        golden("refus_fournisseur_creation_centre_B_par_admin_A",
                doPost("/fournisseurs", json("{'centerId':'" + CENTRE_B + "','raisonSociale':'Intrus'}"), adminA()));
        goldenFournisseurs("fournisseur_centre_B_etat_base", CENTRE_B);
    }

    @Test
    void la_liste_des_fournisseurs_actifs_triee_par_raison_sociale_et_la_recherche() throws Exception {
        fournisseur(CENTRE_A, "F_ZETA", "Zeta Medical", true);
        fournisseur(CENTRE_A, "F_ALPHA", "alpha pharma", true);
        fournisseur(CENTRE_A, "F_BETA", "Beta Distribution", false);
        fournisseur(CENTRE_B, "F_AUTRE", "Alpha du centre B", true);
        golden("fournisseurs_liste_200", doGet("/fournisseurs?centerId=" + CENTRE_A, adminA()));
        golden("fournisseurs_recherche_casse_200", doGet("/fournisseurs?centerId=" + CENTRE_A + "&q=ALPHA", adminA()));
        golden("fournisseurs_recherche_espaces_200", doGet("/fournisseurs?centerId=" + CENTRE_A + "&q=%20zeta%20", adminA()));
        golden("fournisseurs_recherche_inactif_non_trouve_200", doGet("/fournisseurs?centerId=" + CENTRE_A + "&q=beta", adminA()));
        golden("fournisseurs_recherche_blanche_200", doGet("/fournisseurs?centerId=" + CENTRE_A + "&q=%20", adminA()));
        golden("fournisseurs_liste_infirmier_200", doGet("/fournisseurs?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("fournisseurs_liste_sans_centre_utilise_la_session_200", doGet("/fournisseurs", adminA()));
        golden("refus_fournisseurs_liste_centre_B_par_admin_A", doGet("/fournisseurs?centerId=" + CENTRE_B, adminA()));
    }

    // ------------------------------------------------------------------ emplacements

    @Test
    void la_creation_d_un_emplacement() throws Exception {
        golden("emplacement_creation_200",
                doPost("/emplacements", json("{'centerId':'" + CENTRE_A + "','code':'E1','libelle':' Armoire froide '}"), adminA()));
        golden("emplacement_creation_sans_code_200",
                doPost("/emplacements", json("{'centerId':'" + CENTRE_A + "','libelle':'Reserve'}"), as(CENTRE_A, "PHARMACIEN")));
        golden("refus_emplacement_sans_libelle", doPost("/emplacements", json("{'centerId':'" + CENTRE_A + "','code':'E2'}"), adminA()));
        golden("refus_emplacement_libelle_blanc",
                doPost("/emplacements", json("{'centerId':'" + CENTRE_A + "','libelle':'  '}"), adminA()));
        golden("refus_emplacement_sans_centre", doPost("/emplacements", json("{'libelle':'X'}"), adminA()));
        goldenEmplacements("emplacement_creation_etat_base", CENTRE_A);
    }

    @Test
    void la_liste_des_emplacements_actifs_triee_par_libelle() throws Exception {
        emplacement(CENTRE_A, "E_RES", "Reserve", true);
        emplacement(CENTRE_A, "E_ARM", "armoire", true);
        emplacement(CENTRE_A, "E_OLD", "Ancienne salle", false);
        emplacement(CENTRE_B, "E_B", "Centre B", true);
        golden("emplacements_liste_200", doGet("/emplacements?centerId=" + CENTRE_A, adminA()));
        golden("emplacements_liste_infirmier_200", doGet("/emplacements?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("emplacements_liste_sans_centre_utilise_la_session_200", doGet("/emplacements", adminA()));
        golden("refus_emplacements_liste_centre_B_par_admin_A", doGet("/emplacements?centerId=" + CENTRE_B, adminA()));
        golden("refus_emplacement_creation_centre_B_par_admin_A",
                doPost("/emplacements", json("{'centerId':'" + CENTRE_B + "','libelle':'Intrus'}"), adminA()));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void les_roles_sur_fournisseurs_et_emplacements() throws Exception {
        golden("refus_fournisseur_creation_infirmier",
                doPost("/fournisseurs", json("{'centerId':'" + CENTRE_A + "','raisonSociale':'X'}"), as(CENTRE_A, "INFIRMIER")));
        golden("refus_emplacement_creation_infirmier",
                doPost("/emplacements", json("{'centerId':'" + CENTRE_A + "','libelle':'X'}"), as(CENTRE_A, "INFIRMIER")));
        golden("refus_fournisseurs_liste_medecin", doGet("/fournisseurs?centerId=" + CENTRE_A, as(CENTRE_A, "MEDECIN")));
        golden("refus_emplacements_liste_secretaire", doGet("/emplacements?centerId=" + CENTRE_A, as(CENTRE_A, "SECRETAIRE")));
        golden("refus_fournisseurs_anonyme", doGetAnonymous("/fournisseurs?centerId=" + CENTRE_A));
        assertEquals(0, count("fournisseurs", CENTRE_A));
        assertEquals(0, count("emplacements", CENTRE_A));
    }
}





