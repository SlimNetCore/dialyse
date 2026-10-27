package com.hemodialyse.backend.contexts.planification.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des places sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : mêmes tests que le double et que H2, dont le {@code lock_timeout}
 * de la transaction et la course sur la même place. Base JETABLE uniquement : {@code -Dplanification.pg16.url=jdbc:postgresql://…}
 * (script {@code planification-p5-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${planification.pg16.url}",
        "spring.datasource.username=${planification.pg16.user:planification_test}",
        "spring.datasource.password=${planification.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "planification.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlacesJdbcPostgresContractIT extends PlacesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void viderLesLignes() {
        SchemaDuPlacementH2.vider(jdbc, CENTRE.valeur(), AUTRE_CENTRE);
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }
}
