package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.CalendrierDuCentreJdbc;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du calendrier du centre sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Dseance.pg16.url=jdbc:postgresql://…} (script {@code seance-se7a-pg16-rejeu.sh}, mot de passe par la variable d'environnement
 * {@code SEANCE_PG16_PASSWORD}). Prouve en plus que le doublon est refusé par la contrainte d'unicité RÉELLE et que le libellé est borné à
 * 255 caractères par la colonne réelle.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${seance.pg16.url}",
        "spring.datasource.username=${seance.pg16.user:seance_test}",
        "spring.datasource.password=${seance.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "seance.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CalendrierDuCentrePostgresContractIT extends CalendrierDuCentreContrat {
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM center_holiday WHERE center_id IN (?, ?)", A.valeur(), B.valeur());
        jdbc.update("DELETE FROM center_closure_day WHERE center_id IN (?, ?)", A.valeur(), B.valeur());
    }

    @Test
    void should_report_a_duplicate_of_the_real_unique_constraint_as_a_day_already_in_the_calendar() {
        var c = calendrier();
        c.ajouter(new JourDuCalendrier(new UUID(3, 1), A, LocalDate.of(2026, 1, 10), NatureDeJour.FERMETURE_EXCEPTIONNELLE, "Premier"));

        assertThatThrownBy(() -> c.ajouter(new JourDuCalendrier(new UUID(3, 2), A, LocalDate.of(2026, 1, 10),
                NatureDeJour.FERMETURE_EXCEPTIONNELLE, "Second"))).isInstanceOf(JourDejaAuCalendrier.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM center_closure_day WHERE center_id = ?", Integer.class, A.valeur())).isEqualTo(1);
    }

    @Override
    protected CalendrierDuCentre calendrier() {
        return new CalendrierDuCentreJdbc(jdbc);
    }
}
