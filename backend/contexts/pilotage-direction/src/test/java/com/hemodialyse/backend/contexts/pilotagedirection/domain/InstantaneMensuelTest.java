package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstantaneMensuelTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-31T23:00:00Z"), ZoneOffset.UTC);

    @Test
    void capture_instantane_fige() {
        InstantaneMensuel i = InstantaneMensuel.capturer(new InstantaneMensuelId(UUID.randomUUID()),
                CENTRE, Mois.de(2026, 3), "{\"seances\":120}", MAINTENANT);
        assertTrue(i.fige());
        assertEquals(3, i.mois().valeur().getMonthValue());
    }

    @Test
    void rejeter_centre_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                InstantaneMensuel.capturer(new InstantaneMensuelId(UUID.randomUUID()), null,
                        Mois.de(2026, 3), "{}", MAINTENANT));
    }

    @Test
    void rejeter_mois_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                InstantaneMensuel.capturer(new InstantaneMensuelId(UUID.randomUUID()), CENTRE,
                        null, "{}", MAINTENANT));
    }

    @Test
    void rejeter_donnees_vides() {
        assertThrows(IllegalArgumentException.class, () ->
                InstantaneMensuel.capturer(new InstantaneMensuelId(UUID.randomUUID()), CENTRE,
                        Mois.de(2026, 3), "  ", MAINTENANT));
    }
}
