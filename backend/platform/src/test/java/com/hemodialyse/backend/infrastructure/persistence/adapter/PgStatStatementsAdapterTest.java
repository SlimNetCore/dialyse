package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.application.supervision.TriRequetes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PgStatStatementsAdapterTest {

    @Autowired
    private PgStatStatementsAdapter adapter;

    @Test
    void on_h2_the_measure_is_reported_unavailable_instead_of_failing() {
        var statut = adapter.statut();

        assertFalse(statut.disponible());
        assertEquals("BASE_NON_POSTGRESQL", statut.raison());
        assertEquals(0, statut.tempsTotalMs());
    }

    @Test
    void the_sort_column_comes_from_a_closed_whitelist() {
        assertEquals("total_exec_time", PgStatStatementsAdapter.colonneDeTri(TriRequetes.TEMPS_TOTAL));
        assertEquals("mean_exec_time", PgStatStatementsAdapter.colonneDeTri(TriRequetes.TEMPS_MOYEN));
        assertEquals("calls", PgStatStatementsAdapter.colonneDeTri(TriRequetes.APPELS));
    }

    @Test
    void a_long_query_text_is_truncated_for_display() {
        String longue = "SELECT " + "x".repeat(PgStatStatementsAdapter.TEXTE_MAX + 50);

        String tronquee = PgStatStatementsAdapter.tronquer(longue);

        assertEquals(PgStatStatementsAdapter.TEXTE_MAX + 1, tronquee.length());
        assertTrue(tronquee.endsWith("…"));
        assertEquals("", PgStatStatementsAdapter.tronquer(null));
        assertEquals("SELECT 1", PgStatStatementsAdapter.tronquer("SELECT 1"));
    }
}
