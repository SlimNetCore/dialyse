package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CahierDialyseReportServiceTest {

    private static Patient patient() {
        Patient p = Patient.creer(CenterId.of(UUID.randomUUID()), "Benali", "Karim", "M", LocalDate.of(2026, 1, 2),
                LocalDate.of(1970, 5, 5), new NumeroAssurance("ASS-1234567"), null);
        p.setCodePatient("PAT-ROU00008");
        p.setGroupeSanguin("A+");
        return p;
    }

    @Test
    void the_identity_line_gathers_the_patient_information_and_skips_the_missing_ones() {
        assertThat(CahierDialyseReportService.ligneIdentite(patient())).isEqualTo(
                "Patient : Karim Benali  ·  Code PAT-ROU00008  ·  Sexe M  ·  Né(e) le 05/05/1970  ·  Groupe A+  ·  N° assurance ASS-1234567");
    }

    @Test
    void the_parameters_carry_the_patient_the_period_and_the_edition_stamp() {
        Patient p = patient();

        Map<String, Object> params = CahierDialyseReportService.params(p, null, LocalDate.of(2026, 9, 30),
                ZoneOffset.UTC, "admin");

        assertThat(params.get("PATIENT_ID")).isEqualTo(p.getId().value().toString());
        assertThat(params.get("DATE_DEBUT")).isEqualTo(Date.valueOf(CahierDialyseReportService.DEBUT_PAR_DEFAUT));
        assertThat(params.get("DATE_FIN")).isEqualTo(Date.valueOf("2026-09-30"));
        assertThat(params.get("EDITE_PAR")).isEqualTo("admin");
        assertThat((String) params.get("EDITE_LE")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");
    }
}
