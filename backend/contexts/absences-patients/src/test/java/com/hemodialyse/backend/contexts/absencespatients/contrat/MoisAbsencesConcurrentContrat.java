package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.application.CloturerLeMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.GardeDesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.absencespatients.application.PublicationDesMoisDAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.absencespatients.doubles.SuiviEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesClos;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesCloture;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesRouvert;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
 * Sérialisation de la clôture et de l'écriture d'une absence d'un même (centre, mois) entre transactions concurrentes (A18), sur la
 * vraie base (H2 et PostgreSQL 16). Déterministe : la première transaction prend le verrou du mois puis reste ouverte tant qu'on ne
 * la libère pas ; la seconde doit alors attendre (elle ne se termine pas). Sans le verrou de ligne ({@code SELECT ... FOR UPDATE}),
 * la seconde ne serait pas bloquée et le test échoue. Les deux ordres sont prouvés : une écriture en cours fait attendre la clôture
 * (l'écriture aboutit, la clôture la suit) ; une clôture en cours fait attendre l'écriture, qui est alors refusée.
 */
public abstract class MoisAbsencesConcurrentContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997200-0000-0000-0000-0000000000c1"));
    protected static final YearMonth AOUT = YearMonth.of(2026, 8);
    private static final LocalDate JOUR_D_AOUT = LocalDate.of(2026, 8, 14);
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997200-0000-0000-0000-00000000c001"));
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);

    private static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected abstract MoisAbsencesRepository mois();

    protected abstract TransactionTemplate transaction();

    /**
     * Vrai si la base sait gérer deux créations simultanées de la ligne d'un mois (PostgreSQL : ON CONFLICT).
     */
    protected boolean creationConcurrenteDeLaLigneSupportee() {
        return true;
    }

    protected UniteDeTravail unite() {
        return new UniteDeTravail() {
            @Override
            public <T> T executer(java.util.function.Supplier<T> action) {
                return transaction().execute(statut -> action.get());
            }
        };
    }

    protected CloturerLeMoisDAbsences cloture() {
        PublicationDesMoisDAbsences nulle = new PublicationDesMoisDAbsences() {
            @Override
            public void cloture(MoisDAbsencesCloture evenement) {
            }

            @Override
            public void rouvert(MoisDAbsencesRouvert evenement) {
            }
        };
        return new CloturerLeMoisDAbsences(mois(), new SuiviEnMemoire(), nulle, unite(), HorlogeDesCentres.enUtc(HORLOGE));
    }

    protected GardeDesMoisDAbsences garde() {
        return new GardeDesMoisDAbsences(mois());
    }

    protected void ligneDuMoisDejaCreee() {
        transaction().executeWithoutResult(statut -> mois().verrouiller(CENTRE, AOUT));
    }

    @Test
    void should_make_the_closure_wait_for_a_write_in_progress_then_close_after_it() throws Exception {
        if (!creationConcurrenteDeLaLigneSupportee()) {
            ligneDuMoisDejaCreee();
        }
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> ecriture = pool.submit(() -> transaction().execute(statut -> {
                garde().exigerOuvert(CENTRE, JOUR_D_AOUT);
                verrouille.countDown();
                attendre(liberer);
                return "ecrite";
            }));
            assertThat(verrouille.await(20, TimeUnit.SECONDS)).isTrue();
            Future<CloturerLeMoisDAbsences.ResultatDeCloture> cloture = pool.submit(() -> cloture().executer(CENTRE, AOUT, ADMIN));

            assertThatThrownBy(() -> cloture.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(mois().duMois(CENTRE, AOUT).estClos()).isFalse();
            liberer.countDown();

            assertThat(ecriture.get(20, TimeUnit.SECONDS)).isEqualTo("ecrite");
            assertThat(cloture.get(20, TimeUnit.SECONDS).mois()).isEqualTo(AOUT);
            assertThat(mois().duMois(CENTRE, AOUT).estClos()).isTrue();
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_make_a_write_wait_for_a_closure_in_progress_then_refuse_it() throws Exception {
        if (!creationConcurrenteDeLaLigneSupportee()) {
            ligneDuMoisDejaCreee();
        }
        CountDownLatch cloturee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> cloture = pool.submit(() -> transaction().execute(statut -> {
                var courant = mois().verrouiller(CENTRE, AOUT);
                mois().enregistrer(courant, courant.cloturer(HORLOGE.instant(), ADMIN, 0, 0));
                cloturee.countDown();
                attendre(liberer);
                return "cloturee";
            }));
            assertThat(cloturee.await(20, TimeUnit.SECONDS)).isTrue();
            Future<String> ecriture = pool.submit(() -> transaction().execute(statut -> {
                garde().exigerOuvert(CENTRE, JOUR_D_AOUT);
                return "ecrite";
            }));

            assertThatThrownBy(() -> ecriture.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            liberer.countDown();

            assertThat(cloture.get(20, TimeUnit.SECONDS)).isEqualTo("cloturee");
            assertThatThrownBy(() -> ecriture.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(MoisDAbsencesClos.class);
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_not_let_a_write_of_another_month_wait_for_the_closure() throws Exception {
        ligneDuMoisDejaCreee();
        CountDownLatch cloturee = new CountDownLatch(1);
        CountDownLatch liberer = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> cloture = pool.submit(() -> transaction().execute(statut -> {
                var courant = mois().verrouiller(CENTRE, AOUT);
                mois().enregistrer(courant, courant.cloturer(HORLOGE.instant(), ADMIN, 0, 0));
                cloturee.countDown();
                attendre(liberer);
                return "cloturee";
            }));
            assertThat(cloturee.await(20, TimeUnit.SECONDS)).isTrue();

            // un autre mois du même centre : aucun blocage
            transaction().executeWithoutResult(statut -> garde().exigerOuvert(CENTRE, LocalDate.of(2026, 9, 15)));
            liberer.countDown();
            assertThat(cloture.get(20, TimeUnit.SECONDS)).isEqualTo("cloturee");
        } finally {
            liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_release_the_month_when_the_transaction_is_rolled_back() {
        transaction().executeWithoutResult(statut -> {
            var courant = mois().verrouiller(CENTRE, AOUT);
            mois().enregistrer(courant, courant.cloturer(HORLOGE.instant(), ADMIN, 3, 1));
            statut.setRollbackOnly();
        });

        assertThat(mois().duMois(CENTRE, AOUT).estClos()).isFalse();
        assertThat(mois().journal(CENTRE, AOUT)).isEmpty();
        transaction().executeWithoutResult(statut -> garde().exigerOuvert(CENTRE, JOUR_D_AOUT));
    }
}
