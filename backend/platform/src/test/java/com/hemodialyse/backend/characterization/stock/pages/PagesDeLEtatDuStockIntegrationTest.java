package com.hemodialyse.backend.characterization.stock.pages;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** E1 : {@code GET /api/v1/stock/dashboard/stock-valorise/page} (RG-STK-090). */
class PagesDeLEtatDuStockIntegrationTest extends PagesDeLEtatDuStockSupport {

    private static final String PAGE = "/api/v1/stock/dashboard/stock-valorise/page";

    @Test
    void page_triee_filtree_avec_enveloppe_et_elements_de_la_liste_existante() throws Exception {
        article(CENTRE_A, "C-03", "Compresse", "10", "2.5");
        article(CENTRE_A, "A-01", "Aiguille", "3", "4");
        article(CENTRE_A, "B-02", "Bande", "0", "1");
        article(CENTRE_B, "Z-99", "Autre centre", "1", "1");
        Reponse r = lire(PAGE + "?size=2", adminA());
        assertEquals(200, r.statut(), r.brut());
        assertEquals(List.of("A-01", "B-02"), textes(r.json().path("items"), "code"));
        assertEquals(3, r.json().path("total").asLong());
        assertEquals(0, r.json().path("page").asInt());
        assertEquals(2, r.json().path("size").asInt());
        assertEquals(List.of("articleId", "code", "libelle", "unite", "quantite", "pmpCourant", "valeur"),
                List.copyOf(r.json().path("items").get(0).propertyNames()));
        Reponse filtree = lire(PAGE + "?sort=valeur,desc&quantite=%3E0&libelle=e", as(CENTRE_A, "INFIRMIER"));
        assertEquals(List.of("C-03", "A-01"), textes(filtree.json().path("items"), "code"));
        assertEquals(2, filtree.json().path("total").asLong());
    }

    @Test
    void plage_et_difference_numeriques_filtrees_par_le_serveur() throws Exception {
        article(CENTRE_A, "C-03", "Compresse", "10", "2.5");
        article(CENTRE_A, "A-01", "Aiguille", "3", "4");
        article(CENTRE_A, "B-02", "Bande", "0", "1");
        article(CENTRE_A, "D-04", "Drap", "60", "1");
        Reponse plage = lire(PAGE + "?sort=code,asc&quantite=3..10", adminA());
        assertEquals(200, plage.statut(), plage.brut());
        assertEquals(List.of("A-01", "C-03"), textes(plage.json().path("items"), "code"));
        assertEquals(2, plage.json().path("total").asLong());
        Reponse difference = lire(PAGE + "?sort=code,asc&valeur=!%3D0", adminA());
        assertEquals(List.of("A-01", "C-03", "D-04"), textes(difference.json().path("items"), "code"));
        Reponse ouverte = lire(PAGE + "?sort=code,asc&pmp=..2,5", adminA());
        assertEquals(List.of("B-02", "C-03", "D-04"), textes(ouverte.json().path("items"), "code"));
        refuse(PAGE + "?quantite=50..10", "FILTRE_INVALIDE");
        refuse(PAGE + "?quantite=10..x", "FILTRE_INVALIDE");
    }

    @Test
    void codes_400_stables_et_plafond_de_taille() throws Exception {
        refuse(PAGE + "?page=-1", "PAGE_INVALIDE");
        refuse(PAGE + "?size=101", "TAILLE_INVALIDE");
        refuse(PAGE + "?size=0", "TAILLE_INVALIDE");
        refuse(PAGE + "?sort=unite", "TRI_INVALIDE");
        refuse(PAGE + "?sort=code,haut", "TRI_INVALIDE");
        refuse(PAGE + "?sort=code;DROP%20TABLE%20articles", "TRI_INVALIDE");
        refuse(PAGE + "?pmp=abc", "FILTRE_INVALIDE");
        refuse(PAGE + "?page=x", "PARAMETRE_INVALIDE");
        refuse(PAGE + "?centerId=pas-un-uuid", "PARAMETRE_INVALIDE");
    }

    @Test
    void roles_et_isolation_de_centre_de_la_liste_existante() throws Exception {
        assertEquals(200, lire(PAGE, as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF")).statut());
        assertEquals(200, lire(PAGE, as(CENTRE_A, "PHARMACIEN")).statut());
        assertEquals(403, lire(PAGE, as(CENTRE_A, "MEDECIN")).statut());
        assertEquals(403, lire(PAGE + "?centerId=" + CENTRE_B, adminA()).statut());
        assertEquals(403, lireAnonyme(PAGE).statut());
    }
}
