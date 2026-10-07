package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.infrastructure.scheduling.ObservanceMesure.Mesure;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mesure de l'observance : en quantité de dose quand la prescription porte une dose (une prescription passée de 4000 à
 * 8000 UI alors que 4000 UI ont été administrées laisse 4000 UI), en nombre d'administrations sinon.
 */
class ObservanceMesureTest {

    @Test
    void a_prescription_raised_from_4000_to_8000_ui_leaves_the_missing_4000_ui_to_administer() {
        Mesure m = ObservanceMesure.calculer(1, 8000, "UI", 1, 4000);

        assertTrue(m.enDose());
        assertEquals(8000, m.attendu());
        assertEquals(4000, m.administre());
        assertEquals(4000, m.restant());
        assertEquals(1, m.restantes(), "il reste une seringue de 4000 UI à administrer");
        assertTrue(m.enRetard());
    }

    @Test
    void nothing_is_left_once_the_prescribed_quantity_is_administered_in_one_or_several_injections() {
        Mesure complete = ObservanceMesure.calculer(1, 8000, "UI", 1, 8000);
        Mesure enDeuxFois = ObservanceMesure.calculer(1, 8000, "UI", 2, 8000);

        assertEquals(0, complete.restant());
        assertEquals(0, complete.restantes());
        assertFalse(complete.enRetard());
        assertFalse(enDeuxFois.enRetard());
        assertEquals(2, enDeuxFois.administrations());
    }

    @Test
    void the_expected_quantity_is_the_dose_times_the_frequency_and_never_negative_when_exceeded() {
        Mesure m = ObservanceMesure.calculer(3, 4000, "UI", 3, 12000);
        Mesure trop = ObservanceMesure.calculer(1, 4000, "UI", 2, 8000);

        assertEquals(12000, m.attendu());
        assertEquals(0, m.restant());
        assertEquals(0, trop.restant());
        assertEquals(0, trop.restantes());
    }

    @Test
    void the_remaining_administrations_round_up_to_cover_a_partial_dose() {
        Mesure m = ObservanceMesure.calculer(2, 200, "mg", 1, 250);

        assertEquals(400, m.attendu());
        assertEquals(150, m.restant());
        assertEquals(1, m.restantes());
    }

    @Test
    void without_a_prescribed_dose_administrations_are_counted_as_before() {
        Mesure sansDose = ObservanceMesure.calculer(3, null, "UI", 1, 4000);
        Mesure doseNulle = ObservanceMesure.calculer(3, 0, "UI", 2, 8000);

        assertFalse(sansDose.enDose());
        assertNull(sansDose.unite());
        assertEquals(3, sansDose.attendu());
        assertEquals(2, sansDose.restantes());
        assertEquals(2, sansDose.restant());
        assertEquals(1, doseNulle.restantes());
    }

    @Test
    void iron_is_measured_in_milligrams_and_epo_in_international_units() {
        assertEquals("UI", ObservanceMesure.uniteDe("EPO"));
        assertEquals("mg", ObservanceMesure.uniteDe("FER_INJECTABLE"));
    }

    @Test
    void the_closed_period_message_explains_what_was_prescribed_administered_and_missing() {
        Mesure m = ObservanceMesure.calculer(1, 8000, "UI", 1, 4000);

        String message = ObservancePrescriptionScheduler.messageRetard(TypeTraitementAnemie.EPO, m,
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), 1, "SEMAINE");

        assertTrue(message.contains("Retard constaté"), message);
        assertTrue(message.contains("EPO"), message);
        assertTrue(message.contains("du 28/09/2026 au 04/10/2026"), message);
        assertTrue(message.contains("8000 UI x 1 par semaine, soit 8000 UI attendus"), message);
        assertTrue(message.contains("Administré : 4000 UI (1 administration(s))"), message);
        assertTrue(message.contains("Il manque 4000 UI"), message);
        assertTrue(message.length() < 500, "doit tenir dans la colonne du message : " + message.length());
    }

    @Test
    void the_message_counts_administrations_when_the_prescription_has_no_dose() {
        Mesure m = ObservanceMesure.calculer(3, null, "UI", 1, 0);

        String message = ObservancePrescriptionScheduler.messageRetard(TypeTraitementAnemie.FER_INJECTABLE, m,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 3, "MOIS");

        assertTrue(message.contains("fer injectable"), message);
        assertTrue(message.contains("3 administration(s) par mois"), message);
        assertTrue(message.contains("Administré : 1 administration(s)"), message);
        assertTrue(message.contains("Il manque 2 administration(s)"), message);
    }

    @Test
    void the_reminder_message_says_how_much_is_left_and_before_when() {
        Mesure m = ObservanceMesure.calculer(1, 8000, "UI", 1, 4000);

        String message = ObservancePrescriptionScheduler.messageRappel(TypeTraitementAnemie.EPO, m,
                LocalDate.of(2026, 10, 11), 2);

        assertTrue(message.contains("Il reste à administrer 4000 UI de EPO"), message);
        assertTrue(message.contains("le 11/10/2026 (2 jour(s) restant(s))"), message);
    }
}
