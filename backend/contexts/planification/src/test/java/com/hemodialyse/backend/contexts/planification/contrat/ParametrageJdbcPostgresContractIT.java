package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du paramétrage sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V44) : mêmes tests que le contrat JDBC, plus le
 * {@code lock_timeout} de la transaction (écriture concurrente 409). Base JETABLE uniquement :
 * {@code -Dplanification.pg16.url=jdbc:postgresql://…} (script {@code planification-p1-pg16-rejeu.sh}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${planification.pg16.url}",
        "spring.datasource.username=${planification.pg16.user:planification_test}",
        "spring.datasource.password=${planification.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "planification.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParametrageJdbcPostgresContractIT extends ParametrageJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void viderLesLignes() {
        SchemaDuParametrageH2.vider(jdbc, CENTRE.valeur(), AUTRE.valeur());
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }

    @Test
    void should_answer_409_when_the_lock_timeout_of_the_transaction_expires() throws Exception {
        parametres().enregistrer(CENTRE, ParametresDuPlanning.parDefaut());
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                try {
                    liberer.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            var unite = new UniteDeTravailSpring(transaction(), jdbc, Duration.ofMillis(300));

            assertThatThrownBy(() -> unite.executer(() -> {
                verrou().prendre(CENTRE);
                return null;
            })).isInstanceOf(EcritureConcurrente.class);

            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
