package com.hemodialyse.backend.domain.seance.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuppressionSeanceTest {

    private static Seance seance(SeanceStatus statut) {
        Seance s = new Seance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 5));
        s.setStatus(statut);
        return s;
    }

    @Test
    void the_trace_keeps_the_session_its_status_the_reason_and_the_author() {
        Seance s = seance(SeanceStatus.SIGNEE);
        Instant t = Instant.parse("2026-10-06T10:00:00Z");

        SuppressionSeance trace = SuppressionSeance.de(s, new MotifSuppressionSeance("  Doublon de saisie  "), "admin", t);

        assertEquals(s.getId(), trace.seanceId());
        assertEquals(s.getCenterId(), trace.centerId());
        assertEquals(s.getPatientId(), trace.patientId());
        assertEquals(LocalDate.of(2026, 10, 5), trace.dateSeance());
        assertEquals(SeanceStatus.SIGNEE, trace.statut());
        assertEquals("Doublon de saisie", trace.motif().valeur());
        assertEquals("admin", trace.supprimePar());
        assertEquals(t, trace.supprimeLe());
    }

    @Test
    void a_billed_session_cannot_be_deleted() {
        BusinessException e = assertThrows(BusinessException.class, () -> SuppressionSeance.de(
                seance(SeanceStatus.FACTUREE), new MotifSuppressionSeance("Erreur de patient"), "admin", Instant.now()));

        assertEquals("SEANCE_FACTUREE_NON_SUPPRIMABLE", e.getCode());
    }

    @Test
    void the_reason_is_required_and_bounded() {
        assertEquals("SEANCE_SUPPRESSION_MOTIF_INVALIDE",
                assertThrows(BusinessException.class, () -> new MotifSuppressionSeance(null)).getCode());
        assertThrows(BusinessException.class, () -> new MotifSuppressionSeance("  ab  "));
        assertThrows(BusinessException.class, () -> new MotifSuppressionSeance("x".repeat(501)));
        assertEquals(500, new MotifSuppressionSeance("x".repeat(500)).valeur().length());
    }
}
