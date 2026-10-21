package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.MoisDeFacturationJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des mois de facturation sur un PostgreSQL 16 RÉEL (tables {@code periodes_facturation} et
 * {@code periodes_facturation_journal} de la migration V42). Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
 * Propriétés propres à PostgreSQL : unicité (centre, année, mois), contraintes CHECK, fuseau des horodatages, identité du journal.
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
class MoisDeFacturationPostgresContractIT extends MoisDeFacturationContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignesDeTest() {
        SchemaDesMoisDeFacturationH2.vider(jdbc, CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected MoisDeFacturationRepository mois() {
        return new MoisDeFacturationJdbc(jdbc);
    }

    @Test
    void should_have_no_closed_month_after_the_migration_for_the_test_centres() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM periodes_facturation WHERE statut = 'CLOTURE' AND center_id IN (?, ?)",
                Integer.class, CENTRE_A.valeur(), CENTRE_B.valeur())).isZero();
    }

    @Test
    void should_refuse_a_second_line_for_the_same_centre_and_month() {
        mois().verrouiller(CENTRE_A, AOUT);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut) VALUES (?, 2026, 8, 'OUVERT')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void should_refuse_a_month_outside_1_to_12_an_unknown_status_and_an_unknown_act() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut) VALUES (?, 2026, 0, 'OUVERT')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut) VALUES (?, 2026, 8, 'FERME')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO periodes_facturation_journal (center_id, annee, mois, type_acte, effectue_par, "
                + "effectue_le) VALUES (?, 2026, 8, 'EFFACEMENT', ?, CURRENT_TIMESTAMP)", CENTRE_A.valeur(), ADMIN))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void should_return_the_exact_instants_whatever_the_time_zone_of_the_session() {
        var courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.cloturer(LE_1, ADMIN, 1));

        assertThat(mois().duMois(CENTRE_A, AOUT).closLe()).isEqualTo(LE_1);
        assertThat(mois().journal(CENTRE_A, AOUT).getFirst().le()).isEqualTo(LE_1);
    }
}
