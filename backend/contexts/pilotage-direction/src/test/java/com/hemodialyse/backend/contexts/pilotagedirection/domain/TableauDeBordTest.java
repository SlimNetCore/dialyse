package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableauDeBordTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void actualise_tableau_de_bord() {
        TableauDeBord t = TableauDeBord.actualiser(CENTRE, "{\"seances\":10}", MAINTENANT);
        assertEquals(CENTRE, t.centreId());
        assertEquals("{\"seances\":10}", t.indicateurs());
    }

    @Test
    void rejeter_centre_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                TableauDeBord.actualiser(null, "{}", MAINTENANT));
    }

    @Test
    void rejeter_indicateurs_vides() {
        assertThrows(IllegalArgumentException.class, () ->
                TableauDeBord.actualiser(CENTRE, "  ", MAINTENANT));
    }

    @Test
    void rejeter_maintenant_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                TableauDeBord.actualiser(CENTRE, "{}", null));
    }
}
