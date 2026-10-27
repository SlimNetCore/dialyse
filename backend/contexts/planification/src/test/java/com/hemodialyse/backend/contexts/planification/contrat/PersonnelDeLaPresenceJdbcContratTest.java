package com.hemodialyse.backend.contexts.planification.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat du personnel de la présence contre les adaptateurs JDBC sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : l'IT. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-p4;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PersonnelDeLaPresenceJdbcContratTest extends PersonnelDeLaPresenceJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        SchemaDuParametrageH2.creer(jdbc);
        SchemaDuParametrageH2.creerLesInfirmiers(jdbc);
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
        viderPlanningParametres();
        jdbc.update("INSERT INTO centers (id, code, name) VALUES (?, 'P4-A', 'Centre A')", CENTRE.valeur());
        jdbc.update("INSERT INTO centers (id, code, name) VALUES (?, 'P4-B', 'Centre B')", AUTRE.valeur());
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
