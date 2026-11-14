package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.reglement.DemandeDeReglement;
import com.hemodialyse.backend.contexts.facturation.application.reglement.EnregistrerUnReglement;
import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PublicationDesReglements;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.RemboursementSuperieurAuTotalRegle;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ReglementsJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SituationsDesFacturesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FactureReglementRecordedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deux enregistrements simultanés sur la même facture, sur la vraie base (H2 et PostgreSQL 16) : preuve de l'idempotence
 * (KB-FAC-39) et du contrôle du remboursement (KB-FAC-43) sous concurrence. Déterministe : le premier enregistrement reste
 * ouvert (sa publication attend) après avoir inséré son règlement ; le second doit attendre le verrou de la facture (il ne se
 * termine pas) puis voir le règlement du premier une fois celui-ci validé. Sans le verrou {@code SELECT ... FOR UPDATE} de la
 * facture, le second ne verrait rien : un doublon serait créé, ou le remboursement accepté.
 */
public abstract class ReglementsConcurrentsContrat {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99995600-0000-0000-0000-0000000000a1"));
    private static final UUID FACTURE = UUID.fromString("99995600-0000-0000-0000-0000000f0001");

    private static void attendre(CountDownLatch verrou) {
        try {
            verrou.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected abstract JdbcTemplate jdbc();

    protected abstract PlatformTransactionManager transactionManager();

    private void poserLaFacture(String regleDeja) {
        jdbc().update("DELETE FROM facture_reglements WHERE center_id = ?", CENTRE.valeur());
        jdbc().update("DELETE FROM factures WHERE center_id = ?", CENTRE.valeur());
        jdbc().update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, 'FAC-C-1', ?, ?, ?, 0, 1000, 0, 1000)",
                FACTURE, CENTRE.valeur(), UUID.randomUUID(), Date.valueOf("2026-01-31"), Date.valueOf("2026-01-31"),
                Date.valueOf("2026-01-31"));
        if (regleDeja != null) {
            jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, code_reglement) "
                    + "VALUES (?, ?, ?, ?, ?, 'test', 'REG-INIT')", UUID.randomUUID(), FACTURE, CENTRE.valeur(),
                    new BigDecimal(regleDeja), Date.valueOf("2026-02-01"));
        }
    }

    private EnregistrerUnReglement cas(PublicationDesReglements publication) {
        return new EnregistrerUnReglement(new ReglementsJdbc(jdbc()), new SituationsDesFacturesJdbc(jdbc(),
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PayeursDuCentreJdbc(jdbc()),
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LotsDePatientsJdbc(jdbc())), publication,
                new UniteDeTravailSpring(new TransactionTemplate(transactionManager())), Clock.systemDefaultZone(), UUID::randomUUID);
    }

    private static DemandeDeReglement demande(String montant, String cle) {
        return new DemandeDeReglement(new BigDecimal(montant), LocalDate.of(2026, 2, 2), null, null, cle);
    }

    private int nombreDeReglements() {
        return jdbc().queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE facture_id = ?", Integer.class, FACTURE);
    }

    /**
     * Publication qui garde la transaction du premier enregistrement ouverte jusqu'à {@code liberer}.
     */
    private static final class PublicationBloquante implements PublicationDesReglements {
        final CountDownLatch inseree = new CountDownLatch(1);
        final CountDownLatch liberer = new CountDownLatch(1);
        final AtomicInteger publies = new AtomicInteger();

        @Override
        public void reglementEnregistre(FactureReglementRecordedEvent evenement) {
            publies.incrementAndGet();
            inseree.countDown();
            attendre(liberer);
        }
    }

    @Test
    void should_create_one_payment_and_one_event_when_the_same_key_is_sent_twice_at_the_same_time() throws Exception {
        poserLaFacture(null);
        PublicationBloquante premiere = new PublicationBloquante();
        AtomicInteger publieesParLaSeconde = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<LigneDeSituation> envoi1 = pool.submit(() -> cas(premiere).enregistrer(CENTRE, FACTURE, demande("100.00", "envoi-1"), null));
            assertThat(premiere.inseree.await(20, TimeUnit.SECONDS)).isTrue();
            Future<LigneDeSituation> envoi2 = pool.submit(() -> cas(e -> publieesParLaSeconde.incrementAndGet())
                    .enregistrer(CENTRE, FACTURE, demande("100.00", "envoi-1"), null));

            assertThatThrownBy(() -> envoi2.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            premiere.liberer.countDown();

            LigneDeSituation resultat1 = envoi1.get(20, TimeUnit.SECONDS);
            LigneDeSituation resultat2 = envoi2.get(20, TimeUnit.SECONDS);
            assertThat(resultat2).isEqualTo(resultat1);
            assertThat(nombreDeReglements()).isEqualTo(1);
            assertThat(premiere.publies.get()).isEqualTo(1);
            assertThat(publieesParLaSeconde.get()).isZero();
        } finally {
            premiere.liberer.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void should_accept_only_one_of_two_simultaneous_refunds_that_together_exceed_the_total_paid() throws Exception {
        poserLaFacture("100.00");
        PublicationBloquante premiere = new PublicationBloquante();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<LigneDeSituation> remboursement1 = pool.submit(() -> cas(premiere).enregistrer(CENTRE, FACTURE, demande("-60.00", null), null));
            assertThat(premiere.inseree.await(20, TimeUnit.SECONDS)).isTrue();
            Future<LigneDeSituation> remboursement2 = pool.submit(() -> cas(e -> {
            }).enregistrer(CENTRE, FACTURE, demande("-60.00", null), null));

            assertThatThrownBy(() -> remboursement2.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            premiere.liberer.countDown();

            assertThat(remboursement1.get(20, TimeUnit.SECONDS).montantRegle()).isEqualByComparingTo("40.00");
            assertThatThrownBy(() -> remboursement2.get(20, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RemboursementSuperieurAuTotalRegle.class);
            assertThat(nombreDeReglements()).isEqualTo(2);
            assertThat(jdbc().queryForObject("SELECT SUM(montant) FROM facture_reglements WHERE facture_id = ?", BigDecimal.class, FACTURE))
                    .isEqualByComparingTo("40.00");
        } finally {
            premiere.liberer.countDown();
            pool.shutdownNow();
        }
    }
}
