package com.hemodialyse.backend.contexts.comptabilite.domain.periode;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodeComptableTest {
    @Test
    void cloture_est_irreversible_et_conserve_l_auteur() {
        PeriodeComptable periode = PeriodeComptable.ouverte(new CentreId(UUID.randomUUID()), YearMonth.of(2026, 3));
        Instant date = Instant.parse("2026-04-01T00:00:00Z");

        periode.cloturer(date, "  comptable  ");

        assertTrue(periode.estCloturee());
        assertEquals(date, periode.clotureeLe());
        assertEquals("  comptable  ", periode.clotureePar());
        assertThrows(PeriodeDejaCloturee.class, () -> periode.cloturer(date, "second"));
    }
}
