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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du roulement, des absences et des remplacements sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V47, dont la colonne
 * {@code declaree_par}) : mêmes tests que le contrat JDBC, dont les COURSES (verrou de la fiche de l'infirmier, contrainte d'unicité des
 * remplacements). Base JETABLE uniquement : {@code -Dplanification.pg16.url=jdbc:postgresql://…} (script {@code planification-p3-pg16-rejeu.sh}).
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
class PlanningDesInfirmiersJdbcPostgresContractIT extends PlanningDesInfirmiersJdbcContrat {

    private static final UUID SOCIETE = UUID.fromString("99997814-0000-0000-0000-0000000000f1");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void viderEtPoserLesCentres() {
        nettoyer();
        poserLesCentres();
    }

    @AfterEach
    void nettoyer() {
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
        jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
    }

    @Override
    protected void poserLesCentres() {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-P3', 'Société P3', TRUE, "
                + "CURRENT_TIMESTAMP)", SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-P3-A', 'Centre A', ?, TRUE)",
                CENTRE.valeur(), SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-P3-B', 'Centre B', ?, TRUE)",
                AUTRE.valeur(), SOCIETE);
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void la_base_est_bien_postgresql_16_avec_la_colonne_de_l_auteur_des_absences() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'infirmier_absence' "
                + "AND column_name = 'declaree_par'", Integer.class)).isEqualTo(1);
    }
}
