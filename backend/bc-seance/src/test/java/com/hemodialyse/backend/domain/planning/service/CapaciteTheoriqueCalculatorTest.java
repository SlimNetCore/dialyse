package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Niveau;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Resultat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapaciteTheoriqueCalculatorTest {

    private static Resultat calculer(int generateurs, int series, long fileActive) {
        return CapaciteTheoriqueCalculator.calculer(generateurs, series,
                CapaciteTheoriqueCalculator.PATIENTS_PAR_POSTE_ET_SERIE_DEFAUT, fileActive);
    }

    @Test
    void the_reference_example_gives_84_patients() {
        Resultat r = calculer(16, 2, 0);

        assertEquals(2, r.generateursSecours());
        assertEquals(14, r.postesActifs());
        assertEquals(3, r.patientsParPosteEtSerie());
        assertEquals(84, r.capacite());
    }

    @Test
    void the_number_of_patients_per_station_and_shift_is_configurable() {
        assertEquals(56, CapaciteTheoriqueCalculator.calculer(16, 2, 2, 0).capacite());
        assertEquals(112, CapaciteTheoriqueCalculator.calculer(16, 2, 4, 0).capacite());
        assertEquals(4, CapaciteTheoriqueCalculator.calculer(16, 2, 4, 0).patientsParPosteEtSerie());
        assertEquals(0, CapaciteTheoriqueCalculator.calculer(16, 2, 0, 5).capacite());
    }

    @Test
    void one_spare_generator_is_required_for_eight_rounded_up() {
        assertEquals(0, CapaciteTheoriqueCalculator.generateursDeSecours(0));
        assertEquals(1, CapaciteTheoriqueCalculator.generateursDeSecours(1));
        assertEquals(1, CapaciteTheoriqueCalculator.generateursDeSecours(8));
        assertEquals(2, CapaciteTheoriqueCalculator.generateursDeSecours(9));
        assertEquals(2, CapaciteTheoriqueCalculator.generateursDeSecours(16));
        assertEquals(3, CapaciteTheoriqueCalculator.generateursDeSecours(17));
    }

    @Test
    void the_occupation_rate_and_level_follow_the_active_file() {
        Resultat marge = calculer(16, 2, 42);
        assertEquals(0, marge.tauxOccupation().compareTo(new BigDecimal("50.0")));
        assertEquals(Niveau.MARGE, marge.niveau());

        Resultat proche = calculer(16, 2, 80);
        assertEquals(Niveau.PROCHE, proche.niveau());
        assertFalse(proche.atteinte());

        Resultat atteinte = calculer(16, 2, 84);
        assertEquals(Niveau.ATTEINTE, atteinte.niveau());
        assertTrue(atteinte.atteinte());

        Resultat depassee = calculer(16, 2, 100);
        assertTrue(depassee.atteinte());
        assertEquals(0, depassee.tauxOccupation().compareTo(new BigDecimal("119.0")));
    }

    @Test
    void a_centre_without_active_post_or_series_has_no_capacity() {
        for (Resultat r : new Resultat[]{calculer(1, 2, 10), calculer(0, 2, 10), calculer(16, 0, 10)}) {
            assertEquals(Niveau.SANS_CAPACITE, r.niveau());
            assertEquals(0, r.capacite());
            assertNull(r.tauxOccupation());
            assertFalse(r.atteinte());
        }
    }

    @Test
    void consolidation_adds_capacities_and_active_files() {
        Resultat a = calculer(16, 2, 84);   // capacité 84, atteinte
        Resultat b = CapaciteTheoriqueCalculator.calculer(8, 2, 3, 10);    // 7 postes × 2 × 3 = 42

        Resultat total = CapaciteTheoriqueCalculator.agreger(List.of(a, b));

        assertEquals(24, total.generateurs());
        assertEquals(126, total.capacite());
        assertEquals(94, total.fileActive());
        assertEquals(0, total.tauxOccupation().compareTo(new BigDecimal("74.6")));
        assertEquals(Niveau.MARGE, total.niveau());
        assertEquals(Niveau.SANS_CAPACITE, CapaciteTheoriqueCalculator.agreger(List.of()).niveau());
    }

    @Test
    void the_capacity_scales_with_the_number_of_series() {
        assertEquals(42, calculer(16, 1, 0).capacite());
        assertEquals(126, calculer(16, 3, 0).capacite());
    }
}
