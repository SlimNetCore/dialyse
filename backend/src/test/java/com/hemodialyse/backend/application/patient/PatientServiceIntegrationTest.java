package com.hemodialyse.backend.application.patient;

import com.hemodialyse.backend.domain.patient.PatientRepository;
import com.hemodialyse.backend.domain.patient.PatientType;
import com.hemodialyse.backend.domain.shared.TenantScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class PatientServiceIntegrationTest {

    @Autowired
    PatientService service;

    @Autowired
    PatientRepository repo;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createAndFindPatient() {
        // Ensure centers table exists in test DB (helps when Flyway hasn't run)
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, created_at TIMESTAMP)");

        // Ensure patients table exists for the JPA repository (minimal columns used by the tests)
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, created_at TIMESTAMP, date_admission DATE, date_naissance DATE, nom VARCHAR(255), prenom VARCHAR(255), sexe VARCHAR(10), numero_assurance VARCHAR(100), type_patient VARCHAR(30) NOT NULL)");

        // Ensure attestation_droit table exists for the JPA repository (minimal columns used by the tests)
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS attestation_droit (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, date_debut DATE NOT NULL, date_fin DATE NOT NULL, created_at TIMESTAMP)");

        UUID center = UUID.randomUUID();
        // Insert center so FK constraints pass
        String code = "CTR-" + center.toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO centers (id, code, name, created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", center.toString(), code, "Test Center");

        TenantScope scope = new TenantScope(center, "test-user", java.util.Set.of("AGENT_ADMISSION"));

        LocalDate now = LocalDate.now();
        var p = service.createPatient(
            scope,
            "Doe",
            "John",
            "M",
            now,
            LocalDate.of(1980, 1, 1),
            "ASSUR-123",
            PatientType.NON_VACANCIER,
            now,
            now.plusMonths(2)
        );

        assertThat(p).isNotNull();
        var found = service.findById(scope, p.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getNumeroAssurance()).isEqualTo("ASSUR-123");
        assertThat(found.get().getTypePatient()).isEqualTo(PatientType.NON_VACANCIER);
    }
}
