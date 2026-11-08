package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VerrousDesRappelsJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verrou du rappel sur un PostgreSQL 16 RÉEL (KB-SEA-35, multi-instances). Base JETABLE uniquement :
 * {@code -Dseance.pg16.url=jdbc:postgresql://…} (script {@code seance-se4-pg16-rejeu.sh}, mot de passe par la variable d'environnement
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
class VerrousDesRappelsPostgresContractIT {

    private static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    private static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;

    private UniteDeTravailSpring unite() {
        return new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, Duration.ofMillis(500));
    }

    private static String cle(CentreId centre, LocalDate jour) {
        return "rappel-regularisation|" + centre.valeur() + "|" + jour;
    }

    @Test
    void un_second_rappel_du_meme_centre_et_du_meme_jour_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        var verrous = new VerrousDesRappelsJdbc(jdbc);
        try (Connection autreInstance = dataSource.getConnection()) {
            autreInstance.setAutoCommit(false);
            try (PreparedStatement tient = autreInstance.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle(CENTRE_A, JOUR));
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> {
                    verrous.verrouiller(CENTRE_A, JOUR);
                    return null;
                })).isInstanceOf(EcritureConcurrente.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                autreInstance.rollback();
            }
        }
        unite().executer(() -> {
            verrous.verrouiller(CENTRE_A, JOUR);
            return null;
        });
    }

    @Test
    void le_verrou_d_un_autre_centre_ou_d_un_autre_jour_n_attend_pas() throws Exception {
        var verrous = new VerrousDesRappelsJdbc(jdbc);
        try (Connection autreInstance = dataSource.getConnection()) {
            autreInstance.setAutoCommit(false);
            try (PreparedStatement tient = autreInstance.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle(CENTRE_A, JOUR));
                tient.executeQuery();
                unite().executer(() -> {
                    verrous.verrouiller(CENTRE_B, JOUR);
                    verrous.verrouiller(CENTRE_A, JOUR.plusDays(1));
                    return null;
                });
            } finally {
                autreInstance.rollback();
            }
        }
    }

    @Test
    void le_verrou_est_libere_a_la_fin_de_l_unite_de_travail_meme_en_cas_d_echec() {
        var verrous = new VerrousDesRappelsJdbc(jdbc);
        assertThatThrownBy(() -> unite().executer(() -> {
            verrous.verrouiller(CENTRE_A, JOUR);
            throw new IllegalStateException("échec après le verrou");
        })).isInstanceOf(IllegalStateException.class);

        unite().executer(() -> {
            verrous.verrouiller(CENTRE_A, JOUR);
            return null;
        });
    }
}
