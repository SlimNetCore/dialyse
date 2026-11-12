package com.hemodialyse.backend.contexts.documents.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parametres reserves au serveur (KB-DOC1-2, ADR 0142). Inventaire : identite (SOCIETE_*, CENTRE_*), le centre (CENTER_ID), les totaux de la
 * synthese de facturation (TOTAL_HT|TVA|TTC_FACTURES), les parametres internes du moteur (REPORT_*, JASPER_*, IS_IGNORE_PAGINATION).
 */
class ParametresReservesTest {

    private static Map<String, String> client() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("patientId", "p1");
        m.put("PERIOD_START", "2026-01-01");
        m.put("CENTERID", "garde");
        m.put("MON_CENTRE", "garde");
        m.put("center_idx", "garde");
        return m;
    }

    @Test
    void should_keep_the_business_parameters_whatever_the_ordinary_names() {
        assertThat(ParametresReserves.ecarterLesReserves(client())).containsOnlyKeys("patientId", "PERIOD_START", "CENTERID", "MON_CENTRE", "center_idx");
    }

    @Test
    void should_drop_identity_parameters_whatever_the_case() {
        Map<String, String> m = client();
        m.put("SOCIETE_NOM", "x");
        m.put("centre_nom", "y");
        m.put("Centre_Ligne", "z");
        m.put("societe_logo", "w");

        assertThat(ParametresReserves.ecarterLIdentite(m)).containsOnlyKeys("patientId", "PERIOD_START", "CENTERID", "MON_CENTRE", "center_idx");
    }

    @Test
    void should_drop_the_centre_the_totals_and_the_engine_parameters_from_the_client_but_not_the_internal_identity_filter() {
        Map<String, String> m = client();
        for (String reserve : new String[]{"CENTER_ID", "center_id", "TOTAL_HT_FACTURES", "total_tva_factures", "TOTAL_TTC_FACTURES",
                "REPORT_CONNECTION", "report_locale", "REPORT_FORMAT_FACTORY", "JASPER_REPORT", "IS_IGNORE_PAGINATION", "SOCIETE_NOM"}) {
            m.put(reserve, "x");
        }

        assertThat(ParametresReserves.ecarterLesReserves(m)).containsOnlyKeys("patientId", "PERIOD_START", "CENTERID", "MON_CENTRE", "center_idx");
        // les appelants de confiance (contextes) gardent CENTER_ID et REPORT_* : seule l'identite leur est retiree
        assertThat(ParametresReserves.ecarterLIdentite(m)).containsKeys("CENTER_ID", "REPORT_CONNECTION", "TOTAL_HT_FACTURES")
                .doesNotContainKeys("SOCIETE_NOM");
    }

    @Test
    void should_keep_a_null_key_like_the_legacy_filter() {
        Map<String, String> m = new HashMap<>();
        m.put(null, "v");

        assertThat(ParametresReserves.ecarterLesReserves(m)).containsEntry(null, "v");
    }
}
