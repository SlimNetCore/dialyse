package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.InstantanesMensuelsJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Instantanés mensuels sur H2 (mode PostgreSQL), table calquée sur la V1 (unicité {@code uq_direction_snapshot}). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:pilotage-direction-instantanes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InstantanesMensuelsJdbcContratTest extends InstantanesMensuelsContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS direction_snapshot (id UUID PRIMARY KEY, societe_id UUID NOT NULL, "
                + "mois VARCHAR(7) NOT NULL, payload TEXT NOT NULL, generated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                + "CONSTRAINT uq_direction_snapshot UNIQUE (societe_id, mois))");
    }

    @AfterEach
    void apres() {
        jdbc.execute("DELETE FROM direction_snapshot");
    }

    @Override
    protected InstantanesMensuels instantanes() {
        return new InstantanesMensuelsJdbc(jdbc);
    }
}
