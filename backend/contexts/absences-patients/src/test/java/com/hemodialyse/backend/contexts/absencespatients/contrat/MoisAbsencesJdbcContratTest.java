package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.MoisAbsencesJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Contrat des mois d'absences exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL), plus l'unicité (centre, mois) et les
 * contraintes de la table. Variante PostgreSQL 16 : {@code MoisAbsencesPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-contrat-mois;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MoisAbsencesJdbcContratTest extends MoisAbsencesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        SchemaDesMoisDAbsencesH2.creer(jdbc);
        SchemaDesMoisDAbsencesH2.vider(jdbc, CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected MoisAbsencesRepository mois() {
        return new MoisAbsencesJdbc(jdbc);
    }

    @Test
    void should_refuse_a_second_line_for_the_same_centre_and_month() {
        mois().verrouiller(CENTRE_A, AOUT);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut) VALUES (?, 2026, 8, 'OUVERT')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mois_absences WHERE center_id = ?", Integer.class,
                CENTRE_A.valeur())).isEqualTo(1);
    }

    @Test
    void should_refuse_a_month_outside_1_to_12_and_an_unknown_status() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut) VALUES (?, 2026, 13, 'OUVERT')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO mois_absences (center_id, annee, mois, statut) VALUES (?, 2026, 8, 'FERME')",
                CENTRE_A.valeur())).isInstanceOf(DataAccessException.class);
    }
}
