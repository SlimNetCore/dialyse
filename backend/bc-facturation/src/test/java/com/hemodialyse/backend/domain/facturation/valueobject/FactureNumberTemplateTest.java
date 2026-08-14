package com.hemodialyse.backend.domain.facturation.valueobject;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactureNumberTemplateTest {

    private static final CenterId CENTER_ID = CenterId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    @Test
    void format_should_support_legacy_year_and_sequence_tokens() {
        FactureNumberTemplate template = new FactureNumberTemplate("FACT-{YYYY}-{SEQ6}");

        String invoiceNumber = template.format(CENTER_ID, LocalDate.of(2026, 8, 14), 12);

        assertEquals("FACT-2026-000012", invoiceNumber);
    }

    @Test
    void format_should_keep_default_sequence_width_for_current_token() {
        FactureNumberTemplate template = new FactureNumberTemplate("FAC-{YEAR}-{SEQ}-{CENTER}");

        String invoiceNumber = template.format(CENTER_ID, LocalDate.of(2026, 8, 14), 7);

        assertEquals("FAC-2026-0007-11111111", invoiceNumber);
    }
}
