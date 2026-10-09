package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.AnalyseRequete.Verdict;
import com.hemodialyse.backend.application.supervision.port.AnalysePlanPort.PlanGenerique;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyseurPlanTest {

    private static PlanGenerique plan(String json) {
        return new PlanGenerique("SELECT 1", json, "plan", null);
    }

    private static AnalyseRequete analyser(String json, Map<String, List<String>> index, Map<String, Long> lignes) {
        return AnalyseurPlan.analyser(plan(json), t -> index.getOrDefault(t, List.of()), t -> lignes.getOrDefault(t, 0L));
    }

    @Test
    void a_full_scan_of_a_large_table_filtered_on_columns_gets_an_index_suggestion_equalities_first() {
        String json = """
                [{"Plan":{"Node Type":"Seq Scan","Relation Name":"seances",
                  "Filter":"((center_id = $1) AND (date_seance >= $2) AND (statut = $3))","Total Cost":812.3}}]""";

        var analyse = analyser(json, Map.of(), Map.of("seances", 130_000L));

        assertTrue(analyse.disponible());
        assertEquals(812.3, analyse.coutTotal());
        var b = analyse.balayagesComplets().get(0);
        assertEquals("seances", b.table());
        assertEquals(List.of("center_id", "statut", "date_seance"), b.colonnes());
        assertEquals(Verdict.INDEX_RECOMMANDE, b.verdict());
        assertEquals("CREATE INDEX IF NOT EXISTS idx_seances_center_id_statut_date_seance ON seances (center_id, statut, date_seance);",
                b.indexSuggere());
    }

    @Test
    void a_small_table_needs_no_index() {
        String json = """
                [{"Plan":{"Node Type":"Seq Scan","Relation Name":"salle","Filter":"(center_id = $1)","Total Cost":1.5}}]""";

        var b = analyser(json, Map.of(), Map.of("salle", 12L)).balayagesComplets().get(0);

        assertEquals(Verdict.TABLE_PETITE, b.verdict());
        assertNull(b.indexSuggere());
    }

    @Test
    void an_existing_index_on_the_first_filtered_column_is_reported_instead_of_a_new_one() {
        String json = """
                [{"Plan":{"Node Type":"Seq Scan","Relation Name":"seances","Filter":"(center_id = $1)","Total Cost":99}}]""";
        var index = Map.of("seances", List.of(
                "CREATE INDEX idx_seances_center_date ON public.seances USING btree (center_id, date_seance)"));

        var b = analyser(json, index, Map.of("seances", 130_000L)).balayagesComplets().get(0);

        assertEquals(Verdict.INDEX_PRESENT, b.verdict());
        assertNull(b.indexSuggere());
    }

    @Test
    void an_index_starting_with_another_column_does_not_count() {
        String json = """
                [{"Plan":{"Node Type":"Seq Scan","Relation Name":"seances","Filter":"(statut = $1)","Total Cost":99}}]""";
        var index = Map.of("seances", List.of(
                "CREATE INDEX idx_seances_center_date ON public.seances USING btree (center_id, statut)"));

        assertEquals(Verdict.INDEX_RECOMMANDE,
                analyser(json, index, Map.of("seances", 130_000L)).balayagesComplets().get(0).verdict());
    }

    @Test
    void a_scan_without_filter_is_an_intentional_full_read() {
        String json = """
                [{"Plan":{"Node Type":"Aggregate","Total Cost":500,"Plans":[
                  {"Node Type":"Seq Scan","Relation Name":"factures","Total Cost":400}]}}]""";

        var b = analyser(json, Map.of(), Map.of("factures", 44_000L)).balayagesComplets().get(0);

        assertEquals(Verdict.SANS_FILTRE, b.verdict());
    }

    @Test
    void index_scans_are_counted_and_nested_plans_are_walked() {
        String json = """
                [{"Plan":{"Node Type":"Nested Loop","Total Cost":50,"Plans":[
                  {"Node Type":"Index Scan","Relation Name":"patients","Index Name":"patients_pkey","Total Cost":8},
                  {"Node Type":"Bitmap Heap Scan","Relation Name":"seances","Total Cost":20,"Plans":[
                    {"Node Type":"Bitmap Index Scan","Index Name":"idx_seances_center_patient","Total Cost":5}]}]}}]""";

        var analyse = analyser(json, Map.of(), Map.of());

        assertEquals(2, analyse.indexUtilises());
        assertTrue(analyse.balayagesComplets().isEmpty());
    }

    @Test
    void a_function_wrapped_column_is_not_suggested_for_a_plain_index() {
        assertEquals(List.of("center_id"),
                AnalyseurPlan.colonnesFiltrees("((lower((nom)::text) = $1) AND (center_id = $2))"));
        assertEquals(List.of("center_id"), AnalyseurPlan.colonnesFiltrees("((s.center_id = $1))"));
        assertEquals(List.of("statut"), AnalyseurPlan.colonnesFiltrees("((statut)::text = $1)"));
        assertTrue(AnalyseurPlan.colonnesFiltrees("").isEmpty());
        assertTrue(AnalyseurPlan.colonnesFiltrees(null).isEmpty());
    }

    @Test
    void an_unplannable_or_unreadable_query_is_reported_unavailable() {
        var nonSelect = AnalyseurPlan.analyser(PlanGenerique.indisponible("UPDATE x SET y = $1", "NON_SELECT"),
                t -> List.of(), t -> 0);
        assertFalse(nonSelect.disponible());
        assertEquals("NON_SELECT", nonSelect.raison());

        var illisible = analyser("pas du json", Map.of(), Map.of());
        assertFalse(illisible.disponible());
        assertEquals("PLAN_IMPOSSIBLE", illisible.raison());

        assertEquals("PLAN_IMPOSSIBLE", analyser("[]", Map.of(), Map.of()).raison());
    }

    @Test
    void index_definitions_are_read_from_their_first_column() {
        assertTrue(AnalyseurPlan.couvre("CREATE INDEX i ON public.t USING btree (center_id, d DESC)", "center_id"));
        assertFalse(AnalyseurPlan.couvre("CREATE INDEX i ON public.t USING btree (d, center_id)", "center_id"));
        assertTrue(AnalyseurPlan.couvre("CREATE INDEX i ON public.t USING btree (center_id) WHERE (x IS NULL)", "center_id"));
        assertNull(AnalyseurPlan.ordreCreation("Table;DROP", List.of("a")));
    }
}
