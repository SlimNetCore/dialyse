package com.hemodialyse.backend.contexts.facturation.contrat;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistrements simultanés d'un règlement sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V41) : mêmes tests que
 * {@link ReglementsConcurrentsContrat}. Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
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
class ReglementsConcurrentsPostgresContractIT extends ReglementsConcurrentsContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected PlatformTransactionManager transactionManager() {
        return transactionManager;
    }
}
