package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat du carnet de transporteurs sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code TransporteursPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-contrat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransporteursJdbcContratTest extends TransporteursJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        PatientsJdbcContratTest.creerLesTables(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS transporteur (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, telephone VARCHAR(50))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS categorie_transport (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL)");
        vider();
    }

    @AfterEach
    void viderApres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
