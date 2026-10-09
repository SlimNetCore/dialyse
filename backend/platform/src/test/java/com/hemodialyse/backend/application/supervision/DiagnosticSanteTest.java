package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.SanteBase.IndexInutilise;
import com.hemodialyse.backend.application.supervision.SanteBase.StatutPartitionnement;
import com.hemodialyse.backend.application.supervision.SanteBase.TableSante;
import com.hemodialyse.backend.application.supervision.port.SanteBasePort.Generale;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticSanteTest {

    private static final Generale SAINE = new Generale(1_000_000, 99.5, 10, 100, null);

    private static TableSante table(String nom, long taille, long lignes, long mortes, long vivantes, long scans,
                                    long lues, long idx) {
        return new TableSante(nom, taille, lignes, mortes, vivantes, scans, lues, idx, null);
    }

    private static List<String> codes(SanteBase s) {
        return s.alertes().stream().map(a -> a.code() + ":" + a.cible()).toList();
    }

    @Test
    void a_healthy_database_raises_nothing() {
        var sante = DiagnosticSante.evaluer(SAINE, List.of(table("seances", 5_000_000, 130_000, 100, 130_000, 10, 100, 5000)),
                List.of());

        assertTrue(sante.disponible());
        assertTrue(sante.alertes().isEmpty());
        assertEquals(StatutPartitionnement.OK, sante.tables().get(0).partitionnement());
    }

    @Test
    void a_weak_cache_is_an_attention_then_critical_below_ninety_percent() {
        var attention = DiagnosticSante.evaluer(new Generale(1, 93, 1, 100, null), List.of(), List.of());
        var critique = DiagnosticSante.evaluer(new Generale(1, 85, 1, 100, null), List.of(), List.of());

        assertEquals("ATTENTION", attention.alertes().get(0).niveau());
        assertEquals("CRITIQUE", critique.alertes().get(0).niveau());
        assertEquals("CACHE_FAIBLE", critique.alertes().get(0).code());
    }

    @Test
    void saturated_connections_are_reported() {
        var sante = DiagnosticSante.evaluer(new Generale(1, 99, 85, 100, null), List.of(), List.of());

        assertEquals("CONNEXIONS_SATUREES", sante.alertes().get(0).code());
        assertEquals("85/100", sante.alertes().get(0).valeur());
    }

    @Test
    void partitioning_thresholds_come_from_the_study_with_a_watch_level_at_half() {
        long gio = 1024L * 1024 * 1024;
        assertEquals(StatutPartitionnement.A_ETUDIER,
                DiagnosticSante.statutPartitionnement(table("a", 0, 20_000_000, 0, 0, 0, 0, 0)));
        assertEquals(StatutPartitionnement.A_ETUDIER,
                DiagnosticSante.statutPartitionnement(table("b", 10 * gio, 100, 0, 0, 0, 0, 0)));
        assertEquals(StatutPartitionnement.A_SURVEILLER,
                DiagnosticSante.statutPartitionnement(table("c", 6 * gio, 100, 0, 0, 0, 0, 0)));
        assertEquals(StatutPartitionnement.A_SURVEILLER,
                DiagnosticSante.statutPartitionnement(table("d", 0, 12_000_000, 0, 0, 0, 0, 0)));
        assertEquals(StatutPartitionnement.OK, DiagnosticSante.statutPartitionnement(table("e", gio, 1_000_000, 0, 0, 0, 0, 0)));

        var sante = DiagnosticSante.evaluer(SAINE, List.of(table("audit_log", gio, 25_000_000, 0, 25_000_000, 0, 0, 1)), List.of());
        assertEquals(List.of("PARTITIONNEMENT_A_ETUDIER:audit_log"), codes(sante));
    }

    @Test
    void dead_rows_and_full_table_reads_are_reported_for_large_tables_only() {
        var gonflee = table("notification_evenement", 1, 50_000, 30_000, 70_000, 0, 0, 0);
        var lue = table("seances", 1, 130_000, 0, 130_000, 5000, 5000L * 130_000, 200);
        var petite = table("salle", 1, 12, 500, 20, 90_000, 90_000L * 12, 0);

        var sante = DiagnosticSante.evaluer(SAINE, List.of(gonflee, lue, petite), List.of());

        assertTrue(codes(sante).contains("LIGNES_MORTES:notification_evenement"));
        assertTrue(codes(sante).contains("TABLE_LUE_EN_ENTIER:seances"));
        assertEquals(2, sante.alertes().size(), "une petite table lue en entier n'est pas un problème");
    }

    @Test
    void unused_indexes_are_listed_as_information_and_alerts_are_ordered_by_severity() {
        var sante = DiagnosticSante.evaluer(new Generale(1, 80, 1, 100, null), List.of(),
                List.of(new IndexInutilise("seances", "idx_inutile", 8192)));

        assertEquals("CACHE_FAIBLE", sante.alertes().get(0).code());
        assertEquals("INDEX_INUTILISES", sante.alertes().get(1).code());
        assertEquals("INFO", sante.alertes().get(1).niveau());
        assertEquals(1, sante.indexInutilises().size());
    }
}
