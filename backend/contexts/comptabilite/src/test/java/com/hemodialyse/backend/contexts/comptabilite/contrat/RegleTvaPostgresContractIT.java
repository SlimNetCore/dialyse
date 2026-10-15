package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.RegleTvaRepositoryJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des règles de TVA sur un PostgreSQL 16 RÉEL (même suite que {@link RegleTvaContrat}) : l'{@code INSERT} de
 * {@code POST /regles-tva} y réussit (KB-CPT-21 : l'ancien {@code RANDOM_UUID()} échouait). La table {@code tva_types} vient de
 * la migration Flyway V1 ; les lignes de test sont supprimées avant chaque test. Base JETABLE uniquement.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${comptabilite.pg16.url}",
        "spring.datasource.username=${comptabilite.pg16.user:comptabilite_test}",
        "spring.datasource.password=${comptabilite.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "comptabilite.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegleTvaPostgresContractIT extends RegleTvaContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private RegleTvaRepository adaptateur;

    @BeforeEach
    void creerLAdaptateurEtViderLesLignesDeTest() {
        adaptateur = new RegleTvaRepositoryJdbc(jdbc);
        jdbc.update("DELETE FROM tva_types WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected RegleTvaRepository repository() {
        return adaptateur;
    }

    @Override
    protected void regleDesactivee(CentreId centre, String type, String debut) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, actif) "
                + "VALUES (?, ?, 'ancienne', 7.00, ?, FALSE, CAST(? AS DATE), FALSE)", UUID.randomUUID(), centre.valeur(), type, debut);
    }

    @Test
    void should_reject_a_rate_of_1000_or_more_because_of_the_numeric_5_2_column() {
        RegleTva trop = RegleTva.definir(CENTRE_A, "HEMODIALYSE", new BigDecimal("1000"), false, LocalDate.parse("2026-01-01"), null, null);

        assertThatThrownBy(() -> adaptateur.ajouter(trop)).isInstanceOf(DataAccessException.class);
        assertThat(adaptateur.duCentre(CENTRE_A)).isEmpty();
    }

    @Test
    void should_write_the_audit_columns_and_the_label_of_the_shared_table() {
        adaptateur.ajouter(RegleTva.definir(CENTRE_A, "HEMODIALYSE", new BigDecimal("19"), false, LocalDate.parse("2026-01-01"), null, null));

        assertThat(jdbc.queryForObject("SELECT created_by FROM tva_types WHERE center_id = ?", String.class, CENTRE_A.valeur()))
                .isEqualTo("comptabilite");
        assertThat(jdbc.queryForObject("SELECT libelle FROM tva_types WHERE center_id = ?", String.class, CENTRE_A.valeur()))
                .isEqualTo("HEMODIALYSE 19%");
        assertThat(jdbc.queryForObject("SELECT actif FROM tva_types WHERE center_id = ?", Boolean.class, CENTRE_A.valeur())).isTrue();
    }
}
