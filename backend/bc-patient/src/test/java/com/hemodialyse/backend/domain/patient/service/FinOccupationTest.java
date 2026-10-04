package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FinOccupationTest {

    private static final LocalDate EVENEMENT = LocalDate.of(2026, 10, 15);
    private static final LocalDate ADMISSION = LocalDate.of(2026, 1, 2);

    @Test
    void a_permanent_patient_occupies_the_place_without_limit() {
        for (String etat : new String[]{null, "PERMANENT"}) {
            assertTrue(FinOccupation.dernierJourOccupe(etat, EVENEMENT).isEmpty(), etat);
            assertTrue(FinOccupation.occupeLe(etat, EVENEMENT, EVENEMENT.plusYears(5)));
            assertFalse(FinOccupation.placeLiberee(etat, EVENEMENT, EVENEMENT.plusYears(5)));
        }
    }

    @Test
    void a_limited_stay_ends_on_the_event_date_inclusive() {
        for (String etat : new String[]{"OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER"}) {
            assertEquals(EVENEMENT, FinOccupation.dernierJourOccupe(etat, EVENEMENT).orElseThrow(), etat);
            assertTrue(FinOccupation.occupeLe(etat, EVENEMENT, EVENEMENT));
            assertFalse(FinOccupation.occupeLe(etat, EVENEMENT, EVENEMENT.plusDays(1)));
            assertFalse(FinOccupation.placeLiberee(etat, EVENEMENT, EVENEMENT));
            assertTrue(FinOccupation.placeLiberee(etat, EVENEMENT, EVENEMENT.plusDays(1)));
        }
    }

    @Test
    void a_limited_stay_without_end_date_is_not_bounded() {
        assertTrue(FinOccupation.dernierJourOccupe("VACANCIER_LOCAL", null).isEmpty());
        assertTrue(FinOccupation.occupeLe("OCCASIONNEL", null, EVENEMENT.plusYears(1)));
    }

    @Test
    void a_transfer_or_cure_keeps_the_place_through_the_event_date() {
        assertEquals(EVENEMENT, FinOccupation.dernierJourOccupe("TRANSFERE", EVENEMENT).orElseThrow());
        assertTrue(FinOccupation.occupeLe("TRANSFERE", EVENEMENT, EVENEMENT));
        assertFalse(FinOccupation.occupeLe("TRANSFERE", EVENEMENT, EVENEMENT.plusDays(1)));
        assertEquals(EVENEMENT, FinOccupation.dernierJourOccupe("GUERRI", EVENEMENT).orElseThrow());
        assertFalse(FinOccupation.placeLiberee("TRANSFERE", EVENEMENT, EVENEMENT));
        assertTrue(FinOccupation.placeLiberee("TRANSFERE", EVENEMENT, EVENEMENT.plusDays(1)));
    }

    @Test
    void a_death_or_graft_frees_the_place_on_the_event_date() {
        assertEquals(EVENEMENT.minusDays(1), FinOccupation.dernierJourOccupe("DECEDE", EVENEMENT).orElseThrow());
        assertTrue(FinOccupation.occupeLe("DECEDE", EVENEMENT, EVENEMENT.minusDays(1)));
        assertFalse(FinOccupation.occupeLe("DECEDE", EVENEMENT, EVENEMENT));
        assertFalse(FinOccupation.occupeLe("GREFFE", EVENEMENT, EVENEMENT));
        assertTrue(FinOccupation.placeLiberee("DECEDE", EVENEMENT, EVENEMENT));
    }

    @Test
    void an_exit_state_without_date_frees_the_place_immediately() {
        for (String etat : new String[]{"TRANSFERE", "DECEDE", "GREFFE", "GUERRI"}) {
            assertFalse(FinOccupation.occupeLe(etat, null, LocalDate.of(2000, 1, 1)), etat);
            assertTrue(FinOccupation.placeLiberee(etat, null, LocalDate.of(2000, 1, 1)), etat);
        }
    }

    @Test
    void an_exit_state_requires_an_event_date() {
        for (String etat : new String[]{"TRANSFERE", "DECEDE", "GREFFE", "GUERRI"}) {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> FinOccupation.verifierDate(etat, null, ADMISSION), etat);
            assertEquals("PATIENT_DATE_EVENEMENT_REQUISE", e.getCode());
        }
    }

    @Test
    void the_event_date_cannot_precede_the_admission() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> FinOccupation.verifierDate("DECEDE", ADMISSION.minusDays(1), ADMISSION));
        assertEquals("PATIENT_DATE_EVENEMENT_AVANT_ADMISSION", e.getCode());
        assertThrows(BusinessException.class,
                () -> FinOccupation.verifierDate("OCCASIONNEL", ADMISSION.minusDays(1), ADMISSION));
    }

    @Test
    void valid_combinations_are_accepted() {
        assertDoesNotThrow(() -> FinOccupation.verifierDate("PERMANENT", null, ADMISSION));
        assertDoesNotThrow(() -> FinOccupation.verifierDate("OCCASIONNEL", null, ADMISSION));
        assertDoesNotThrow(() -> FinOccupation.verifierDate("TRANSFERE", EVENEMENT, ADMISSION));
        assertDoesNotThrow(() -> FinOccupation.verifierDate("DECEDE", ADMISSION, ADMISSION));
    }
}
