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

/** Contrat du journal des suppressions sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code JournalDesSuppressionsPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:seance-journal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JournalDesSuppressionsJdbcContratTest extends JournalDesSuppressionsContrat {
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS seance_suppression (id UUID PRIMARY KEY, center_id UUID NOT NULL, seance_id UUID NOT NULL, "
                + "patient_id UUID NOT NULL, date_seance DATE NOT NULL, statut VARCHAR(20) NOT NULL, motif VARCHAR(500) NOT NULL, "
                + "supprime_par VARCHAR(255) NOT NULL, supprime_le TIMESTAMP WITH TIME ZONE NOT NULL)");
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
