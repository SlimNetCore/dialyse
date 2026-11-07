package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CodeScanne;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResoudreLePatientScanneTest {

    private final Banc banc = new Banc();

    private PatientId resoudre(String qr) {
        return banc.resolution().executer(CENTRE_A, CodeScanne.lire(qr));
    }

    @Test
    void should_find_the_patient_by_identifier() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        assertThat(resoudre(patient.valeur().toString())).isEqualTo(patient);
    }

    @Test
    void should_find_the_patient_by_code_ignoring_case_and_prefix() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        assertThat(resoudre("p-001")).isEqualTo(patient);
        assertThat(resoudre("patient: p-001")).isEqualTo(patient);
    }

    @Test
    void should_find_the_patient_by_insurance_number() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "A1");
        assertThat(resoudre("a1")).isEqualTo(patient);
    }

    @Test
    void should_fall_back_on_the_tolerant_comparison() {
        var patient = banc.patient(CENTRE_A, 1, "P-001", "12.34 56");
        assertThat(resoudre("p 001")).isEqualTo(patient);
        assertThat(resoudre("123456")).isEqualTo(patient);
    }

    @Test
    void should_not_fall_back_on_other_forms_when_an_identifier_is_unknown_in_the_centre() {
        banc.patient(CENTRE_A, 1, "P-001", "A1");
        assertThatThrownBy(() -> resoudre(UUID.randomUUID().toString())).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void should_never_return_a_patient_of_another_centre() {
        banc.patient(CENTRE_B, 2, "P-002", "B2");
        assertThatThrownBy(() -> resoudre("P-002")).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void should_ignore_patients_without_any_reference_in_the_tolerant_comparison() {
        List<PatientsScannables.References> sans = new ArrayList<>();
        sans.add(new PatientsScannables.References(new PatientId(UUID.randomUUID()), null, null, null));
        sans.add(null);
        banc.patients.references.put(CENTRE_A, sans);
        assertThatThrownBy(() -> resoudre("zzz")).isInstanceOf(PatientIntrouvable.class);
    }
}
