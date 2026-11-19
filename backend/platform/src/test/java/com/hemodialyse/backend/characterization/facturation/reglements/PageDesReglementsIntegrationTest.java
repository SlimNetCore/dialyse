package com.hemodialyse.backend.characterization.facturation.reglements;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RG-REG-009 : {@code GET /api/v1/reglements/page} câblé de bout en bout (sécurité, session, read model SQL des situations) —
 * mêmes lignes que {@code GET /reglements} (route inchangée, golden masters de {@link RechercheEtPaiementsCharacterizationIntegrationTest}),
 * plus le tri, les filtres de colonne et les codes 400 stables. Assertions directes : aucun golden master nouveau.
 */
class PageDesReglementsIntegrationTest extends ReglementsCharacterizationSupport {

    @Override
    protected String theme() {
        return "page";
    }

    private void jeuDeFactures() {
        facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-01-31", "1000.00", PAYEUR_1, AGENCE_1);
        var f2 = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-02-01", "500.00", PAYEUR_2, AGENCE_2);
        facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-02-01", "800.00", null, null);
        facture(CENTRE_B, P_CENTRE_B, "FAC-B-0001", "2026-02-01", "900.00", PAYEUR_B, AGENCE_B);
        paiement(f2, CENTRE_A, "500.00", "2026-02-11", "u1", "REG-SEED-002", "2026-02-11 09:00:00");
    }

    private static List<String> numeros(JsonNode corps) {
        List<String> numeros = new ArrayList<>();
        corps.path("items").forEach(i -> numeros.add(i.path("numeroFacture").asString()));
        return numeros;
    }

    @Test
    void la_page_reprend_les_lignes_de_la_recherche_dans_le_meme_ordre_par_defaut() throws Exception {
        jeuDeFactures();

        JsonNode historique = json(doGet("?centerId=" + CENTRE_A + "&year=2026", adminA()));
        Resp page = doGet("/page?centerId=" + CENTRE_A + "&year=2026", secretaireA());

        assertEquals(200, page.status());
        JsonNode corps = json(page);
        assertEquals(historique.path("items"), corps.path("items"));
        assertEquals(3, corps.path("total").asInt());
        assertEquals(20, corps.path("size").asInt());
    }

    @Test
    void le_tri_et_les_filtres_de_colonne_sont_appliques_par_le_serveur() throws Exception {
        jeuDeFactures();

        assertEquals(List.of("FAC-0001", "FAC-0003", "FAC-0002"),
                numeros(json(doGet("/page?centerId=" + CENTRE_A + "&year=2026&sort=montantFacture,desc", adminA()))));
        JsonNode reglees = json(doGet("/page?centerId=" + CENTRE_A + "&year=2026&etat=REGLEE", adminA()));
        assertEquals(List.of("FAC-0002"), numeros(reglees));
        assertEquals(1, reglees.path("total").asInt());
        assertEquals(List.of("FAC-0003", "FAC-0001"),
                numeros(json(doGet("/page?centerId=" + CENTRE_A + "&year=2026&soldeType=RESTE&size=5", adminA()))));
    }

    @Test
    void les_refus_portent_un_code_stable_et_l_isolation_du_centre_est_conservee() throws Exception {
        jeuDeFactures();

        Resp taille = doGet("/page?centerId=" + CENTRE_A + "&year=2026&size=101", adminA());
        assertEquals(400, taille.status());
        assertEquals("TAILLE_INVALIDE", json(taille).path("code").asString());
        assertEquals("TRI_INVALIDE",
                json(doGet("/page?centerId=" + CENTRE_A + "&year=2026&sort=reste", adminA())).path("code").asString());
        assertEquals("FILTRE_INVALIDE",
                json(doGet("/page?centerId=" + CENTRE_A + "&year=2026&etat=RECU", adminA())).path("code").asString());
        assertEquals("PARAMETRE_INVALIDE",
                json(doGet("/page?centerId=" + CENTRE_A, adminA())).path("code").asString());
        assertEquals(403, doGet("/page?centerId=" + CENTRE_B + "&year=2026", adminA()).status());
        assertEquals(403, doGet("/page?centerId=" + CENTRE_A + "&year=2026", as(CENTRE_A, "INFIRMIER")).status());
    }
}
