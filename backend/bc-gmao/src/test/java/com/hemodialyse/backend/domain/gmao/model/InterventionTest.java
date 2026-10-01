package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de domaine purs (JUnit 5, sans Spring/JPA — AGENTS.md §3) pour l'agrégat Intervention.
 */
class InterventionTest {

    @Test
    void demarrer_then_terminer_should_transition_statut() {
        Intervention i = intervention();

        i.demarrer(UUID.randomUUID());
        assertEquals(StatutIntervention.EN_COURS, i.getStatut());

        i.terminer("Pièce remplacée", UUID.randomUUID());
        assertEquals(StatutIntervention.TERMINEE, i.getStatut());
        assertEquals("Pièce remplacée", i.getActions());
        assertNotNull(i.getDateFin());
    }

    @Test
    void terminer_should_reject_an_intervention_not_in_progress() {
        Intervention i = intervention();

        assertThrows(IllegalStateException.class, () -> i.terminer("x", UUID.randomUUID()));
    }

    @Test
    void annuler_should_reject_an_already_finished_intervention() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());
        i.terminer("Fait", UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> i.annuler("raison", UUID.randomUUID()));
    }

    @Test
    void ajouterLigneCout_should_accumulate_and_compute_coutTotal() {
        Intervention i = intervention();

        i.ajouterLigneCout(LigneCoutIntervention.creer(
                        TypeLigneCout.PIECE, "Filtre RO", new BigDecimal("2"), new BigDecimal("1500.00"), null),
                UUID.randomUUID());
        i.ajouterLigneCout(LigneCoutIntervention.creer(
                        TypeLigneCout.MAIN_OEUVRE, "2h de main d'œuvre", new BigDecimal("2"), new BigDecimal("2000.00"), null),
                UUID.randomUUID());

        assertEquals(2, i.getLignesCout().size());
        assertEquals(new BigDecimal("7000.00"), i.coutTotal());
    }

    @Test
    void coutTotal_should_be_zero_without_any_cost_line() {
        assertEquals(BigDecimal.ZERO, intervention().coutTotal());
    }

    private Intervention intervention() {
        return Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                LocalDateTime.now(), "Panne pompe", UUID.randomUUID(), UUID.randomUUID());
    }
}
