package com.hemodialyse.backend.characterization.patientcouverture.rf1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * RF1, scénarios 6, 7 et 9 : lectures communes ({@code /api/v1/referentials/transporteurs} et {@code /categories-transport}) et leur
 * jumelle canonique ({@code /api/v1/organisation/referentiels/...}) : forme {@code RefItem}, tri, cloisonnement ; caches
 * {@code ref.transporteurs} et {@code ref.categoriesTransport} et leur invalidation après écriture (APRÈS commit, centre du
 * seul écrivain).
 */
class LecturesCommunesEtCachesCharacterizationIntegrationTest extends TransporteursCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lectures-communes";
    }

    private final org.springframework.test.web.servlet.request.RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    private tools.jackson.databind.JsonNode lire(String chemin, org.springframework.test.web.servlet.request.RequestPostProcessor qui) throws Exception {
        return vue(doGet(chemin, qui));
    }

    @Test
    void les_transporteurs_du_centre_sont_rendus_en_ref_item_tries_par_nom() throws Exception {
        transporteur(CENTRE_A, "ZEBRA", "Zebra Transport", "0795006136");
        transporteur(CENTRE_A, "ALPHA", "alpha ambulances", null);
        transporteur(CENTRE_A, "BETA", "Beta Taxi", "021 11 22 33");
        transporteur(CENTRE_B, "AUTRE", "Transport du centre B", null);
        ObjectNode m = mapper.createObjectNode();
        m.set("lecture commune", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_A, admin));
        m.set("lecture canonique avec centre", lire(CANONIQUE + "/transporteurs?centreId=" + CENTRE_A, admin));
        m.set("lecture canonique sans centre", lire(CANONIQUE + "/transporteurs", admin));
        m.set("lecture commune du centre B par un admin du centre A", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_B, admin));
        m.set("lecture commune du centre B par le super administrateur", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_B, superadmin()));
        m.set("lecture commune du centre B par son administrateur", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_B, adminB));
        m.set("lecture commune par la secretaire", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_A, as(CENTRE_A, "SECRETAIRE")));
        m.set("lecture commune par un infirmier", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        m.set("lecture commune sans centerId", lire(COMMUNS + "/transporteurs", admin));
        m.set("lecture commune centerId illisible", lire(COMMUNS + "/transporteurs?centerId=pas-un-uuid", admin));
        m.set("lecture commune anonyme", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_A, null));
        m.set("lecture commune sans centre dans la session", lire(COMMUNS + "/transporteurs?centerId=" + CENTRE_A, as(null, "ADMIN")));
        goldenNode("transporteurs", m);
    }

    @Test
    void les_categories_de_transport_du_centre_sont_rendues_par_libelle() throws Exception {
        categorie(CENTRE_A, "VSL", "VSL");
        categorie(CENTRE_A, "AMBULANCE", "Ambulance");
        categorie(CENTRE_A, "TAXI", "Taxi conventionne");
        categorie(CENTRE_B, "AUTRE", "Categorie du centre B");
        ObjectNode m = mapper.createObjectNode();
        m.set("lecture commune", lire(COMMUNS + "/categories-transport?centerId=" + CENTRE_A, admin));
        m.set("lecture canonique avec centre", lire(CANONIQUE + "/categories-transport?centreId=" + CENTRE_A, admin));
        m.set("lecture canonique sans centre", lire(CANONIQUE + "/categories-transport", admin));
        m.set("lecture commune du centre B par un admin du centre A", lire(COMMUNS + "/categories-transport?centerId=" + CENTRE_B, admin));
        m.set("lecture commune du centre B par le super administrateur", lire(COMMUNS + "/categories-transport?centerId=" + CENTRE_B, superadmin()));
        m.set("lecture commune sans centerId", lire(COMMUNS + "/categories-transport", admin));
        m.set("lecture commune anonyme", lire(COMMUNS + "/categories-transport?centerId=" + CENTRE_A, null));
        goldenNode("categories-de-transport", m);
    }

    @Test
    void la_liste_est_gardee_en_cache_par_centre_une_ecriture_de_l_api_la_renouvelle_une_ecriture_directe_reste_ignoree() throws Exception {
        UUID existant = transporteur(CENTRE_A, "EXISTANT", "Premier", null);
        UUID libre = transporteur(CENTRE_A, "LIBRE", "Libre", null);
        transporteur(CENTRE_B, "B1", "Transport B1", null);
        ObjectNode m = mapper.createObjectNode();
        String lecA = COMMUNS + "/transporteurs?centerId=" + CENTRE_A;
        String lecB = COMMUNS + "/transporteurs?centerId=" + CENTRE_B;
        m.set("1. premiere lecture A", lire(lecA, admin));
        m.set("1. premiere lecture B", lire(lecB, adminB));
        transporteur(CENTRE_A, "DIRECT_A", "Ajoute en base A", null);
        transporteur(CENTRE_B, "DIRECT_B", "Ajoute en base B", null);
        m.set("2. apres ecritures directes : listes perimees", lire(lecA, admin));
        m.set("2. apres ecritures directes : B perimee", lire(lecB, adminB));
        m.set("3. creation refusee (doublon)", vue(creer("Premier", null, admin)));
        m.set("3. la liste A est toujours perimee", lire(lecA, admin));
        m.set("4. creation reussie", vue(creer("Cree par l'API".replace("'", ""), null, admin)));
        m.set("4. la liste A est renouvelee, la liste B reste perimee", lire(lecA, admin));
        m.set("4. liste B", lire(lecB, adminB));
        transporteur(CENTRE_A, "DIRECT_A2", "Ajoute en base A apres", null);
        m.set("5. liste A perimee a nouveau", lire(lecA, admin));
        m.set("5. modification", vue(modifier(existant, "Premier renomme", null, admin)));
        m.set("5. liste A apres modification", lire(lecA, admin));
        transporteur(CENTRE_A, "DIRECT_A3", "Ajoute en base A encore", null);
        patient(CENTRE_A, "P", libre, null);
        m.set("6. suppression refusee", vue(doDelete(ADMIN + "/" + libre, admin)));
        m.set("6. la liste A reste perimee", lire(lecA, admin));
        jdbc.update("UPDATE patients SET transporteur_aller_id = NULL WHERE center_id = ?", CENTRE_A);
        m.set("7. suppression reussie", vue(doDelete(ADMIN + "/" + libre, admin)));
        m.set("7. la liste A est renouvelee", lire(lecA, admin));
        m.set("7. liste B", lire(lecB, adminB));
        m.set("8. creation dans le centre B par le super administrateur", vue(doPost(ADMIN,
                "{'centerId':'" + CENTRE_B + "','values':{'nom':'Cree en B'}}", adminB)));
        m.set("8. liste B renouvelee", lire(lecB, adminB));
        goldenNode("cache-des-transporteurs", m);
    }

    @Test
    void les_categories_de_transport_restent_en_cache_aucune_ecriture_de_l_api_ne_les_renouvelle() throws Exception {
        categorie(CENTRE_A, "VSL", "VSL");
        ObjectNode m = mapper.createObjectNode();
        String lec = COMMUNS + "/categories-transport?centerId=" + CENTRE_A;
        m.set("1. premiere lecture", lire(lec, admin));
        categorie(CENTRE_A, "TAXI", "Taxi");
        m.set("2. apres ecriture directe", lire(lec, admin));
        creer("Ecriture de l'API sur un transporteur".replace("'", ""), null, admin);
        m.set("3. apres une ecriture de l'API sur les transporteurs", lire(lec, admin));
        videLesCaches();
        m.set("4. apres vidage des caches", lire(lec, admin));
        goldenNode("cache-des-categories", m);
    }
}
