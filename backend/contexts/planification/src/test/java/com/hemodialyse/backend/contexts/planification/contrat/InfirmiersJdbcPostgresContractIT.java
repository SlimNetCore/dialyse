package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.infrastructure.transaction.UniteDeTravailSpring;
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

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du référentiel des infirmiers sur un PostgreSQL 16 RÉEL (schéma Flyway V1..V46) : mêmes tests que le contrat JDBC, plus
 * le {@code lock_timeout} de la transaction, le verrou {@code FOR NO KEY UPDATE} du centre qui n'empêche pas d'insérer une fiche
 * (clé étrangère vers le centre) et les contraintes réelles. Base JETABLE uniquement : {@code -Dplanification.pg16.url=jdbc:postgresql://…}
 * (script {@code planification-p2-pg16-rejeu.sh}).
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
class InfirmiersJdbcPostgresContractIT extends InfirmiersJdbcContrat {

    private static final UUID SOCIETE = UUID.fromString("99997813-0000-0000-0000-0000000000f1");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void viderEtPoserLesCentres() {
        nettoyer();
        poserLesCentres();
    }

    @AfterEach
    void nettoyer() {
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
        jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
    }

    @Override
    protected void poserLesCentres() {
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?, 'ZT-P2', 'Société P2', TRUE, "
                + "CURRENT_TIMESTAMP)", SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-P2-A', 'Centre A', ?, TRUE)",
                CENTRE.valeur(), SOCIETE);
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?, 'ZT-P2-B', 'Centre B', ?, TRUE)",
                AUTRE.valeur(), SOCIETE);
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
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                attendre(liberer);
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

    /** Le verrou du centre est un FOR NO KEY UPDATE : une fiche (clé étrangère vers le centre) s'insère pendant qu'il est tenu. */
    @Test
    void should_let_a_row_referencing_the_centre_be_inserted_while_the_centre_lock_is_held() throws Exception {
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<?> detenteur = pool.submit(() -> transaction().execute(statut -> {
                verrou().prendre(CENTRE);
                verrouille.countDown();
                attendre(liberer);
                return null;
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            var unite = new UniteDeTravailSpring(transaction(), jdbc, Duration.ofMillis(500));
            unite.executer(() -> {
                infirmiers().enregistrer(fiche(CENTRE, "M-FK", "Amrani", "Sara"));
                return null;
            });
            assertThat(infirmiers().parMatricule(CENTRE, com.hemodialyse.backend.contexts.planification.domain.Matricule
                    .depuisLaSaisie("M-FK"))).isPresent();
            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
