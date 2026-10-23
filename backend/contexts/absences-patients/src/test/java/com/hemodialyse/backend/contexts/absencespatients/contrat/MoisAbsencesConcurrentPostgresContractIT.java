package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.MoisAbsencesJdbc;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.transaction.UniteDeTravailSpring;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Clôture et écriture concurrentes d'un même mois d'absences sur un PostgreSQL 16 RÉEL (tables de la migration V44), y compris la
 * création simultanée de la ligne d'un mois jamais verrouillé ({@code ON CONFLICT DO NOTHING}) et le {@code lock_timeout} de la
 * transaction (écriture concurrente 409, comme A2). Base JETABLE uniquement : {@code -Dabsences.pg16.url=jdbc:postgresql://…}.
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
class MoisAbsencesConcurrentPostgresContractIT extends MoisAbsencesConcurrentContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void viderLesLignesDeTest() {
        SchemaDesMoisDAbsencesH2.vider(jdbc, CENTRE.valeur());
    }

    @Override
    protected MoisAbsencesRepository mois() {
        return new MoisAbsencesJdbc(jdbc);
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
                mois().verrouiller(CENTRE, AOUT);
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
                garde().exigerOuvert(CENTRE, LocalDate.of(2026, 8, 14));
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
