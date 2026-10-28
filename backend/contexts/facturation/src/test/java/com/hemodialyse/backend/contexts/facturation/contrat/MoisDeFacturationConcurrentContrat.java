package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.cloture.CloturerLeMois;
import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationClos;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sérialisation de la clôture et de l'émission d'un même (centre, mois) entre transactions concurrentes (RG-FAC-042), sur la vraie
 * base (H2 et PostgreSQL 16). Déterministe : la première transaction prend le verrou du mois puis reste ouverte tant qu'on ne la
 * libère pas ; la seconde doit alors attendre (elle ne se termine pas). Sans le verrou de ligne ({@code SELECT ... FOR UPDATE}), la
 * seconde ne serait pas bloquée et le test échoue. Les deux ordres sont prouvés : une validation en cours fait attendre la
 * clôture (la validation aboutit, la clôture la suit) ; une clôture en cours fait attendre la validation, qui est alors refusée.
 */
public abstract class MoisDeFacturationConcurrentContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997100-0000-0000-0000-0000000000c1"));
    private static final YearMonth AOUT = YearMonth.of(2026, 8);
    private static final UUID ADMIN = UUID.fromString("99997100-0000-0000-0000-00000000c001");
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneOffset.UTC);

    private static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected abstract MoisDeFacturationRepository mois();

    protected abstract TransactionTemplate transaction();

    /** Vrai si la base sait gérer deux créations simultanées de la ligne d'un mois (PostgreSQL : ON CONFLICT). */
    protected boolean creationConcurrenteDeLaLigneSupportee() {
        return true;
    }

    private CloturerLeMois cloture() {
        UniteDeTravail unite = new UniteDeTravail() {
            @Override
            public <T> T executer(java.util.function.Supplier<T> action) {
                return transaction().execute(statut -> action.get());
            }
        };
        return new CloturerLeMois(mois(), (centre, periode) -> List.of(), new PublicationNulle(), unite, HORLOGE, (centre, m) -> true,
                centre -> ZoneOffset.UTC);
    }

    private GardeDesMoisClos garde() {
        return new GardeDesMoisClos(mois());
    }

    private static PeriodeDeFacturation periodeAout() {
        return PeriodeDeFacturation.deMois(AOUT);
    }

    protected void ligneDuMoisDejaCreee() {
        transaction().executeWithoutResult(statut -> mois().verrouiller(CENTRE, AOUT));
    }

    @Test
    void should_make_the_closure_wait_for_a_validation_in_progress_then_close_after_it() throws Exception {
        if (!creationConcurrenteDeLaLigneSupportee()) {
            ligneDuMoisDejaCreee();
        }
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> validation = pool.submit(() -> transaction().execute(statut -> {
                garde().exigerOuverts(CENTRE, periodeAout());
                verrouille.countDown();
                attendre(liberer);
                return "emise";
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            Future<CloturerLeMois.ResultatDeCloture> cloture = pool.submit(() -> cloture().executer(CENTRE, AOUT, ADMIN));

            assertThatThrownBy(() -> cloture.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(mois().duMois(CENTRE, AOUT).estClos()).isFalse();
            liberer.countDown();

            assertThat(validation.get(20, TimeUnit.SECONDS)).isEqualTo("emise");
            assertThat(cloture.get(20, TimeUnit.SECONDS).mois()).isEqualTo(AOUT);
            assertThat(mois().duMois(CENTRE, AOUT).estClos()).isTrue();
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_make_a_validation_wait_for_a_closure_in_progress_then_refuse_it() throws Exception {
        if (!creationConcurrenteDeLaLigneSupportee()) {
            ligneDuMoisDejaCreee();
        }
        CountDownLatch cloturee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> cloture = pool.submit(() -> transaction().execute(statut -> {
                var courant = mois().verrouiller(CENTRE, AOUT);
                mois().enregistrer(courant, courant.cloturer(HORLOGE.instant(), ADMIN, 0));
                cloturee.countDown();
                attendre(liberer);
                return "cloturee";
            }));
            assertThat(cloturee.await(20, TimeUnit.SECONDS)).isTrue();
            Future<String> validation = pool.submit(() -> transaction().execute(statut -> {
                garde().exigerOuverts(CENTRE, periodeAout());
                return "emise";
            }));

            assertThatThrownBy(() -> validation.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();

            assertThat(cloture.get(20, TimeUnit.SECONDS)).isEqualTo("cloturee");
            assertThatThrownBy(() -> validation.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(MoisDeFacturationClos.class);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_not_let_a_validation_of_another_month_or_centre_wait_for_the_closure() throws Exception {
        ligneDuMoisDejaCreee();
        CountDownLatch cloturee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> cloture = pool.submit(() -> transaction().execute(statut -> {
                var courant = mois().verrouiller(CENTRE, AOUT);
                mois().enregistrer(courant, courant.cloturer(HORLOGE.instant(), ADMIN, 0));
                cloturee.countDown();
                attendre(liberer);
                return "cloturee";
            }));
            assertThat(cloturee.await(20, TimeUnit.SECONDS)).isTrue();

            // un autre mois du même centre : aucun blocage
            transaction().executeWithoutResult(statut ->
                    garde().exigerOuvert(CENTRE, LocalDate.of(2026, 9, 15)));
            liberer.countDown();
            assertThat(cloture.get(20, TimeUnit.SECONDS)).isEqualTo("cloturee");
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_lock_every_month_of_a_multi_month_validation_so_a_closure_of_its_last_month_waits() throws Exception {
        YearMonth septembre = YearMonth.of(2026, 9);
        if (!creationConcurrenteDeLaLigneSupportee()) {
            transaction().executeWithoutResult(statut -> mois().verrouillerDe(CENTRE, YearMonth.of(2026, 7), septembre));
        }
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> validation = pool.submit(() -> transaction().execute(statut -> {
                garde().exigerOuverts(CENTRE, new PeriodeDeFacturation(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 9, 20)));
                verrouille.countDown();
                attendre(liberer);
                return "emise";
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            Future<CloturerLeMois.ResultatDeCloture> cloture = pool.submit(() -> cloture().executer(CENTRE, septembre, ADMIN));

            assertThatThrownBy(() -> cloture.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();

            assertThat(validation.get(20, TimeUnit.SECONDS)).isEqualTo("emise");
            assertThat(cloture.get(20, TimeUnit.SECONDS).mois()).isEqualTo(septembre);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_release_the_month_when_the_transaction_is_rolled_back() {
        transaction().executeWithoutResult(statut -> {
            var courant = mois().verrouiller(CENTRE, AOUT);
            mois().enregistrer(courant, courant.cloturer(HORLOGE.instant(), ADMIN, 3));
            statut.setRollbackOnly();
        });

        assertThat(mois().duMois(CENTRE, AOUT).estClos()).isFalse();
        assertThat(mois().journal(CENTRE, AOUT)).isEmpty();
        transaction().executeWithoutResult(statut -> garde().exigerOuverts(CENTRE, periodeAout()));
    }

    private static final class PublicationNulle implements com.hemodialyse.backend.contexts.facturation.application.cloture.PublicationDesPeriodes {
        @Override
        public void cloturee(com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationCloturee evenement) {
        }

        @Override
        public void rouverte(com.hemodialyse.backend.contexts.facturation.publishedlanguage.PeriodeDeFacturationRouverte evenement) {
        }
    }
}
