package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du référentiel des infirmiers exécuté contre les adaptateurs JDBC sur H2 (mode PostgreSQL). Variante PostgreSQL 16 :
 * {@code InfirmiersJdbcPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-infirmiers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InfirmiersJdbcContratTest extends InfirmiersJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        SchemaDuParametrageH2.creer(jdbc);
        SchemaDuParametrageH2.creerLesInfirmiers(jdbc);
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
        poserLesCentres();
    }

    /** H2 refuse un verrou de ligne tenu au-delà de son délai propre : l'écriture reçoit 409 (PostgreSQL : lock_timeout, voir l'IT). */
    @Test
    void should_answer_a_concurrent_write_when_the_centre_lock_is_not_released_within_the_delay_of_the_database() throws Exception {
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
            assertThatThrownBy(() -> transaction().executeWithoutResult(statut -> verrou().prendre(CENTRE)))
                    .isInstanceOf(EcritureConcurrente.class);
            liberer.countDown();
            detenteur.get(20, TimeUnit.SECONDS);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Override
    protected void poserLesCentres() {
        jdbc.update("INSERT INTO centers (id, code, name) VALUES (?, 'P2-A', 'Centre A')", CENTRE.valeur());
        jdbc.update("INSERT INTO centers (id, code, name) VALUES (?, 'P2-B', 'Centre B')", AUTRE.valeur());
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
