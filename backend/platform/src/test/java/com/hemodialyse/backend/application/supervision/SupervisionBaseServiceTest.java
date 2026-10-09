package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.port.StatistiquesRequetesPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionBaseServiceTest {

    private static RequeteStatistique requete(String id, double totalMs, double moyenMs) {
        return new RequeteStatistique(id, "SELECT 1", 10, totalMs, moyenMs, moyenMs * 2, 100);
    }

    @Test
    void each_query_gets_its_share_of_the_total_time_and_a_severity() {
        FauxPort port = new FauxPort();
        port.lignes = List.of(requete("a", 600, 800), requete("b", 300, 120), requete("c", 100, 5));
        var service = new SupervisionBaseService(port);

        var resultat = service.requetes(TriRequetes.TEMPS_TOTAL, 0, 20).items();

        assertEquals(60.0, resultat.get(0).partTempsTotalPct(), 0.001);
        assertEquals(NiveauRequete.CRITIQUE, resultat.get(0).niveau());
        assertEquals(NiveauRequete.ATTENTION, resultat.get(1).niveau());
        assertEquals(NiveauRequete.NORMAL, resultat.get(2).niveau());
    }

    @Test
    void paging_is_clamped_and_the_sort_is_forwarded() {
        FauxPort port = new FauxPort();
        var service = new SupervisionBaseService(port);

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
        var service = new SupervisionBaseService(port);

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
        var service = new SupervisionBaseService(port);

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
        var service = new SupervisionBaseService(port);

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
