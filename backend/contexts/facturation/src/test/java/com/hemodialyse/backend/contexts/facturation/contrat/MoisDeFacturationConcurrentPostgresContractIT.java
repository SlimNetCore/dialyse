package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.EcritureConcurrenteDeFacturation;
import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.MoisDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.transaction.UniteDeTravailSpring;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Clôture et émission concurrentes d'un même mois sur un PostgreSQL 16 RÉEL (tables de la migration V42), y compris la création
 * simultanée de la ligne d'un mois jamais verrouillé ({@code ON CONFLICT DO NOTHING}) et la politique de délai de verrou de
 * l'ADR 0043 (délai dépassé = 409 {@code FACTURATION_ECRITURE_CONCURRENTE} ; une transaction rejointe garde le délai de son
 * propriétaire). Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…}.
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
class MoisDeFacturationConcurrentPostgresContractIT extends MoisDeFacturationConcurrentContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void viderLesLignesDeTest() {
        SchemaDesMoisDeFacturationH2.vider(jdbc, CENTRE.valeur());
    }

    @Override
    protected MoisDeFacturationRepository mois() {
        return new MoisDeFacturationJdbc(jdbc);
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    void should_answer_a_concurrent_write_when_the_month_lock_is_not_released_within_the_lock_timeout() throws Exception {
        ligneDuMoisDejaCreee();
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                mois().verrouiller(CENTRE, YearMonth.of(2026, 8));
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
                new GardeDesMoisClos(mois()).exigerOuvert(CENTRE, LocalDate.of(2026, 8, 14));
                return null;
            })).isInstanceOf(EcritureConcurrenteDeFacturation.class);

            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_set_the_lock_timeout_only_when_it_opens_the_transaction() {
        var unite = new UniteDeTravailSpring(transaction(), jdbc, Duration.ofMillis(300));

        assertThat(unite.executer(() -> jdbc.queryForObject("SHOW lock_timeout", String.class))).isEqualTo("300ms");
        String rejointe = transaction().execute(statut -> {
            jdbc.execute("SET LOCAL lock_timeout = 7000");
            return unite.executer(() -> jdbc.queryForObject("SHOW lock_timeout", String.class));
        });
        assertThat(rejointe).isEqualTo("7s");
    }
}
