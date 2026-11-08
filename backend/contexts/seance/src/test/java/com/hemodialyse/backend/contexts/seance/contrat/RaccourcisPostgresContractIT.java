package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.RaccourcisDesCentresJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.transaction.UniteDeTravailSpring;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des raccourcis sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Dseance.pg16.url=jdbc:postgresql://…} (script {@code seance-se3-pg16-rejeu.sh}, mot de passe par la variable d'environnement
 * {@code SEANCE_PG16_PASSWORD}).
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
class RaccourcisPostgresContractIT extends RaccourcisJdbcContrat {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void un_second_remplacement_du_meme_centre_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        String cle = "seance-raccourcis|" + CENTRE_A.valeur();
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, Duration.ofMillis(500));
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle);
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite.executer(() -> {
                    new RaccourcisDesCentresJdbc(jdbc).remplacer(RaccourcisDeConsommables.restituer(CENTRE_A, List.of()));
                    return null;
                })).isInstanceOf(EcritureConcurrente.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        unite.executer(() -> {
            new RaccourcisDesCentresJdbc(jdbc).remplacer(RaccourcisDeConsommables.restituer(CENTRE_A, List.of()));
            return null;
        });
    }

    @Test
    void le_verrou_d_un_autre_centre_n_attend_pas() throws Exception {
        String cle = "seance-raccourcis|" + CENTRE_A.valeur();
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, Duration.ofMillis(500));
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle);
                tient.executeQuery();
                unite.executer(() -> {
                    new RaccourcisDesCentresJdbc(jdbc).remplacer(RaccourcisDeConsommables.restituer(CENTRE_B, List.of()));
                    return null;
                });
            } finally {
                verrou.rollback();
            }
        }
    }

    @BeforeEach
    void avant() {
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
