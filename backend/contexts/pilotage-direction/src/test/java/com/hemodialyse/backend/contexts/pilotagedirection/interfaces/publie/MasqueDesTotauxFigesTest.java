package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Suppression complémentaire appliquée à la lecture d'un instantané figé AVANT la correction de KB-DIR-01 / KB-MED-104 (2026-11-09). */
class MasqueDesTotauxFigesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String texte) {
        return mapper.readTree(texte);
    }

    @Test
    void un_ancien_total_de_patients_est_supprime_quand_un_centre_est_masque() {
        JsonNode overview = json("""
                {"centres":[{"patients":50,"patientsSousKt":9},{"patients":null,"patientsSousKt":null}],
                 "totaux":{"patients":52,"patientsSousKt":12,"seances":300}}""");

        MasqueDesTotauxFiges.appliquer(overview, json("{}"), json("null"));

        assertThat(overview.path("totaux").path("patients").isNull()).isTrue();
        assertThat(overview.path("totaux").path("patientsSousKt").isNull()).isTrue();
        assertThat(overview.path("totaux").path("seances").asLong()).isEqualTo(300);
        assertThat(overview.path("centres").get(0).path("patients").asLong()).isEqualTo(50);
    }

    @Test
    void un_total_compose_de_centres_publies_est_conserve_et_la_lecture_est_idempotente() {
        JsonNode overview = json("""
                {"centres":[{"patients":50,"patientsSousKt":0},{"patients":5,"patientsSousKt":0}],
                 "totaux":{"patients":55,"patientsSousKt":0}}""");

        MasqueDesTotauxFiges.appliquer(overview, json("{}"), json("{}"));
        MasqueDesTotauxFiges.appliquer(overview, json("{}"), json("{}"));

        assertThat(overview.path("totaux").path("patients").asLong()).isEqualTo(55);
        assertThat(overview.path("totaux").path("patientsSousKt").asLong()).isZero();
    }

    @Test
    void les_consolides_cliniques_et_les_taux_des_marqueurs_sont_supprimes_champ_par_champ() {
        JsonNode indicateurs = json("""
                {"centres":[
                  {"clinique":{"hemoglobine":{"evalues":20,"pctDansCible":50.0},"vhbPositifs":6,"vhcPositifs":null,"greffesPeriode":0}},
                  {"clinique":{"hemoglobine":{"evalues":null,"pctDansCible":null},"vhbPositifs":7,"vhcPositifs":null,"greffesPeriode":0}}],
                 "totaux":{"clinique":{"hemoglobine":{"evalues":23,"pctDansCible":47.8,"pctSousCible":10.0,"pctAuDessus":5.0},
                   "vhbPositifs":13,"vhcPositifs":3,"greffesPeriode":0}}}""");

        MasqueDesTotauxFiges.appliquer(json("{}"), indicateurs, json("{}"));

        JsonNode total = indicateurs.path("totaux").path("clinique");
        assertThat(total.path("vhbPositifs").asLong()).isEqualTo(13);
        assertThat(total.path("vhcPositifs").isNull()).isTrue();
        assertThat(total.path("greffesPeriode").asLong()).isZero();
        assertThat(total.path("hemoglobine").path("evalues").isNull()).isTrue();
        assertThat(total.path("hemoglobine").path("pctDansCible").isNull()).isTrue();
        assertThat(total.path("hemoglobine").path("pctAuDessus").isNull()).isTrue();
    }

    @Test
    void le_total_d_une_caisse_est_supprime_selon_les_cellules_de_la_meme_caisse() {
        JsonNode repartition = json("""
                {"caisses":[{"caisseCode":"CNAS","patients":40},{"caisseCode":"CNAS","patients":null},
                            {"caisseCode":"CASNOS","patients":10},{"caisseCode":"CASNOS","patients":8}],
                 "caisseTotaux":[{"caisseCode":"CNAS","patients":42,"seances":9},{"caisseCode":"CASNOS","patients":18}]}""");

        MasqueDesTotauxFiges.appliquer(json("{}"), json("{}"), repartition);

        assertThat(repartition.path("caisseTotaux").get(0).path("patients").isNull()).isTrue();
        assertThat(repartition.path("caisseTotaux").get(0).path("seances").asLong()).isEqualTo(9);
        assertThat(repartition.path("caisseTotaux").get(1).path("patients").asLong()).isEqualTo(18);
    }

    @Test
    void un_contenu_sans_section_ou_sans_centre_est_laisse_tel_quel() {
        JsonNode sansCentre = json("{\"centres\":[],\"totaux\":{\"patients\":0}}");

        MasqueDesTotauxFiges.appliquer(sansCentre, json("null"), json("{\"caisses\":[]}"));

        assertThat(sansCentre.path("totaux").path("patients").asLong()).isZero();
    }
}
