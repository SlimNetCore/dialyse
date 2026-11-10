package com.hemodialyse.backend.characterization.patientcouverture.rf2;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF2, scénarios 5 et 9 : lectures communes ({@code /api/v1/referentials/forfaits}) et jumelle canonique
 * ({@code /api/v1/organisation/referentiels/forfaits}) : forme {@code RefItem} (le prix en texte dans {@code libelle}, le libellé du
 * forfait dans {@code nom}), tri par code, prix nul, zéro, décimales, cloisonnement ; cache {@code ref.forfaits} et son invalidation
 * après écriture (APRÈS commit, centre du seul écrivain).
 */
class LecturesCommunesEtCacheDesForfaitsCharacterizationIntegrationTest extends ForfaitsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lectures-communes";
    }

    private final org.springframework.test.web.servlet.request.RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    private tools.jackson.databind.JsonNode lire(String chemin, org.springframework.test.web.servlet.request.RequestPostProcessor qui) throws Exception {
        return vue(doGet(chemin, qui));
    }

    @Test
    void les_forfaits_du_centre_sont_rendus_en_ref_item_tries_par_code() throws Exception {
        forfait(CENTRE_A, "ZEBRA", "Z-HD", "Zebra forfait", "5600.00");
        forfait(CENTRE_A, "ALPHA", "a-hd", "alpha forfait", null);
        forfait(CENTRE_A, "BETA", "B-HD", "Beta forfait", "0.00");
        forfait(CENTRE_A, "GAMMA", "G-HD", "Gamma forfait", "12.5");
        forfait(CENTRE_A, "DELTA", "D-HD", "Delta forfait", "99999999.99");
        forfait(CENTRE_A, "NEG", "N-HD", "Forfait negatif", "-5.00");
        forfait(CENTRE_B, "AUTRE", "AUTRE-B", "Forfait du centre B", "1.00");
        ObjectNode m = mapper.createObjectNode();
        m.set("lecture commune", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_A, admin));
        m.set("lecture canonique avec centre", lire(CANONIQUE + "/forfaits?centreId=" + CENTRE_A, admin));
        m.set("lecture canonique sans centre", lire(CANONIQUE + "/forfaits", admin));
        m.set("lecture commune du centre B par un admin du centre A", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_B, admin));
        m.set("lecture commune du centre B par le super administrateur", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_B, superadmin()));
        m.set("lecture commune du centre B par son administrateur", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_B, adminB));
        m.set("lecture commune par la secretaire", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_A, as(CENTRE_A, "SECRETAIRE")));
        m.set("lecture commune par un infirmier", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        m.set("lecture commune par un medecin", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_A, as(CENTRE_A, "MEDECIN")));
        m.set("lecture commune sans centerId", lire(COMMUNS + "/forfaits", admin));
        m.set("lecture commune centerId illisible", lire(COMMUNS + "/forfaits?centerId=pas-un-uuid", admin));
        m.set("lecture commune anonyme", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_A, null));
        m.set("lecture commune sans centre dans la session", lire(COMMUNS + "/forfaits?centerId=" + CENTRE_A, as(null, "ADMIN")));
        m.set("lecture canonique anonyme", lire(CANONIQUE + "/forfaits", null));
        goldenNode("forfaits", m);
    }

    @Test
    void la_liste_est_gardee_en_cache_par_centre_une_ecriture_de_l_api_la_renouvelle_une_ecriture_directe_reste_ignoree() throws Exception {
        UUID existant = forfait(CENTRE_A, "EXISTANT", "F-1", "Premier", "100.00");
        UUID libre = forfait(CENTRE_A, "LIBRE", "F-LIBRE", "Libre", null);
        forfait(CENTRE_B, "B1", "F-B1", "Forfait B1", "1.00");
        ObjectNode m = mapper.createObjectNode();
        String lecA = COMMUNS + "/forfaits?centerId=" + CENTRE_A;
        String lecB = COMMUNS + "/forfaits?centerId=" + CENTRE_B;
        m.set("1. premiere lecture A", lire(lecA, admin));
        m.set("1. premiere lecture B", lire(lecB, adminB));
        forfait(CENTRE_A, "DIRECT_A", "F-DA", "Ajoute en base A", "5.00");
        forfait(CENTRE_B, "DIRECT_B", "F-DB", "Ajoute en base B", "5.00");
        m.set("2. apres ecritures directes : A perimee", lire(lecA, admin));
        m.set("2. apres ecritures directes : B perimee", lire(lecB, adminB));
        m.set("3. creation refusee (doublon)", vue(creer("F-1", "Doublon", null, admin)));
        m.set("3. la liste A est toujours perimee", lire(lecA, admin));
        m.set("4. creation reussie", vue(creer("F-API", "Cree par l API", "42", admin)));
        m.set("4. la liste A est renouvelee, la liste B reste perimee", lire(lecA, admin));
        m.set("4. liste B", lire(lecB, adminB));
        forfait(CENTRE_A, "DIRECT_A2", "F-DA2", "Ajoute en base A apres", null);
        m.set("5. liste A perimee a nouveau", lire(lecA, admin));
        m.set("5. modification", vue(modifier(existant, "F-1", "Premier renomme", "101", admin)));
        m.set("5. liste A apres modification", lire(lecA, admin));
        forfait(CENTRE_A, "DIRECT_A3", "F-DA3", "Ajoute en base A encore", null);
        pec(CENTRE_A, "PEC", libre, null);
        m.set("6. suppression refusee", vue(doDelete(ADMIN + "/" + libre, admin)));
        m.set("6. la liste A reste perimee", lire(lecA, admin));
        jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", CENTRE_A);
        m.set("7. suppression reussie", vue(doDelete(ADMIN + "/" + libre, admin)));
        m.set("7. la liste A est renouvelee", lire(lecA, admin));
        m.set("7. liste B", lire(lecB, adminB));
        m.set("8. creation dans le centre B par son administrateur", vue(doPost(ADMIN,
                "{'centerId':'" + CENTRE_B + "','values':{'code':'F-CREE-B','libelle':'Cree en B'}}", adminB)));
        m.set("8. liste B renouvelee", lire(lecB, adminB));
        m.set("8. la liste A n'est pas touchee", lire(lecA, admin));
        goldenNode("cache-des-forfaits", m);
    }

    @Test
    void un_import_renouvelle_la_liste_en_cache_et_une_verification_non() throws Exception {
        forfait(CENTRE_A, "EXISTANT", "F-1", "Premier", "100.00");
        ObjectNode m = mapper.createObjectNode();
        String lecA = COMMUNS + "/forfaits?centerId=" + CENTRE_A;
        m.set("1. premiere lecture", lire(lecA, admin));
        m.set("2. verification de l'import", vue(importer("Code,Libelle,Prix\nF-2,Second,20\n", true, admin)));
        m.set("2. liste inchangee", lire(lecA, admin));
        forfait(CENTRE_A, "DIRECT", "F-DIR", "Ajoute en base", null);
        m.set("3. import refuse", vue(importer("Code,Libelle,Prix\nF-3,Troisieme,abc\n", false, admin)));
        m.set("3. liste toujours perimee", lire(lecA, admin));
        m.set("4. import reussi", vue(importer("Code,Libelle,Prix\nF-2,Second,20\n", false, admin)));
        m.set("4. liste renouvelee", lire(lecA, admin));
        goldenNode("cache-et-import", m);
    }
}
