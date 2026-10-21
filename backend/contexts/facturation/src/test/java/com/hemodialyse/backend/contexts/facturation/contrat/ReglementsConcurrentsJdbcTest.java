package com.hemodialyse.backend.contexts.facturation.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistrements simultanés d'un règlement sur H2 (mode PostgreSQL). Variante PostgreSQL 16 :
 * {@code ReglementsConcurrentsPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-reglements-concurrents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReglementsConcurrentsJdbcTest extends ReglementsConcurrentsContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void creerLesTables() {
        ReglementsJdbcContratTest.creerLesTables(jdbc);
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected PlatformTransactionManager transactionManager() {
        return transactionManager;
    }
}
