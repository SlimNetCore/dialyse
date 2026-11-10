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

/** Contrat de la chaîne des payeurs sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code PayeursPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:patient-couverture-contrat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayeursJdbcContratTest extends PayeursJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        PatientsJdbcContratTest.creerLesTables(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, type_caisse VARCHAR(50) NOT NULL DEFAULT 'STANDARD')");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, center_id UUID NOT NULL, caisse_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, nom VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, nom VARCHAR(255) NOT NULL, adresse VARCHAR(500))");
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
