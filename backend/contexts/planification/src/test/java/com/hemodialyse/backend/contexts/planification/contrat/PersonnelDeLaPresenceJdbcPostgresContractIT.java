package com.hemodialyse.backend.contexts.planification.contrat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du personnel de la présence sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V47) : mêmes tests que le double et que H2. Base
 * JETABLE uniquement : {@code -Dplanification.pg16.url=jdbc:postgresql://…} (script {@code planification-p4-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${planification.pg16.url}",
        "spring.datasource.username=${planification.pg16.user:planification_test}",
        "spring.datasource.password=${planification.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "planification.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PersonnelDeLaPresenceJdbcPostgresContractIT extends PersonnelDeLaPresenceJdbcContrat {
    private static final UUID SOCIETE = UUID.fromString("99997814-0000-0000-0000-0000000000f4");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderEtPoserLesCentres() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-P4', 'Société P4', TRUE, "
                + "CURRENT_TIMESTAMP)", SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-P4-A', 'Centre A', ?, TRUE)",
                CENTRE.valeur(), SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-P4-B', 'Centre B', ?, TRUE)",
                AUTRE.valeur(), SOCIETE);
    }

    @AfterEach
    void nettoyer() {
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
        viderPlanningParametres();
        jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }
}
