package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlerteDirectionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate JOUR = LocalDate.of(2026, 3, 10);

    private AlerteDirection alerteValide() {
        return AlerteDirection.generer(new AlerteDirectionId(UUID.randomUUID()), CENTRE,
                TypeAlerte.SOUS_EFFECTIF, "ratio<0.8", "Sous-effectif détecté", JOUR, MAINTENANT);
    }

    @Test
    void genere_alerte_non_lue() {
        AlerteDirection a = alerteValide();
        assertFalse(a.lue());
        assertEquals(TypeAlerte.SOUS_EFFECTIF, a.type());
    }

    @Test
    void acquitter_alerte() {
        AlerteDirection a = alerteValide().acquitter(MAINTENANT);
        assertTrue(a.lue());
    }

    @Test
    void rejeter_double_acquittement() {
        AlerteDirection a = alerteValide().acquitter(MAINTENANT);
        assertThrows(IllegalArgumentException.class, () -> a.acquitter(MAINTENANT));
    }

    @Test
    void rejeter_type_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                AlerteDirection.generer(new AlerteDirectionId(UUID.randomUUID()), CENTRE,
                        null, null, null, JOUR, MAINTENANT));
    }

    @Test
    void rejeter_centre_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                AlerteDirection.generer(new AlerteDirectionId(UUID.randomUUID()), null,
                        TypeAlerte.SOUS_EFFECTIF, null, null, JOUR, MAINTENANT));
    }
}
