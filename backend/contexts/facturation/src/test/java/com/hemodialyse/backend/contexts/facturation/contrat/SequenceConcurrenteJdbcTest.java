package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SequenceDeFacturationJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Réservation concurrente de la séquence sur H2 (mode PostgreSQL). Variante PostgreSQL 16 :
 * {@code SequenceConcurrentePostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-sequence-concurrente;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SequenceConcurrenteJdbcTest extends SequenceConcurrenteContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void creerLaTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_sequence (center_id UUID NOT NULL, seq_year INTEGER NOT NULL, "
                + "seq_value INTEGER NOT NULL, PRIMARY KEY (center_id, seq_year))");
        jdbc.update("DELETE FROM facture_sequence");
    }

    @Override
    protected SequenceDeFacturation sequence() {
        return new SequenceDeFacturationJdbc(jdbc);
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    protected boolean creationConcurrenteDeLaLigneSupportee() {
        return false;
    }

    @Override
    protected void poser(CentreId centre, int annee, int valeur) {
        jdbc.update("INSERT INTO facture_sequence (center_id, seq_year, seq_value) VALUES (?, ?, ?)", centre.valeur(), annee, valeur);
    }
}
