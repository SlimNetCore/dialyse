package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve que {@link InterventionsHotes#verrouiller} verrouille vraiment (H2 et PostgreSQL 16) : tant que la première transaction
 * est ouverte, la seconde attend ; elle n'obtient le verrou qu'après la fin de la première. C'est ce qui tient le quota de 50
 * sous concurrence (KB-GMAO-58).
 */
final class VerrouDeLIntervention {

    private VerrouDeLIntervention() {
    }

    static void verifier(InterventionsHotes hotes, TransactionTemplate transaction, InterventionId intervention) throws Exception {
        verifier(hotes::verrouiller, transaction, intervention);
    }

    /** Même preuve pour n'importe quelle opération qui verrouille l'intervention (ex. {@code chargerPourModification}). */
    static void verifier(java.util.function.Consumer<InterventionId> verrouiller, TransactionTemplate transaction,
                         InterventionId intervention) throws Exception {
        CountDownLatch premierAVerrouille = new CountDownLatch(1);
        AtomicLong finDeLaPremiere = new AtomicLong();
        AtomicLong verrouDeLaSeconde = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> premiere = pool.submit(() -> transaction.executeWithoutResult(statut -> {
                verrouiller.accept(intervention);
                premierAVerrouille.countDown();
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finDeLaPremiere.set(System.nanoTime());
            }));
            premierAVerrouille.await(10, TimeUnit.SECONDS);
            Future<?> seconde = pool.submit(() -> transaction.executeWithoutResult(statut -> {
                verrouiller.accept(intervention);
                verrouDeLaSeconde.set(System.nanoTime());
            }));
            premiere.get(20, TimeUnit.SECONDS);
            seconde.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(verrouDeLaSeconde.get()).as("la seconde transaction obtient le verrou après la fin de la première")
                .isGreaterThanOrEqualTo(finDeLaPremiere.get());
    }
}
