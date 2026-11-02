package com.hemodialyse.backend.contexts.organisation.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat d'Organisation sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code OrganisationJdbcPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:organisation-contrat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganisationJdbcContratTest extends OrganisationJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @BeforeEach
    void creerLesTablesEtVider() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL, ouverte BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS medecin (id UUID PRIMARY KEY, center_id UUID NOT NULL, nom VARCHAR(255) NOT NULL, "
                + "prenom VARCHAR(255), specialite VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS machine (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(100) NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL, actif BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS protocole (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(100) NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL)");
        vider();
    }
}
