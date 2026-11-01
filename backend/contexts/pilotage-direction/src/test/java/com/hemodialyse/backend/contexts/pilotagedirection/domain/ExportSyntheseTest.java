package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportSyntheseTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void genere_export() {
        ExportSynthese e = ExportSynthese.generer(new ExportSyntheseId(UUID.randomUUID()), CENTRE,
                "2026-03", FormatExport.PDF, "{\"centre\":\"x\"}", MAINTENANT);
        assertEquals(StatutExport.GENERE, e.statut());
        assertEquals(FormatExport.PDF, e.format());
    }

    @Test
    void rejeter_periode_vide() {
        assertThrows(IllegalArgumentException.class, () ->
                ExportSynthese.generer(new ExportSyntheseId(UUID.randomUUID()), CENTRE,
                        "  ", FormatExport.HTML, "{}", MAINTENANT));
    }

    @Test
    void rejeter_format_nul() {
        assertThrows(IllegalArgumentException.class, () ->
                ExportSynthese.generer(new ExportSyntheseId(UUID.randomUUID()), CENTRE,
                        "2026-03", null, "{}", MAINTENANT));
    }

    @Test
    void rejeter_contenu_vide() {
        assertThrows(IllegalArgumentException.class, () ->
                ExportSynthese.generer(new ExportSyntheseId(UUID.randomUUID()), CENTRE,
                        "2026-03", FormatExport.HTML, " ", MAINTENANT));
    }
}
