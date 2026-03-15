package com.hemodialyse.backend.application.pec;

import com.hemodialyse.backend.domain.shared.TenantScope;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PecServiceIntegrationTest {

    @Autowired
    private PecService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAllowSessionOnlyWhenPecValidated() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, created_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, created_at TIMESTAMP, date_admission DATE, date_naissance DATE, nom VARCHAR(255), prenom VARCHAR(255), sexe VARCHAR(10), numero_assurance VARCHAR(100), type_patient VARCHAR(30) NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS prise_en_charge (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, date_debut_demande DATE, date_fin_demande DATE, statut VARCHAR(20) NOT NULL, created_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS attestation_droit (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, date_debut DATE NOT NULL, date_fin DATE NOT NULL, created_at TIMESTAMP)");

        UUID centerId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO centers (id, code, name, created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", centerId, "CTR-TEST", "Centre Test");
        jdbcTemplate.update(
            "INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            patientId, centerId, "Doe", "Jane", "F", LocalDate.now(), "ASSUR-PEC-01", "NON_VACANCIER"
        );
        jdbcTemplate.update(
            "INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
            UUID.randomUUID(), patientId, centerId, LocalDate.now().minusDays(1), LocalDate.now().plusMonths(1)
        );

        TenantScope scope = new TenantScope(centerId, "agent-assurance", Set.of("AGENT_ASSURANCE"));
        var pec = service.create(scope, patientId, LocalDate.now(), LocalDate.now().plusMonths(3));

        assertThat(service.canCreateSession(scope, pec.getId())).isFalse();

        service.validate(scope, pec.getId());
        assertThat(service.canCreateSession(scope, pec.getId())).isTrue();

        service.close(scope, pec.getId());
        assertThat(service.canCreateSession(scope, pec.getId())).isFalse();
    }
}
