package com.hemodialyse.backend.contexts.planification.contrat;

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
 * Contrat du roulement, des absences et des remplacements contre les adaptateurs JDBC sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : l'IT.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-p3;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlanningDesInfirmiersJdbcContratTest extends PlanningDesInfirmiersJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        SchemaDuParametrageH2.creer(jdbc);
        SchemaDuParametrageH2.creerLesInfirmiers(jdbc);
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
        poserLesCentres();
    }

    @Override
    protected void poserLesCentres() {
        jdbc.update("INSERT INTO centers (id, code, name) VALUES (?, 'P3-A', 'Centre A')", CENTRE.valeur());
        jdbc.update("INSERT INTO centers (id, code, name) VALUES (?, 'P3-B', 'Centre B')", AUTRE.valeur());
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
