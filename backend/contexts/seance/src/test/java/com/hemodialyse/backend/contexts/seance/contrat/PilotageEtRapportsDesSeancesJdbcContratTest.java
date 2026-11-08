package com.hemodialyse.backend.contexts.seance.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat SE7e (ADR 0099) sur H2 en mode PostgreSQL (celui du filet de la plateforme) : libellés en MAJUSCULES (KB-SEA-86), moyennes à 12
 * décimales (KB-SEA-87). Variante PostgreSQL 16 : {@code PilotageEtRapportsDesSeancesPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:seance-se7e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PilotageEtRapportsDesSeancesJdbcContratTest extends PilotageEtRapportsDesSeancesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        preparer();
    }

    @AfterEach
    void apres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected boolean libellesEnMajuscules() {
        return true;
    }

    @Override
    protected int decimalesDesMoyennes() {
        return 12;
    }
}
