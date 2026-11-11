package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients.CarteDuPatient;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.application.erreur.BusinessException;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CahierDialyseReportServiceTest {

    private static CarteDuPatient carte() {
        return new CarteDuPatient("PAT-ROU00008", "Benali", "Karim", "M", LocalDate.of(1970, 5, 5), "A+", "ASS-1234567", null);
    }

    @Test
    void the_identity_line_gathers_the_patient_information_and_skips_the_missing_ones() {
        assertThat(CahierDialyseReportService.ligneIdentite(carte())).isEqualTo(
                "Patient : Karim Benali  ·  Code PAT-ROU00008  ·  Sexe M  ·  Né(e) le 05/05/1970  ·  Groupe A+  ·  N° assurance ASS-1234567");
    }

    @Test
    void the_identity_line_prints_the_mobile_phone_when_known() {
        CarteDuPatient c = new CarteDuPatient(null, "Benali", null, null, null, null, null, " 0555 12 34 56 ");

        assertThat(CahierDialyseReportService.ligneIdentite(c)).isEqualTo("Patient : Benali  ·  Tél 0555 12 34 56");
    }

    @Test
    void the_parameters_carry_the_patient_the_period_and_the_edition_stamp() {
        UUID patientId = UUID.randomUUID();

        Map<String, Object> params = CahierDialyseReportService.params(patientId, carte(), null, LocalDate.of(2026, 9, 30),
                ZoneOffset.UTC, "admin");

        assertThat(params.get("PATIENT_ID")).isEqualTo(patientId.toString());
        assertThat(params.get("DATE_DEBUT")).isEqualTo(Date.valueOf(CahierDialyseReportService.DEBUT_PAR_DEFAUT));
        assertThat(params.get("DATE_FIN")).isEqualTo(Date.valueOf("2026-09-30"));
        assertThat(params.get("EDITE_PAR")).isEqualTo("admin");
        assertThat((String) params.get("EDITE_LE")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");
    }

    @Test
    void a_patient_unknown_to_the_centre_is_refused_before_anything_is_counted_or_printed() {
        FichesDesPatients fiches = mock(FichesDesPatients.class);
        when(fiches.carte(any(), any())).thenReturn(Optional.empty());
        CahierDialyseReportService service = new CahierDialyseReportService(mock(ModeleDocumentPrinter.class), fiches,
                mock(SeancesDuCahierPubliees.class));

        assertThatThrownBy(() -> service.imprimer(UUID.randomUUID(), UUID.randomUUID(), null, null, ZoneOffset.UTC, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("Patient introuvable");
    }
}
