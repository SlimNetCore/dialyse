package com.hemodialyse.backend.characterization.stock.pages;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2 et E3 : {@code GET /api/v1/stock/sorties-pour-seances/page} et {@code GET /{id}/lignes/page} (RG-STK-091) ; la route littérale
 * {@code /page} prime sur {@code /{id}}.
 */
class PagesDesSortiesPourSeancesIntegrationTest extends PagesDeLEtatDuStockSupport {

    private static final String BASE = "/api/v1/stock/sorties-pour-seances";
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    private UUID piece(UUID centre, String reference, String statut, int heures) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO sorties_pour_seances (id, center_id, reference, jour, statut, enregistree_le, enregistree_par) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'u')", id, centre, reference, LocalDate.of(2026, 11, 2), statut, T0.plusHours(heures));
        return id;
    }

    private void ligne(UUID piece, UUID article, String quantite) {
        jdbc.update("INSERT INTO sorties_pour_seances_lignes (id, sortie_id, article_id, quantite_sortie, valeur_unitaire) "
                + "VALUES (?, ?, ?, ?, 1)", UUID.randomUUID(), piece, article, new BigDecimal(quantite));
    }

    @Test
    void pieces_sans_lignes_avec_leur_nombre_filtrees_par_statut() throws Exception {
        UUID aiguille = article(CENTRE_A, "AIG", "Aiguille", "10", "1");
        UUID p1 = piece(CENTRE_A, "SPS-90001", "RAPPROCHEE", 0);
        ligne(p1, aiguille, "2");
        piece(CENTRE_A, "SPS-90002", "ENREGISTREE", 1);
        piece(CENTRE_B, "SPS-90003", "ENREGISTREE", 2);
        Reponse r = lire(BASE + "/page", adminA());
        assertEquals(200, r.statut(), r.brut());
        assertEquals(List.of("SPS-90002", "SPS-90001"), textes(r.json().path("items"), "reference"));
        assertEquals(1, r.json().path("items").get(1).path("nombreDeLignes").asInt());
        assertTrue(r.json().path("items").get(1).path("lignes").isMissingNode());
        Reponse rapprochees = lire(BASE + "/page?statut=RAPPROCHEE&sort=reference,asc", as(CENTRE_A, "INFIRMIER"));
        assertEquals(List.of("SPS-90001"), textes(rapprochees.json().path("items"), "reference"));
        assertEquals(1, rapprochees.json().path("total").asLong());
    }

    @Test
    void lignes_d_une_piece_avec_article_lisible_et_piece_d_un_autre_centre_refusee() throws Exception {
        UUID aiguille = article(CENTRE_A, "AIG", "Aiguille", "10", "1");
        UUID bande = article(CENTRE_A, "BDE", "Bande", "10", "1");
        UUID p1 = piece(CENTRE_A, "SPS-90001", "ENREGISTREE", 0);
        ligne(p1, bande, "1");
        ligne(p1, aiguille, "2");
        Reponse r = lire(BASE + "/" + p1 + "/lignes/page?size=1", adminA());
        assertEquals(200, r.statut(), r.brut());
        assertEquals(List.of("Aiguille"), textes(r.json().path("items"), "articleLibelle"));
        assertEquals("AIG", r.json().path("items").get(0).path("articleCode").asString());
        assertEquals(2, r.json().path("total").asLong());
        Reponse filtree = lire(BASE + "/" + p1 + "/lignes/page?article=bd", adminA());
        assertEquals(List.of("BDE"), textes(filtree.json().path("items"), "articleCode"));
        UUID ailleurs = piece(CENTRE_B, "SPS-90009", "ENREGISTREE", 0);
        Reponse refusee = lire(BASE + "/" + ailleurs + "/lignes/page", adminA());
        assertEquals(lire(BASE + "/" + ailleurs, adminA()).statut(), refusee.statut());
        assertEquals("PIECE_INTROUVABLE", refusee.json().path("code").asString());
    }

    @Test
    void codes_400_roles_et_isolation() throws Exception {
        refuse(BASE + "/page?statut=VALIDE", "FILTRE_INVALIDE");
        refuse(BASE + "/page?sort=salle", "TRI_INVALIDE");
        refuse(BASE + "/page?size=500", "TAILLE_INVALIDE");
        refuse(BASE + "/pas-un-uuid/lignes/page", "PARAMETRE_INVALIDE");
        refuse(BASE + "/" + UUID.randomUUID() + "/lignes/page?sort=lot", "TRI_INVALIDE");
        assertEquals(200, lire(BASE + "/page", as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF")).statut());
        assertEquals(403, lire(BASE + "/page", as(CENTRE_A, "MEDECIN")).statut());
        assertEquals(403, lire(BASE + "/page?centerId=" + CENTRE_B, adminA()).statut());
        assertEquals(403, lire(BASE + "/" + UUID.randomUUID() + "/lignes/page", as(CENTRE_A, "SECRETAIRE")).statut());
    }
}
