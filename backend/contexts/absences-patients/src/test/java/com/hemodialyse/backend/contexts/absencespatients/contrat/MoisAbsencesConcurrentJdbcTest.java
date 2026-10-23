package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.MoisAbsencesJdbc;
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
 * Clôture et écriture concurrentes d'un même mois d'absences sur H2 (mode PostgreSQL). Variante PostgreSQL 16 :
 * {@code MoisAbsencesConcurrentPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-contrat-mois-concurrent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MoisAbsencesConcurrentJdbcTest extends MoisAbsencesConcurrentContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void preparer() {
        SchemaDesMoisDAbsencesH2.creer(jdbc);
        SchemaDesMoisDAbsencesH2.vider(jdbc, CENTRE.valeur());
    }

    @Override
    protected MoisAbsencesRepository mois() {
        return new MoisAbsencesJdbc(jdbc);
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    protected boolean creationConcurrenteDeLaLigneSupportee() {
        return false;
    }
}
