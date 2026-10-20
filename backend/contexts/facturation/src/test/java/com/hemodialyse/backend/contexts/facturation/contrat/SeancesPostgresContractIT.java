package com.hemodialyse.backend.contexts.facturation.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des séances sur un PostgreSQL 16 RÉEL (même suite que {@link SeancesContrat}) : les écritures d'exclusion, de
 * réinclusion et de surcharge de forfait, et les lectures de la simulation, s'exécutent sur le schéma Flyway V1..V39 (dont
 * V39 : {@code statut_avant_exclusion}, {@code exclusion_absence_id}). Base JETABLE uniquement : lancer avec
 * {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${facturation.pg16.url}",
        "spring.datasource.username=${facturation.pg16.user:facturation_test}",
        "spring.datasource.password=${facturation.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "facturation.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SeancesPostgresContractIT extends SeancesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignes() {
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Test
    void should_write_the_author_and_the_instant_of_the_forfait_imposed() {
        decor();
        UUID id = UUID.randomUUID();
        seance(id, CENTRE_A, AMRANI, java.time.LocalDate.of(2026, 8, 2), "VALIDEE", false);

        seances().poserLeForfait(CENTRE_A, id, new ForfaitApplique(PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00")),
                "medecin", Instant.parse("2026-10-20T08:00:00Z"));

        assertThat(jdbc.queryForObject("SELECT forfait_override_updated_by FROM seances WHERE id = ?", String.class, id))
                .isEqualTo("medecin");
        assertThat(jdbc.queryForObject("SELECT forfait_override_updated_at FROM seances WHERE id = ?", Timestamp.class, id)
                .toInstant()).isEqualTo(Instant.parse("2026-10-20T08:00:00Z"));
    }

    @Test
    void should_keep_the_memory_of_the_exclusion_in_the_two_columns_added_by_v39() {
        decor();
        UUID id = UUID.randomUUID();
        UUID absence = UUID.randomUUID();
        seance(id, CENTRE_A, AMRANI, java.time.LocalDate.of(2026, 8, 2), "SIGNEE", false);

        seances().exclure(CENTRE_A, id, StatutDeSeance.SIGNEE);
        seances().lierALAbsence(CENTRE_A, id, absence);

        assertThat(jdbc.queryForObject("SELECT statut || '/' || statut_avant_exclusion FROM seances WHERE id = ?", String.class, id))
                .isEqualTo("ABSENT/SIGNEE");
        assertThat(jdbc.queryForObject("SELECT exclusion_absence_id FROM seances WHERE id = ?", UUID.class, id)).isEqualTo(absence);
    }
}
