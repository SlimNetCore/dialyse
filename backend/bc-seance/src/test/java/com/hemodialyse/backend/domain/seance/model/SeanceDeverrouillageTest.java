package com.hemodialyse.backend.domain.seance.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeanceDeverrouillageTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 5);

    private Seance seance(LocalDate date) {
        return new Seance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), date);
    }

    @Test
    void an_unvalidated_past_day_session_can_be_unlocked_for_regularisation() {
        Seance s = seance(AUJOURDHUI.minusDays(2));
        assertFalse(s.estDeverrouilleePourRegularisation());

        s.deverrouillerPourRegularisation("admin", AUJOURDHUI);

        assertTrue(s.estDeverrouilleePourRegularisation());
        assertEquals("admin", s.getRegularisationDeverrouilleeBy());
    }

    @Test
    void unlocking_twice_keeps_the_first_author_and_date() {
        Seance s = seance(AUJOURDHUI.minusDays(2));
        s.deverrouillerPourRegularisation("admin-1", AUJOURDHUI);
        var premiereDate = s.getRegularisationDeverrouilleeAt();

        s.deverrouillerPourRegularisation("admin-2", AUJOURDHUI);

        assertEquals("admin-1", s.getRegularisationDeverrouilleeBy());
        assertEquals(premiereDate, s.getRegularisationDeverrouilleeAt());
    }

    @Test
    void todays_session_is_never_unlocked_because_the_nurse_validates_it_freely() {
        for (LocalDate date : new LocalDate[]{AUJOURDHUI, AUJOURDHUI.plusDays(1)}) {
            Seance s = seance(date);
            var e = assertThrows(BusinessException.class, () -> s.deverrouillerPourRegularisation("admin", AUJOURDHUI));
            assertEquals("SEANCE_DEVERROUILLAGE_INVALIDE", e.getCode());
            assertFalse(s.estDeverrouilleePourRegularisation());
        }
    }

    @Test
    void only_a_session_still_created_can_be_unlocked() {
        Seance validee = seance(AUJOURDHUI.minusDays(2));
        validee.validerParInfirmier("inf");

        var e = assertThrows(BusinessException.class, () -> validee.deverrouillerPourRegularisation("admin", AUJOURDHUI));

        assertEquals("SEANCE_DEVERROUILLAGE_INVALIDE", e.getCode());
    }

    @Test
    void a_session_without_date_cannot_be_unlocked() {
        Seance s = seance(null);
        assertThrows(BusinessException.class, () -> s.deverrouillerPourRegularisation("admin", AUJOURDHUI));
    }
}
