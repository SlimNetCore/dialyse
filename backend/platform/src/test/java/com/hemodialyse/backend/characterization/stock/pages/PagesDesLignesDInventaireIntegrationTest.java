package com.hemodialyse.backend.characterization.stock.pages;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** E4 : {@code GET /api/v1/stock/inventaires/{id}/lignes/page} (RG-STK-092). */
class PagesDesLignesDInventaireIntegrationTest extends PagesDeLEtatDuStockSupport {

    private static final String BASE = "/api/v1/stock/inventaires/";
    private int position;

    private UUID inventaire(UUID centre, String reference) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, 'EN_COURS', 'u', ?)", id, centre, reference, LocalDate.of(2026, 11, 1), OffsetDateTime.now());
        return id;
    }

    private void ligne(UUID inventaire, String code, String lot, String theorique, String comptee) {
        jdbc.update("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, article_code, "
                        + "article_libelle, unite, numero_lot, quantite_theorique, quantite_comptee, pmp, ajoutee) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'piece', ?, ?, ?, 2, FALSE)", UUID.randomUUID(), inventaire, CENTRE_A, position++,
                UUID.randomUUID(), code, "Article " + code, lot, new BigDecimal(theorique),
                comptee == null ? null : new BigDecimal(comptee));
    }

    @Test
    void feuille_dans_l_ordre_filtree_par_etat_et_recherche_elements_de_details() throws Exception {
        UUID inv = inventaire(CENTRE_A, "INV-90001");
        ligne(inv, "C", "LOT-1", "5", "5");
        ligne(inv, "A", null, "3", null);
        ligne(inv, "B", "LOT-2", "4", "1");
        Reponse r = lire(BASE + inv + "/lignes/page?size=2", adminA());
        assertEquals(200, r.statut(), r.brut());
        assertEquals(List.of("C", "A"), textes(r.json().path("items"), "articleCode"));
        assertEquals(3, r.json().path("total").asLong());
        assertEquals(List.of("id", "articleId", "articleCode", "articleLibelle", "unite", "lotId", "numeroLot", "datePeremption",
                        "quantiteTheorique", "quantiteComptee", "ecart", "pmp", "valeurEcart", "motifEcart", "comptePar", "compteLe",
                        "ajoutee"), List.copyOf(r.json().path("items").get(0).propertyNames()));
        Reponse details = lire(BASE + inv, adminA());
        assertEquals(details.json().path("details").get(0), r.json().path("items").get(0));
        assertEquals(List.of("B"), textes(lire(BASE + inv + "/lignes/page?etat=ECARTS", adminA()).json().path("items"), "articleCode"));
        assertEquals(List.of("A"), textes(lire(BASE + inv + "/lignes/page?etat=A_COMPTER", as(CENTRE_A, "INFIRMIER")).json()
                .path("items"), "articleCode"));
        assertEquals(List.of("B"), textes(lire(BASE + inv + "/lignes/page?q=lot-2", adminA()).json().path("items"), "articleCode"));
        assertEquals(List.of("B", "C", "A"), textes(lire(BASE + inv + "/lignes/page?sort=ecart", adminA()).json().path("items"),
                "articleCode"));
    }

    @Test
    void codes_400_inventaire_d_un_autre_centre_roles() throws Exception {
        UUID inv = inventaire(CENTRE_A, "INV-90001");
        refuse(BASE + inv + "/lignes/page?etat=ECARTS,COMPTEES", "FILTRE_INVALIDE");
        refuse(BASE + inv + "/lignes/page?etat=INCONNU", "FILTRE_INVALIDE");
        refuse(BASE + inv + "/lignes/page?sort=motif", "TRI_INVALIDE");
        refuse(BASE + inv + "/lignes/page?size=101", "TAILLE_INVALIDE");
        UUID ailleurs = inventaire(CENTRE_B, "INV-90002");
        Reponse refusee = lire(BASE + ailleurs + "/lignes/page", adminA());
        assertEquals(lire(BASE + ailleurs, adminA()).statut(), refusee.statut());
        assertEquals("INVENTORY_NOT_FOUND", refusee.json().path("code").asString());
        assertEquals(200, lire(BASE + inv + "/lignes/page", as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF")).statut());
        assertEquals(403, lire(BASE + inv + "/lignes/page", as(CENTRE_A, "MEDECIN")).statut());
        assertEquals(403, lire(BASE + inv + "/lignes/page?centerId=" + CENTRE_B, adminA()).statut());
    }
}
