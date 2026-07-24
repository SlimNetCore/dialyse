package com.hemodialyse.backend.application.query;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientSummaryQueryServiceTest {

    private static Map<String, Object> row(String sexe, LocalDate dateNaissance, Boolean sousKt,
                                           String etatPatient, LocalDate dateEvenementEtat) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sexe", sexe);
        row.put("date_naissance", dateNaissance);
        row.put("sous_kt", sousKt);
        row.put("etat_patient", etatPatient);
        row.put("date_evenement_etat", dateEvenementEtat);
        return row;
    }

    @Test
    void getSummary_should_group_patients_by_sex_age_and_kt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID centerId = UUID.randomUUID();
        YearMonth referenceMonth = YearMonth.of(2026, 6);
        LocalDate referenceDate = referenceMonth.atEndOfMonth();

        when(jdbc.queryForList(anyString(), eq(centerId))).thenReturn(List.of(
                row("M", referenceDate.minusYears(25), true, "PERMANENT", null),
                row("F", referenceDate.minusYears(61), false, "GREFFE", LocalDate.of(2026, 6, 15)),
                row(null, null, true, "VACANCIER_LOCAL", LocalDate.of(2026, 5, 20)),
                row("X", referenceDate.minusYears(12), false, "OCCASIONNEL", LocalDate.of(2026, 6, 2))
        ));

        PatientSummaryQueryService service = new PatientSummaryQueryService(jdbc);
        PatientSummaryQueryService.PatientSummaryResponse summary = service.getSummary(centerId, referenceMonth);

        assertEquals(3, summary.totalPatients());
        assertEquals(List.of(
                new PatientSummaryQueryService.SummaryBucket("M", "Masculin", 1),
                new PatientSummaryQueryService.SummaryBucket("F", "Féminin", 1),
                new PatientSummaryQueryService.SummaryBucket("AUTRE", "Autre / inconnu", 1)
        ), summary.sexDistribution());
        assertEquals(List.of(
                new PatientSummaryQueryService.SummaryBucket("0_17", "0-17 ans", 1),
                new PatientSummaryQueryService.SummaryBucket("18_29", "18-29 ans", 1),
                new PatientSummaryQueryService.SummaryBucket("30_44", "30-44 ans", 0),
                new PatientSummaryQueryService.SummaryBucket("45_59", "45-59 ans", 0),
                new PatientSummaryQueryService.SummaryBucket("60_PLUS", "60 ans et +", 1),
                new PatientSummaryQueryService.SummaryBucket("INCONNU", "Non renseigné", 1)
        ), summary.ageDistribution());
        assertEquals(List.of(
                new PatientSummaryQueryService.SummaryBucket("OUI", "Sous KT", 1),
                new PatientSummaryQueryService.SummaryBucket("NON", "Sans KT", 2)
        ), summary.ktDistribution());
    }
}



