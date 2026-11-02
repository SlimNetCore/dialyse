package com.hemodialyse.backend.contexts.organisation.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat d'Organisation sur un PostgreSQL 16 RÉEL dont le schéma est celui de Flyway (V1..V51 : salle.ouverte, machine,
 * protocole). Base JETABLE uniquement : {@code -Dorganisation.pg16.url=jdbc:postgresql://…} (script
 * {@code organisation-pg16-rejeu.sh}). Aucune table n'est créée ici : le schéma est celui de la production.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${organisation.pg16.url}",
        "spring.datasource.username=${organisation.pg16.user:organisation_test}",
        "spring.datasource.password=${organisation.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "organisation.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrganisationJdbcPostgresContractIT extends OrganisationJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @BeforeEach
    void viderLesLignesDeTest() {
        vider();
    }
}
