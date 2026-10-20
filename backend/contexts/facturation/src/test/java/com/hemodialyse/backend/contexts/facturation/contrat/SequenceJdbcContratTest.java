package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SequenceDeFacturationJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat de la séquence exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL). Variante PostgreSQL 16 :
 * {@code SequencePostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-sequence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SequenceJdbcContratTest extends SequenceContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLaTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_sequence (center_id UUID NOT NULL, seq_year INTEGER NOT NULL, "
                + "seq_value INTEGER NOT NULL)");
        jdbc.update("DELETE FROM facture_sequence");
    }

    @Override
    protected SequenceDeFacturation sequence() {
        return new SequenceDeFacturationJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, int annee, int valeur) {
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, ?)", centre.valeur(), annee, valeur);
    }
}
