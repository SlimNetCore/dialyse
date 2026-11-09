package com.hemodialyse.backend.characterization.pilotagedirection.pd6;

import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PD6 : la COMPOSITION des indicateurs de la direction ({@code GET /api/v1/direction/indicators}, legacy
 * {@code DirectionIndicatorsQueryService}) : ce que DM8 (cliniques), DM9 (fuite par différence) et PD3 (alertes, stock à la date de fin) ne
 * couvrent pas : société sans centre, centre inactif ou sans stock, arrondi de la valeur du stock (par centre ; les totaux additionnent les
 * valeurs NON arrondies puis arrondissent), stock évalué à la date de fin de période (KB-DIR-07). Aucun mock ; données fictives.
 */
class LaCompositionDesIndicateursDeLaDirectionCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    @Override
    protected String slice() {
        return "pd6";
    }

    @Override
    protected String dossier() {
        return "indicateurs";
    }

    private static String resume(JsonNode centre) {
        return centre.path("nom").asString() + " | actif=" + centre.path("actif").asBoolean();
    }

    private JsonNode stockDe(Resp r, String nom) {
        for (JsonNode c : corps(r).path("centres")) {
            if (nom.equals(c.path("nom").asString())) return c.path("stock");
        }
        return mapper.createObjectNode();
    }

    @Test
    void une_societe_sans_centre_recoit_des_totaux_a_zero_sans_centre_ni_alerte() throws Exception {
        Resp r = doGet(DIRECTION + "/indicators?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse complete (centres vides, totaux a zero, aucune alerte, seuil d'anonymat 5)", reponse(r));
        arbrePd("sans_centre", m);
    }

    @Test
    void la_valeur_du_stock_est_arrondie_par_centre_et_les_totaux_additionnent_les_valeurs_non_arrondies() throws Exception {
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);
        article(CENTRE_A, "A_MICRO", "0.30", "0", "0.01");
        article(CENTRE_B, "B_MICRO", "0.30", "0", "0.01");
        article(CENTRE_B, "B_SANS_PMP", "4", "10", null);
        Resp r = direction("/indicators?from=" + fixe(0) + "&to=" + fixe(100));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 centres (nom | actif) : le centre inactif est servi", java.util.List.of(resume(corps(r).path("centres").get(0)),
                resume(corps(r).path("centres").get(1))));
        m.put("02 centre A : 0,30 x 0,01 = 0,003 arrondi a 0,00", stockDe(r, "Centre A"));
        m.put("03 centre B (inactif) : meme valeur, un article sans PMP vaut zero et passe sous le seuil", stockDe(r, "Centre B"));
        m.put("04 totaux : 0,003 + 0,003 = 0,006 arrondi a 0,01 (et non 0,00 + 0,00)", corps(r).path("totaux").path("stock"));
        arbrePd("arrondi_et_inactif", m);
    }

    @Test
    void le_stock_est_evalue_a_la_date_de_fin_de_la_periode_et_un_centre_sans_article_est_a_zero() throws Exception {
        UUID art = article(CENTRE_A, "ART", "5", "1", "3.00");
        lot(CENTRE_A, art, "PERIME_A_J60", fixe(60), "5");
        lot(CENTRE_A, art, "PROCHE_DE_J60", fixe(150), "5");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int fin : new int[]{50, 60, 61, 100}) {
            Resp r = direction("/indicators?from=" + fixe(0) + "&to=" + fixe(fin));
            m.put("fin <D+" + fin + "> : centre A", stockDe(r, "Centre A"));
            m.put("fin <D+" + fin + "> : centre B (aucun article)", stockDe(r, "Centre B"));
            m.put("fin <D+" + fin + "> : alertes", corps(r).path("alertes"));
        }
        arbrePd("date_de_fin", m);
    }
}
