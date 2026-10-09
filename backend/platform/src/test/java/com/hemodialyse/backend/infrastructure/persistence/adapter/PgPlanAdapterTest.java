package com.hemodialyse.backend.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garde-fous de l'analyse de plan : seules les lectures simples sont préparées. L'établissement réel du plan
 * ({@code PREPARE} + {@code EXPLAIN EXECUTE}) ne se teste que sur PostgreSQL.
 */
class PgPlanAdapterTest {

    @Test
    void single_read_statements_are_analysed() {
        assertTrue(PgPlanAdapter.estLectureSimple("SELECT id FROM seances WHERE center_id = $1"));
        assertTrue(PgPlanAdapter.estLectureSimple("  select 1;  "));
        assertTrue(PgPlanAdapter.estLectureSimple("WITH t AS (SELECT id FROM patients) SELECT * FROM t"));
    }

    @Test
    void writes_multiple_statements_and_hidden_writes_are_never_prepared() {
        assertFalse(PgPlanAdapter.estLectureSimple("UPDATE seances SET statut = $1"));
        assertFalse(PgPlanAdapter.estLectureSimple("INSERT INTO audit_log (id) VALUES ($1)"));
        assertFalse(PgPlanAdapter.estLectureSimple("DELETE FROM seances"));
        assertFalse(PgPlanAdapter.estLectureSimple("SELECT 1; DROP TABLE seances"));
        assertFalse(PgPlanAdapter.estLectureSimple("WITH x AS (DELETE FROM seances RETURNING id) SELECT * FROM x"));
        assertFalse(PgPlanAdapter.estLectureSimple("WITH x AS (INSERT INTO t (a) VALUES ($1) RETURNING a) SELECT a FROM x"));
        assertFalse(PgPlanAdapter.estLectureSimple("SET statement_timeout = 0"));
        assertFalse(PgPlanAdapter.estLectureSimple(null));
    }

    @Test
    void a_very_long_plan_is_truncated() {
        String long_ = "x".repeat(PgPlanAdapter.PLAN_TEXTE_MAX + 10);

        assertEquals(PgPlanAdapter.PLAN_TEXTE_MAX + 1, PgPlanAdapter.tronquer(long_).length());
        assertEquals("court", PgPlanAdapter.tronquer("court"));
    }
}
