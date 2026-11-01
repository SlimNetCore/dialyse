package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeanceTest {
    @Test
    void should_create_in_cree_state() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        assertEquals(StatutSeance.CREE, s.statut());
    }

    @Test
    void should_reject_valider_if_not_cree() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        s.valider();
        assertThrows(IllegalStateException.class, s::valider);
    }

    @Test
    void should_cycle_cree_validee_signee() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        s.valider();
        assertEquals(StatutSeance.VALIDEE, s.statut());
        s.signer();
        assertEquals(StatutSeance.SIGNEE, s.statut());
    }

    @Test
    void should_reject_signer_if_not_validee() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        assertThrows(IllegalStateException.class, s::signer);
    }

    @Test
    void should_store_forfait_surcharge_when_not_facturee() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        ForfaitApplique f = new ForfaitApplique(UUID.randomUUID(), "HD", "Forfait HD", new BigDecimal("100"),
                "admin", java.time.OffsetDateTime.now());
        s.declarerForfaitSurcharge(f, "admin", java.time.OffsetDateTime.now());
        assertEquals(f.id(), s.forfaitSurcharge().id());
    }

    @Test
    void should_refuse_forfait_when_facturee() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        s.verrouillerFacturation();
        ForfaitApplique f = new ForfaitApplique(UUID.randomUUID(), "HD", "Forfait HD", new BigDecimal("100"),
                "admin", java.time.OffsetDateTime.now());
        assertThrows(IllegalStateException.class, () -> s.declarerForfaitSurcharge(f, "admin", java.time.OffsetDateTime.now()));
    }

    @Test
    void should_keep_first_author_on_resurcharge() {
        Seance s = Seance.creer(new SeanceId(UUID.randomUUID()), new CentreId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new PecId(UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), "HD", java.time.OffsetDateTime.now());
        ForfaitApplique f1 = new ForfaitApplique(UUID.randomUUID(), "HD", "Forfait HD", new BigDecimal("100"),
                "admin", java.time.OffsetDateTime.now());
        s.declarerForfaitSurcharge(f1, "admin", java.time.OffsetDateTime.now());
        ForfaitApplique f2 = new ForfaitApplique(UUID.randomUUID(), "PREM", "Premium", new BigDecimal("200"),
                "medecin", java.time.OffsetDateTime.now());
        s.declarerForfaitSurcharge(f2, "medecin", java.time.OffsetDateTime.now());
        assertEquals("admin", s.forfaitSurcharge().auteur());
    }
}