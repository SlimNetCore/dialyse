package com.hemodialyse.backend.domain.patient.model;

import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PatientContactValidationTest {

    @Test
    void shouldNormalizeEmailAndPhonesThroughSetters() {
        Patient patient = Patient.creer(
                CenterId.of(UUID.randomUUID()),
                "Nom",
                "Prenom",
                "M",
                LocalDate.now(),
                LocalDate.of(1990, 1, 1),
                new NumeroAssurance("12345678901234567890"),
                PatientType.NON_VACANCIER
        );

        patient.setEmail(" Test@Example.com ");
        patient.setTelMobile("00 213 555-12-34-56");

        assertEquals("test@example.com", patient.getEmail());
        assertEquals("+213555123456", patient.getTelMobile());
    }

    @Test
    void shouldRejectInvalidEmailInPatientSetter() {
        Patient patient = new Patient();
        assertThrows(IllegalArgumentException.class, () -> patient.setEmail("bad-mail"));
    }
}

