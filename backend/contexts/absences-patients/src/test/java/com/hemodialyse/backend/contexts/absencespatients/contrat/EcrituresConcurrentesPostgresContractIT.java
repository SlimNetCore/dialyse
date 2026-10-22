package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.persistence.AbsencesJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * Contrat de concurrence des écritures d'absences sur un PostgreSQL 16 RÉEL et JETABLE (verrous de ligne, attente du verrou,
 * unicité). Activé par {@code -Dabsences.pg16.url=jdbc:postgresql://…} (script {@code absences-a2-pg16-rejeu.sh}).
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
class EcrituresConcurrentesPostgresContractIT extends EcrituresConcurrentesContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void viderLesLignes() {
        for (var centre : new com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId[]{CENTRE, AUTRE_CENTRE}) {
            jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", centre.valeur());
        }
    }

    @Override
    protected AbsencesRepository absences() {
        return new AbsencesJdbc(jdbc);
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @org.junit.jupiter.api.Test
    void une_ecriture_qui_attend_trop_longtemps_le_verrou_recoit_409_au_lieu_d_attendre_sans_limite() throws Exception {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));
        var gestionnaire = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        var longue = new com.hemodialyse.backend.contexts.absencespatients.infrastructure.transaction.UniteDeTravailSpring(
                new org.springframework.transaction.support.TransactionTemplate(gestionnaire));
        var bornee = new com.hemodialyse.backend.contexts.absencespatients.infrastructure.transaction.UniteDeTravailSpring(
                new org.springframework.transaction.support.TransactionTemplate(gestionnaire), jdbc, java.time.Duration.ofSeconds(1));
        var verrouillee = new java.util.concurrent.CountDownLatch(1);
        var liberation = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(1);
        try {
            var premiere = pool.submit(() -> longue.executer(() -> {
                absences().verrouiller(CENTRE, id(1));
                verrouillee.countDown();
                try {
                    liberation.await(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            org.assertj.core.api.Assertions.assertThat(verrouillee.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long debut = System.nanoTime();

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> bornee.executer(() -> absences().verrouiller(CENTRE, id(1))))
                    .isInstanceOf(com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente.class);

            org.assertj.core.api.Assertions.assertThat(java.time.Duration.ofNanos(System.nanoTime() - debut)).isLessThan(java.time.Duration.ofSeconds(10));
            liberation.countDown();
            premiere.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            liberation.countDown();
            pool.shutdownNow();
        }
    }
}
