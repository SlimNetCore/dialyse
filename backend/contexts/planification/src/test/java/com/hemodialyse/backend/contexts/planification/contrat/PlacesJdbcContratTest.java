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

/** Contrat des places exécuté contre les adapters JDBC sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code PlacesJdbcPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-places;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlacesJdbcContratTest extends PlacesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        SchemaDuPlacementH2.creer(jdbc);
        SchemaDuPlacementH2.vider(jdbc, CENTRE.valeur(), AUTRE_CENTRE);
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
