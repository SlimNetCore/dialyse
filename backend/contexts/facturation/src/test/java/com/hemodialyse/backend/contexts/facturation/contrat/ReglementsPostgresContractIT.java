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
 * Contrat des règlements sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V41) : mêmes tests que {@link ReglementsContrat}, plus la
 * preuve que la migration V41 ajoute les trois colonnes nullables et l'index unique de la clé d'idempotence, et que la montant
 * garde la précision {@code numeric(14,2)} (KB-FAC-44). Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
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
class ReglementsPostgresContractIT extends ReglementsJdbcContrat {

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
    void should_have_the_three_nullable_columns_added_by_v41() {
        assertThat(jdbc.queryForList("SELECT column_name || '/' || data_type || '/' || character_maximum_length || '/' || is_nullable "
                + "FROM information_schema.columns WHERE table_name = 'facture_reglements' AND column_name IN "
                + "('mode_reglement', 'reference_paiement', 'cle_idempotence') ORDER BY column_name", String.class))
                .containsExactly("cle_idempotence/character varying/120/YES", "mode_reglement/character varying/20/YES",
                        "reference_paiement/character varying/120/YES");
    }

    @Test
    void should_have_the_unique_index_of_the_idempotency_key_per_centre_and_invoice() {
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_facture_reglements_cle_idempotence'",
                String.class)).contains("UNIQUE").contains("(center_id, facture_id, cle_idempotence)");
    }

    @Test
    void should_refuse_an_amount_beyond_the_numeric_14_2_precision() {
        poserLesFactures();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reglements()
                        .enregistrer(reglement(F1, "1000000000000.00", "2026-02-10", null, null, null)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
