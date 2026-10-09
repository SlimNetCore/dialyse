package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.port.AnalysePlanPort;
import com.hemodialyse.backend.application.supervision.port.SanteBasePort;
import com.hemodialyse.backend.application.supervision.port.StatistiquesRequetesPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionBaseServiceTest {

    private final AnalysePlanPort plans = mock(AnalysePlanPort.class);
    private final SanteBasePort sante = mock(SanteBasePort.class);

    private static RequeteStatistique requete(String id, double totalMs, double moyenMs) {
        return new RequeteStatistique(id, "SELECT 1", 10, totalMs, moyenMs, moyenMs * 2, 100, 0, 0, 0);
    }

    @Test
    void the_analysis_reads_the_plan_of_a_measured_query_and_advises_an_index_for_a_full_scan() {
        FauxPort port = new FauxPort();
        var service = new SupervisionBaseService(port, plans, sante);
        String json = """
                [{"Plan":{"Node Type":"Seq Scan","Relation Name":"seances","Filter":"(center_id = $1)","Total Cost":420.5}}]""";
        when(plans.planGenerique("42")).thenReturn(Optional.of(
                new AnalysePlanPort.PlanGenerique("SELECT * FROM seances WHERE center_id = $1", json, "Seq Scan on seances", null)));
        when(plans.definitionsIndex("seances")).thenReturn(List.of());
        when(plans.lignesEstimees("seances")).thenReturn(130_000L);

        var analyse = service.analyse("42");

        assertTrue(analyse.disponible());
        assertEquals(420.5, analyse.coutTotal());
        assertEquals(AnalyseRequete.Verdict.INDEX_RECOMMANDE, analyse.balayagesComplets().get(0).verdict());
        assertEquals("CREATE INDEX IF NOT EXISTS idx_seances_center_id ON seances (center_id);",
                analyse.balayagesComplets().get(0).indexSuggere());
    }

    @Test
    void the_analysis_of_an_unknown_query_or_without_measure_is_refused() {
        FauxPort port = new FauxPort();
        var service = new SupervisionBaseService(port, plans, sante);
        when(plans.planGenerique("1")).thenReturn(Optional.empty());

        assertEquals("SUPERVISION_REQUETE_INTROUVABLE",
                assertThrows(BusinessException.class, () -> service.analyse("1")).getCode());

        port.statut = StatutStatistiques.indisponible("BASE_NON_POSTGRESQL");
        assertEquals("SUPERVISION_INDISPONIBLE",
                assertThrows(BusinessException.class, () -> service.analyse("1")).getCode());
    }

    @Test
    void the_health_is_unavailable_outside_postgresql_and_diagnosed_otherwise() {
        var service = new SupervisionBaseService(new FauxPort(), plans, sante);
        when(sante.disponible()).thenReturn(false);

        var indisponible = service.sante();
        assertFalse(indisponible.disponible());
        assertEquals("BASE_NON_POSTGRESQL", indisponible.raison());

        when(sante.disponible()).thenReturn(true);
        when(sante.generale()).thenReturn(new SanteBasePort.Generale(1000, 80, 5, 100, null));
        when(sante.plusGrossesTables()).thenReturn(List.of());
        when(sante.indexInutilises()).thenReturn(List.of());

        var ok = service.sante();
        assertTrue(ok.disponible());
        assertEquals("CACHE_FAIBLE", ok.alertes().get(0).code());
    }

    @Test
    void each_ranked_query_carries_its_improvement_advice() {
        FauxPort port = new FauxPort();
        port.lignes = List.of(new RequeteStatistique("a", "SELECT * FROM seances WHERE center_id = $1", 100, 8000, 80,
                120, 50, 0, 0, 0));
        var service = new SupervisionBaseService(port, plans, sante);

        var conseils = service.requetes(TriRequetes.TEMPS_TOTAL, 0, 20).items().get(0).conseils();

        assertEquals("SCAN_PROBABLE", conseils.get(0).code());
    }

    @Test
    void each_query_gets_its_share_of_the_total_time_and_a_severity() {
        FauxPort port = new FauxPort();
        port.lignes = List.of(requete("a", 600, 800), requete("b", 300, 120), requete("c", 100, 5));
        var service = new SupervisionBaseService(port, plans, sante);

        var resultat = service.requetes(TriRequetes.TEMPS_TOTAL, 0, 20).items();

        assertEquals(60.0, resultat.get(0).partTempsTotalPct(), 0.001);
        assertEquals(NiveauRequete.CRITIQUE, resultat.get(0).niveau());
        assertEquals(NiveauRequete.ATTENTION, resultat.get(1).niveau());
        assertEquals(NiveauRequete.NORMAL, resultat.get(2).niveau());
    }

    @Test
    void paging_is_clamped_and_the_sort_is_forwarded() {
        FauxPort port = new FauxPort();
        var service = new SupervisionBaseService(port, plans, sante);

        service.requetes(TriRequetes.APPELS, -3, 9999);

        assertEquals(TriRequetes.APPELS, port.dernierTri);
        assertEquals(0, port.dernierePage);
        assertEquals(SupervisionBaseService.TAILLE_MAX, port.derniereTaille);

        service.requetes(TriRequetes.TEMPS_MOYEN, 2, 0);
        assertEquals(2, port.dernierePage);
        assertEquals(SupervisionBaseService.TAILLE_PAR_DEFAUT, port.derniereTaille);
    }

    @Test
    void an_unavailable_measure_returns_an_empty_page_without_querying_the_ranking() {
        FauxPort port = new FauxPort();
        port.statut = StatutStatistiques.indisponible("BASE_NON_POSTGRESQL");
        var service = new SupervisionBaseService(port, plans, sante);

        var resultat = service.requetes(TriRequetes.TEMPS_TOTAL, 0, 20);

        assertTrue(resultat.items().isEmpty());
        assertEquals(0, resultat.total());
        assertEquals(-1, port.dernierePage);
    }

    @Test
    void a_zero_total_time_gives_a_zero_share_instead_of_dividing_by_zero() {
        var analysee = SupervisionBaseService.analyser(requete("a", 0, 0), 0);

        assertEquals(0, analysee.partTempsTotalPct());
        assertEquals(NiveauRequete.NORMAL, analysee.niveau());
    }

    @Test
    void the_counters_are_reset_only_when_the_measure_is_available() {
        FauxPort port = new FauxPort();
        var service = new SupervisionBaseService(port, plans, sante);

        service.reinitialiser();
        assertEquals(1, port.reinitialisations);

        port.statut = StatutStatistiques.indisponible("EXTENSION_ABSENTE");
        var erreur = assertThrows(BusinessException.class, service::reinitialiser);
        assertEquals("SUPERVISION_INDISPONIBLE", erreur.getCode());
        assertEquals(1, port.reinitialisations);
    }

    @Test
    void a_database_failure_during_the_reset_becomes_a_business_error() {
        FauxPort port = new FauxPort();
        port.echecReinitialisation = true;
        var service = new SupervisionBaseService(port, plans, sante);

        var erreur = assertThrows(BusinessException.class, service::reinitialiser);

        assertEquals("SUPERVISION_REINITIALISATION_IMPOSSIBLE", erreur.getCode());
    }

    @Test
    void the_sort_falls_back_to_total_time_for_an_unknown_value() {
        assertEquals(TriRequetes.TEMPS_TOTAL, TriRequetes.depuis(null));
        assertEquals(TriRequetes.TEMPS_TOTAL, TriRequetes.depuis("n'importe quoi"));
        assertEquals(TriRequetes.APPELS, TriRequetes.depuis("appels"));
    }

    @Test
    void severity_thresholds() {
        assertEquals(NiveauRequete.CRITIQUE, NiveauRequete.evaluer(500, 0));
        assertEquals(NiveauRequete.ATTENTION, NiveauRequete.evaluer(100, 0));
        assertEquals(NiveauRequete.ATTENTION, NiveauRequete.evaluer(2, 20));
        assertEquals(NiveauRequete.NORMAL, NiveauRequete.evaluer(99.9, 19.9));
    }

    /**
     * Port de test : enregistre les paramètres reçus.
     */
    private static final class FauxPort implements StatistiquesRequetesPort {
        StatutStatistiques statut = new StatutStatistiques(true, null, Instant.parse("2026-10-01T00:00:00Z"), 1000);
        List<RequeteStatistique> lignes = List.of();
        TriRequetes dernierTri;
        int dernierePage = -1;
        int derniereTaille = -1;
        int reinitialisations;
        boolean echecReinitialisation;

        @Override
        public StatutStatistiques statut() {
            return statut;
        }

        @Override
        public PagedResult<RequeteStatistique> classement(TriRequetes tri, int page, int size) {
            dernierTri = tri;
            dernierePage = page;
            derniereTaille = size;
            return PagedResult.of(lignes, lignes.size(), page, size);
        }

        @Override
        public void reinitialiser() {
            if (echecReinitialisation) {
                throw new IllegalStateException("permission denied");
            }
            reinitialisations++;
        }
    }
}
