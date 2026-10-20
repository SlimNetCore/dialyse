package com.hemodialyse.backend.contexts.facturation.contrat;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des factures sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V40) : mêmes tests que {@link FacturesContrat}, plus la preuve
 * que la migration V40 ajoute la colonne d'auteur, nullable. Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${facturation.pg16.url}",
        "spring.datasource.username=${facturation.pg16.user:facturation_test}",
        "spring.datasource.password=${facturation.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "facturation.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FacturesPostgresContractIT extends FacturesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignes() {
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Test
    void should_have_the_nullable_author_column_added_by_v40() {
        assertThat(jdbc.queryForObject("SELECT data_type || '/' || is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'factures' AND column_name = 'emise_par'", String.class)).isEqualTo("uuid/YES");
    }

    @Test
    void should_keep_the_unique_number_per_centre_constraint_of_the_baseline() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE conname = 'factures_center_id_numero_facture_key'",
                Integer.class)).isEqualTo(1);
    }
}
