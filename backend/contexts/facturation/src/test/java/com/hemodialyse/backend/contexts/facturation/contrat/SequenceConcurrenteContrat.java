package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Atomicité de la réservation de la séquence entre transactions concurrentes (KB-FAC-24), sur la vraie base (H2 et PostgreSQL 16).
 * Le test est déterministe : la première transaction réserve puis reste ouverte tant qu'on ne la libère pas ; la seconde doit
 * alors attendre (elle ne se termine pas) et obtient la valeur suivante après le commit de la première. Sans le verrou de ligne
 * ({@code UPDATE ... seq_value + 1}), la seconde lirait la même valeur sans attendre et le test échoue.
 */
public abstract class SequenceConcurrenteContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-0000000000c1"));
    private static final int ANNEE = 2026;

    private static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected abstract SequenceDeFacturation sequence();

    protected abstract TransactionTemplate transaction();

    protected abstract void poser(CentreId centre, int annee, int valeur);

    /**
     * Vrai si la base sait gérer deux créations simultanées de la ligne d'une année (PostgreSQL : ON CONFLICT).
     */
    protected boolean creationConcurrenteDeLaLigneSupportee() {
        return true;
    }

    @Test
    void should_make_a_second_reservation_wait_for_the_end_of_the_first_transaction_then_take_the_next_value() throws Exception {
        poser(CENTRE, ANNEE, 41);
        CountDownLatch reservee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> premiere = pool.submit(() -> transaction().execute(statut -> {
                int valeur = sequence().reserverLaSuivante(CENTRE, ANNEE);
                reservee.countDown();
                attendre(liberer);
                return valeur;
            }));
            assertThat(reservee.await(20, TimeUnit.SECONDS)).isTrue();
            Future<Integer> seconde = pool.submit(() -> transaction().execute(statut -> sequence().reserverLaSuivante(CENTRE, ANNEE)));

            assertThatThrownBy(() -> seconde.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();

            assertThat(premiere.get(20, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(seconde.get(20, TimeUnit.SECONDS)).isEqualTo(43);
            assertThat(sequence().courante(CENTRE, ANNEE)).isEqualTo(43);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_give_back_the_reserved_value_when_the_transaction_is_rolled_back() {
        poser(CENTRE, ANNEE, 41);

        transaction().executeWithoutResult(statut -> {
            assertThat(sequence().reserverLaSuivante(CENTRE, ANNEE)).isEqualTo(42);
            statut.setRollbackOnly();
        });

        assertThat(sequence().courante(CENTRE, ANNEE)).isEqualTo(41);
        Integer suivante = transaction().execute(statut -> sequence().reserverLaSuivante(CENTRE, ANNEE));
        assertThat(suivante).isEqualTo(42);
    }

    @Test
    void should_create_the_line_of_a_new_year_once_when_two_transactions_start_the_year_together() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(creationConcurrenteDeLaLigneSupportee());
        CountDownLatch reservee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> premiere = pool.submit(() -> transaction().execute(statut -> {
                int valeur = sequence().reserverLaSuivante(CENTRE, ANNEE + 1);
                reservee.countDown();
                attendre(liberer);
                return valeur;
            }));
            assertThat(reservee.await(20, TimeUnit.SECONDS)).isTrue();
            Future<Integer> seconde = pool.submit(() -> transaction().execute(statut -> sequence().reserverLaSuivante(CENTRE, ANNEE + 1)));

            assertThatThrownBy(() -> seconde.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();

            assertThat(premiere.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(seconde.get(20, TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }
}
