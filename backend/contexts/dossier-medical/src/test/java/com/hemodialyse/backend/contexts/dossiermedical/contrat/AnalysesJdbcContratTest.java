package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat des analyses sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code AnalysesPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-analyses;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AnalysesJdbcContratTest extends AnalysesJdbcContrat {
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        TablesDeLaBiologie.creer(jdbc);
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
