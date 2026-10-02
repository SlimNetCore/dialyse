package com.hemodialyse.backend.domain.planning.service;

import com.hemodialyse.backend.domain.planning.model.SalleVue;
import com.hemodialyse.backend.domain.planning.model.SalleVue.GenerateurVue;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapaciteSalleRegleTest {

    private static GenerateurVue generateur(String code) {
        return new GenerateurVue(UUID.randomUUID(), code, "Générateur " + code, "EN_SERVICE");
    }

    @Test
    void a_room_below_its_capacity_accepts_one_more_generator() {
        assertDoesNotThrow(() -> CapaciteSalleRegle.verifierAffectation(3, 2, "Salle 1"));
    }

    @Test
    void a_room_at_its_capacity_refuses_the_assignment() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> CapaciteSalleRegle.verifierAffectation(2, 2, "Salle 1"));
        assertEquals("SALLE_CAPACITE_DEPASSEE", e.getCode());
    }

    @Test
    void a_room_without_capacity_is_unlimited() {
        assertDoesNotThrow(() -> CapaciteSalleRegle.verifierAffectation(null, 500, "Salle 1"));
    }

    @Test
    void the_room_view_counts_generators_and_remaining_places() {
        SalleVue salle = new SalleVue(UUID.randomUUID(), "S1", "Salle 1", false, 3, List.of(generateur("G1")));

        assertEquals(1, salle.nbGenerateurs());
        assertEquals(2, salle.placesRestantes());
        assertFalse(salle.depassement());
    }

    @Test
    void the_room_view_flags_a_capacity_lowered_below_the_assigned_generators() {
        SalleVue salle = new SalleVue(UUID.randomUUID(), "S1", "Salle 1", true, 1,
                List.of(generateur("G1"), generateur("G2")));

        assertEquals(-1, salle.placesRestantes());
        assertTrue(salle.depassement());
    }

    @Test
    void the_room_view_of_an_unlimited_room_has_no_remaining_places_figure() {
        SalleVue salle = new SalleVue(UUID.randomUUID(), "S1", "Salle 1", false, null, List.of(generateur("G1")));

        assertNull(salle.placesRestantes());
        assertFalse(salle.depassement());
    }
}
