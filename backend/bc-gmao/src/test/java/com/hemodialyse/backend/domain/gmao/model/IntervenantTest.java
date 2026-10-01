package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntervenantTest {

    @Test
    void creer_should_build_an_active_intervenant() {
        UUID centreId = UUID.randomUUID();
        Intervenant i = Intervenant.creer(centreId, "Ahmed B.", TypeIntervenant.INTERNE, "0555000000",
                "ahmed@centre.dz", new BigDecimal("1500"));

        assertEquals(centreId, i.centreId());
        assertTrue(i.actif());
        assertEquals(TypeIntervenant.INTERNE, i.type());
    }

    @Test
    void creer_should_reject_blank_nom() {
        assertThrows(IllegalArgumentException.class, () -> Intervenant.creer(
                UUID.randomUUID(), "  ", TypeIntervenant.EXTERNE, null, null, null));
    }

    @Test
    void creer_should_reject_negative_tarif() {
        assertThrows(IllegalArgumentException.class, () -> Intervenant.creer(
                UUID.randomUUID(), "Fresenius Service", TypeIntervenant.EXTERNE, null, null, new BigDecimal("-1")));
    }

    @Test
    void desactiver_should_keep_other_fields_unchanged() {
        Intervenant i = Intervenant.creer(UUID.randomUUID(), "Ahmed B.", TypeIntervenant.INTERNE, null, null, null);

        Intervenant desactive = i.desactiver();

        assertFalse(desactive.actif());
        assertEquals(i.nom(), desactive.nom());
        assertEquals(i.id(), desactive.id());
    }

    @Test
    void modifier_should_update_fields_and_keep_id_and_actif() {
        Intervenant i = Intervenant.creer(UUID.randomUUID(), "Ahmed B.", TypeIntervenant.INTERNE, null, null, null);

        Intervenant modifie = i.modifier("Ahmed Benali", TypeIntervenant.EXTERNE, "0555", "a@b.dz", new BigDecimal("2000"));

        assertEquals("Ahmed Benali", modifie.nom());
        assertEquals(TypeIntervenant.EXTERNE, modifie.type());
        assertEquals(i.id(), modifie.id());
        assertTrue(modifie.actif());
    }
}
