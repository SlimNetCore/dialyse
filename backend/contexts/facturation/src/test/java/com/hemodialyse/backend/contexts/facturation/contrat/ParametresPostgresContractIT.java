package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ParametresDeFacturationJdbc;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des paramètres de facturation sur un PostgreSQL 16 RÉEL (même suite que {@link ParametresDeFacturationContrat}) :
 * création et remplacement y réussissent (KB-FAC-07 : l'ancien {@code MERGE INTO … KEY} était refusé). La table vient de la
 * migration Flyway V1 ; les lignes de test sont supprimées avant chaque test. Base JETABLE uniquement : lancer avec
 * {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
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
class ParametresPostgresContractIT extends ParametresDeFacturationContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private ParametresDeFacturationRepository adaptateur;

    @BeforeEach
    void creerLAdaptateurEtViderLesLignesDeTest() {
        adaptateur = new ParametresDeFacturationJdbc(jdbc);
        jdbc.update("DELETE FROM facturation_settings WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected ParametresDeFacturationRepository repository() {
        return adaptateur;
    }

    @Test
    void should_write_the_author_and_leave_the_legacy_vat_rate_column_alone() {
        adaptateur.enregistrer(CENTRE_A, ParametresDeFacturation.saisis("FAC-{SEQ}", true,
                Instant.parse("2026-10-19T10:00:00Z")), "admin");

        assertThat(jdbc.queryForObject("SELECT updated_by FROM facturation_settings WHERE center_id = ?", String.class,
                CENTRE_A.valeur())).isEqualTo("admin");
        assertThat(jdbc.queryForObject("SELECT tva_rate FROM facturation_settings WHERE center_id = ?", java.math.BigDecimal.class,
                CENTRE_A.valeur())).isEqualByComparingTo("0");
    }
}
