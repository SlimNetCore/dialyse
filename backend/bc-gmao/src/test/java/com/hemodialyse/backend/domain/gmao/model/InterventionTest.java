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

        i.terminer("Pièce remplacée", StatutEquipement.EN_SERVICE, UUID.randomUUID());
        assertEquals(StatutIntervention.TERMINEE, i.getStatut());
        assertEquals("Pièce remplacée", i.getActions());
        assertNotNull(i.getDateFin());
    }

    @Test
    void creer_should_require_a_valid_equipment_state_at_intervention_time() {
        assertThrows(IllegalArgumentException.class, () -> Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE, LocalDateTime.now(), "Panne",
                null, null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE, LocalDateTime.now(), "Panne",
                null, StatutEquipement.REFORME, UUID.randomUUID()));
    }

    @Test
    void terminer_should_record_the_state_after_and_allow_a_reform_proposal() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());

        i.terminer("Irréparable", StatutEquipement.A_REFORMER, UUID.randomUUID());

        assertEquals(StatutEquipement.EN_MAINTENANCE, i.getEtatEquipementAvant());
        assertEquals(StatutEquipement.A_REFORMER, i.getEtatEquipementApres());
    }

    @Test
    void terminer_should_reject_a_missing_or_final_state_after() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> i.terminer("Fait", null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> i.terminer("Fait", StatutEquipement.REFORME, UUID.randomUUID()));
        assertEquals(StatutIntervention.EN_COURS, i.getStatut());
    }

    @Test
    void terminer_should_reject_an_intervention_not_in_progress() {
        Intervention i = intervention();

        assertThrows(IllegalStateException.class, () -> i.terminer("x", StatutEquipement.EN_SERVICE, UUID.randomUUID()));
    }

    @Test
    void annuler_should_reject_an_already_finished_intervention() {
        Intervention i = intervention();
        i.demarrer(UUID.randomUUID());
        i.terminer("Fait", StatutEquipement.EN_SERVICE, UUID.randomUUID());

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

    @Test
    void appliquerTarifIntervenant_should_add_labour_line_from_duration_and_rate() {
        Intervention i = terminee(LocalDateTime.of(2026, 1, 5, 8, 0), LocalDateTime.of(2026, 1, 5, 10, 30));

        assertTrue(i.appliquerTarifIntervenant(new BigDecimal("2000"), UUID.randomUUID()));

        assertEquals(1, i.getLignesCout().size());
        assertEquals(TypeLigneCout.INTERVENANT, i.getLignesCout().get(0).getType());
        assertEquals(0, new BigDecimal("5000").compareTo(i.coutTotal()));
    }

    @Test
    void appliquerTarifIntervenant_should_be_idempotent_and_skip_missing_rate() {
        Intervention i = terminee(LocalDateTime.of(2026, 1, 5, 8, 0), LocalDateTime.of(2026, 1, 5, 10, 0));

        assertFalse(i.appliquerTarifIntervenant(null, UUID.randomUUID()));
        assertTrue(i.appliquerTarifIntervenant(new BigDecimal("1000"), UUID.randomUUID()));
        assertFalse(i.appliquerTarifIntervenant(new BigDecimal("1000"), UUID.randomUUID()));
        assertEquals(1, i.getLignesCout().size());
    }

    @Test
    void appliquerTarifIntervenant_should_ignore_an_intervention_not_finished() {
        assertFalse(intervention().appliquerTarifIntervenant(new BigDecimal("1000"), UUID.randomUUID()));
    }

    private Intervention terminee(LocalDateTime debut, LocalDateTime fin) {
        return Intervention.reconstruct(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                StatutIntervention.TERMINEE, debut, fin, UUID.randomUUID(), "Panne", "Fait", null, null,
                debut, fin, UUID.randomUUID(), UUID.randomUUID(), null,
                StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_SERVICE);
    }

    private Intervention intervention() {
        return Intervention.creer(
                UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                LocalDateTime.now(), "Panne pompe", UUID.randomUUID(), StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
    }
}
