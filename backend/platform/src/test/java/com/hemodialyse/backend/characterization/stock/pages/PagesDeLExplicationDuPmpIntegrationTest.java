package com.hemodialyse.backend.characterization.stock.pages;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E5 : {@code GET /api/v1/stock/dashboard/pmp-explain/{articleId}/page} (RG-STK-093), pagination en mémoire du rejeu. */
class PagesDeLExplicationDuPmpIntegrationTest extends PagesDeLEtatDuStockSupport {

    private static final String BASE = "/api/v1/stock/dashboard/pmp-explain/";

    private void entree(UUID article, int jour, String quantite, String prix) {
        OffsetDateTime le = OffsetDateTime.of(2026, 10, jour, 9, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, created_by, "
                + "created_at, saisi_le) VALUES (?, ?, ?, 'ENTREE', ?, ?, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article,
                new BigDecimal(quantite), new BigDecimal(prix), le, le);
    }

    @Test
    void etapes_de_la_page_identiques_a_l_explication_complete_et_resume_a_cote() throws Exception {
        UUID article = article(CENTRE_A, "A1", "Article 1", "0", "0");
        for (int jour = 1; jour <= 5; jour++) {
            entree(article, jour, "10", String.valueOf(jour));
        }
        Reponse complete = lire(BASE + article, adminA());
        Reponse page = lire(BASE + article + "/page?page=1&size=2", adminA());
        assertEquals(200, page.statut(), page.brut());
        assertEquals(5, page.json().path("total").asLong());
        assertEquals(1, page.json().path("page").asInt());
        assertEquals(2, page.json().path("size").asInt());
        assertEquals(complete.json().path("etapes").get(2), page.json().path("items").get(0));
        assertEquals(complete.json().path("etapes").get(3), page.json().path("items").get(1));
        for (String champ : new String[]{"articleId", "code", "libelle", "methode", "quantiteFinale", "valeurFinale", "pmpFinal"}) {
            assertEquals(complete.json().path(champ), page.json().path(champ), champ);
        }
        Reponse fin = lire(BASE + article + "/page?page=9", adminA());
        assertTrue(fin.json().path("items").isEmpty());
        assertEquals(5, fin.json().path("total").asLong());
    }

    @Test
    void aucun_tri_codes_400_article_inconnu_et_roles() throws Exception {
        UUID article = article(CENTRE_A, "A1", "Article 1", "0", "0");
        refuse(BASE + article + "/page?sort=date", "TRI_INVALIDE");
        refuse(BASE + article + "/page?size=101", "TAILLE_INVALIDE");
        refuse(BASE + "pas-un-uuid/page", "PARAMETRE_INVALIDE");
        UUID inconnu = UUID.randomUUID();
        assertEquals(lire(BASE + inconnu, adminA()).statut(), lire(BASE + inconnu + "/page", adminA()).statut());
        assertEquals(200, lire(BASE + article + "/page", as(CENTRE_A, "INFIRMIER")).statut());
        assertEquals(403, lire(BASE + article + "/page", as(CENTRE_A, "MEDECIN")).statut());
        assertEquals(403, lire(BASE + article + "/page?centerId=" + CENTRE_B, adminA()).statut());
    }
}
