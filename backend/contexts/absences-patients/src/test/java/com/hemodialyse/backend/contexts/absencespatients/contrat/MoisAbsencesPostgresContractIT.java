package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.MoisAbsencesJdbc;
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
 * Contrat des mois d'absences sur un PostgreSQL 16 RÉEL (tables {@code mois_absences} et {@code mois_absences_journal} de la
 * migration V44). Base JETABLE uniquement : {@code -Dabsences.pg16.url=jdbc:postgresql://…} (script {@code absences-a6-pg16-rejeu.sh}).
 * Propriétés propres à PostgreSQL : unicité (centre, année, mois), contraintes CHECK, fuseau des horodatages, identité du journal.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${absences.pg16.url}",
        "spring.datasource.username=${absences.pg16.user:absences_test}",
        "spring.datasource.password=${absences.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "absences.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MoisAbsencesPostgresContractIT extends MoisAbsencesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void viderLesLignesDeTest() {
        SchemaDesMoisDAbsencesH2.vider(jdbc, CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected MoisAbsencesRepository mois() {
        return new MoisAbsencesJdbc(jdbc);
    }

    @Test
    void should_have_no_closed_month_after_the_migration_for_the_test_centres() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mois_absences WHERE statut = 'CLOTURE' AND center_id IN (?, ?)",
                Integer.class, CENTRE_A.valeur(), CENTRE_B.valeur())).isZero();
    }

    @Test
    void should_refuse_a_second_line_for_the_same_centre_and_month() {
        mois().verrouiller(CENTRE_A, AOUT);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut) VALUES (?, 2026, 8, 'OUVERT')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void should_refuse_a_month_outside_1_to_12_an_unknown_status_and_an_unknown_act() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut) VALUES (?, 2026, 0, 'OUVERT')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut) VALUES (?, 2026, 8, 'FERME')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences_journal (center_id, annee, mois, type_acte, effectue_par, "
                + "effectue_le) VALUES (?, 2026, 8, 'EFFACEMENT', ?, CURRENT_TIMESTAMP)", CENTRE_A.valeur(), ADMIN.valeur()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void should_return_the_exact_instants_whatever_the_time_zone_of_the_session() {
        var courant = mois().verrouiller(CENTRE_A, AOUT);
        mois().enregistrer(courant, courant.cloturer(LE_1, ADMIN, 1, 0));

        assertThat(mois().duMois(CENTRE_A, AOUT).closLe()).isEqualTo(LE_1);
        assertThat(mois().journal(CENTRE_A, AOUT).getFirst().le()).isEqualTo(LE_1);
    }
}
