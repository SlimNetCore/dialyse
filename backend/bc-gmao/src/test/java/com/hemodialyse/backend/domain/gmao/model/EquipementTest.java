package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de domaine purs (JUnit 5, sans Spring/JPA — AGENTS.md §3) pour l'agrégat Equipement.
 */
class EquipementTest {

    @Test
    void modifier_should_update_editable_fields_but_keep_code_and_type_immutable() {
        UUID createur = UUID.randomUUID();
        Equipement eq = Equipement.creer(
                "EQ-001", "Générateur de dialyse", TypeEquipement.GENERATEUR_DIALYSE,
                "Fresenius", "4008S", "SN-1", LocalDateTime.now(), UUID.randomUUID(), "Salle 1", createur,
                null, new BigDecimal("1200000"));

        UUID editeur = UUID.randomUUID();
        UUID salleId = UUID.randomUUID();
        eq.modifier("Générateur révisé", "Nipro", "Surdial X", "SN-2", "Salle 3", editeur, salleId,
                new BigDecimal("1300000"));

        assertEquals("EQ-001", eq.getCode());
        assertEquals(TypeEquipement.GENERATEUR_DIALYSE, eq.getType());
        assertEquals("Générateur révisé", eq.getDesignation());
        assertEquals("Nipro", eq.getFabricant());
        assertEquals("Surdial X", eq.getModele());
        assertEquals("SN-2", eq.getNumeroSerie());
        assertEquals("Salle 3", eq.getLocalisation());
        assertEquals(editeur, eq.getModifiePar());
        assertEquals(salleId, eq.getSalleId());
        assertEquals(new BigDecimal("1300000"), eq.getPrixAcquisition());
        assertNotNull(eq.getDateModification());
    }

    @Test
    void modifier_should_reject_blank_designation() {
        Equipement eq = equipement();

        assertThrows(IllegalArgumentException.class,
                () -> eq.modifier("  ", null, null, null, null, UUID.randomUUID(), null, null));
    }

    @Test
    void creer_should_reject_negative_prix_acquisition() {
        assertThrows(IllegalArgumentException.class, () -> Equipement.creer(
                "EQ-004", "Générateur", TypeEquipement.GENERATEUR_DIALYSE,
                null, null, null, LocalDateTime.now(), UUID.randomUUID(), null, UUID.randomUUID(),
                null, new BigDecimal("-1")));
    }

    @Test
    void marquerHorsService_then_reactiver_should_round_trip_statut() {
        Equipement eq = equipement();

        eq.marquerHorsService("Panne pompe", UUID.randomUUID());
        assertEquals(StatutEquipement.HORS_SERVICE, eq.getStatut());

        eq.reactiver(UUID.randomUUID());
        assertEquals(StatutEquipement.EN_SERVICE, eq.getStatut());
    }

    @Test
    void reformer_should_be_terminal_and_reject_reactivation() {
        Equipement eq = equipement();

        eq.reformer("Fin de vie, pièces indisponibles", UUID.randomUUID());
        assertEquals(StatutEquipement.REFORME, eq.getStatut());

        assertThrows(IllegalStateException.class, () -> eq.reactiver(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> eq.reformer("encore", UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> eq.marquerHorsService("x", UUID.randomUUID()));
    }

    @Test
    void reformer_should_require_a_motif() {
        Equipement eq = equipement();

        assertThrows(IllegalArgumentException.class, () -> eq.reformer("  ", UUID.randomUUID()));
    }

    @Test
    void changerStatutIntervention_should_apply_operational_states_and_report_changes() {
        Equipement eq = equipement();

        assertTrue(eq.changerStatutIntervention(StatutEquipement.EN_MAINTENANCE, "Intervention démarrée", UUID.randomUUID()));
        assertEquals(StatutEquipement.EN_MAINTENANCE, eq.getStatut());
        assertFalse(eq.changerStatutIntervention(StatutEquipement.EN_MAINTENANCE, "idem", UUID.randomUUID()));
        assertTrue(eq.changerStatutIntervention(StatutEquipement.A_REFORMER, "Irréparable", UUID.randomUUID()));
        assertEquals(StatutEquipement.A_REFORMER, eq.getStatut());
    }

    @Test
    void changerStatutIntervention_should_never_reform_or_touch_a_reformed_equipment() {
        Equipement eq = equipement();

        assertThrows(IllegalArgumentException.class,
                () -> eq.changerStatutIntervention(StatutEquipement.REFORME, "x", UUID.randomUUID()));
        eq.reformer("Fin de vie", UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> eq.changerStatutIntervention(StatutEquipement.EN_SERVICE, "x", UUID.randomUUID()));
    }

    private Equipement equipement() {
        return Equipement.creer(
                "EQ-003", "Générateur", TypeEquipement.GENERATEUR_DIALYSE,
                null, null, null, LocalDateTime.now(), UUID.randomUUID(), null, UUID.randomUUID(),
                null, null);
    }
}
