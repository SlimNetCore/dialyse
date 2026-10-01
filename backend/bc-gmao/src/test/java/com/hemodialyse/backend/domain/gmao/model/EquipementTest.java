package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

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
                "Fresenius", "4008S", "SN-1", LocalDateTime.now(), UUID.randomUUID(), "Salle 1", createur);

        UUID editeur = UUID.randomUUID();
        eq.modifier("Générateur révisé", "Nipro", "Surdial X", "SN-2", "Salle 3", editeur);

        assertEquals("EQ-001", eq.getCode());
        assertEquals(TypeEquipement.GENERATEUR_DIALYSE, eq.getType());
        assertEquals("Générateur révisé", eq.getDesignation());
        assertEquals("Nipro", eq.getFabricant());
        assertEquals("Surdial X", eq.getModele());
        assertEquals("SN-2", eq.getNumeroSerie());
        assertEquals("Salle 3", eq.getLocalisation());
        assertEquals(editeur, eq.getModifiePar());
        assertNotNull(eq.getDateModification());
    }

    @Test
    void modifier_should_reject_blank_designation() {
        Equipement eq = Equipement.creer(
                "EQ-002", "Générateur", TypeEquipement.GENERATEUR_DIALYSE,
                null, null, null, LocalDateTime.now(), UUID.randomUUID(), null, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> eq.modifier("  ", null, null, null, null, UUID.randomUUID()));
    }

    @Test
    void marquerHorsService_then_reactiver_should_round_trip_statut() {
        Equipement eq = Equipement.creer(
                "EQ-003", "Générateur", TypeEquipement.GENERATEUR_DIALYSE,
                null, null, null, LocalDateTime.now(), UUID.randomUUID(), null, UUID.randomUUID());

        eq.marquerHorsService("Panne pompe", UUID.randomUUID());
        assertEquals(StatutEquipement.HORS_SERVICE, eq.getStatut());

        eq.reactiver(UUID.randomUUID());
        assertEquals(StatutEquipement.EN_SERVICE, eq.getStatut());
    }
}
