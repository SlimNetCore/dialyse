package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la libération des places sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn) : mêmes tests que H2, plus la course entre la
 * libération de nuit et l'enregistrement d'une fiche sur la même ligne. Base JETABLE uniquement :
 * {@code -Dplanification.pg16.url=jdbc:postgresql://…} (script {@code planification-p7-pg16-rejeu.sh}).
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
class LiberationJdbcPostgresContractIT extends LiberationJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void vider() {
        jdbc.execute("ALTER TABLE mouvement_patient DROP CONSTRAINT IF EXISTS zz_p7_echec");
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            for (String table : new String[]{"mouvement_patient", "patients", "planning_parametres"}) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    protected Duration delaiDeVerrou() {
        return Duration.ofMillis(800);
    }

    @Test
    void la_base_est_bien_postgresql_16() {
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).startsWith("PostgreSQL 16");
    }

    /**
     * Ordre global des verrous (revue P7, ADR 0056/0061) : la fiche prend le verrou du centre AVANT d'écrire la ligne du patient, comme
     * la nuit. Rejouée en parallèle, aucun interblocage (40P01) ni délai dépassé (55P03) : la nuit n'a aucun incident et la fiche réussit.
     */
    @Test
    void should_never_deadlock_when_a_record_and_the_night_take_the_centre_lock_before_the_patient_row() throws Exception {
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        var verrou = new com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDesPlacesJdbc(jdbc);
        // la ligne du centre existe (committée) : sa création simultanée est une autre règle (la perdante reçoit 409, ADR 0059)
        tx.executeWithoutResult(s -> verrou.prendre(CENTRE));
        for (int tour = 0; tour < 8; tour++) {
            jdbc.update("DELETE FROM mouvement_patient WHERE center_id = ?", CENTRE.valeur());
            jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE.valeur());
            fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
            fiche(CENTRE, 2, "TRANSFERE", J.minusDays(2));
            CompletableFuture<LibererLesPlacesEchues.ResultatDuCentre> nuit =
                    CompletableFuture.supplyAsync(() -> monde().nuit().liberer(CENTRE));
            CompletableFuture<Void> fiche = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
                verrou.prendre(CENTRE);
                jdbc.update("UPDATE patients SET nom = 'Modifie' WHERE id = ?", patient(1));
            }));
            fiche.get(20, TimeUnit.SECONDS);
            assertThat(nuit.get(20, TimeUnit.SECONDS).incidents()).as("tour " + tour).isEmpty();
        }
    }

    /**
     * Course : la fiche tient la ligne du patient (et la change) pendant que la nuit la libère ; la nuit attend, relit, ne perd rien.
     */
    @Test
    void should_not_lose_a_record_saved_at_the_same_time_as_the_night_release() throws Exception {
        fiche(CENTRE, 1, "TRANSFERE", J.minusDays(2));
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(s -> new com.hemodialyse.backend.contexts.planification.infrastructure.persistence.VerrouDesPlacesJdbc(jdbc)
                .prendre(CENTRE));
        CountDownLatch tenue = new CountDownLatch(1);
        CountDownLatch relache = new CountDownLatch(1);
        // la fiche reporte la sortie dans le futur et reste ouverte un instant
        CompletableFuture<Void> fiche = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE patients SET date_evenement_etat = ? WHERE id = ?", java.sql.Date.valueOf(J.plusDays(10)), patient(1));
            tenue.countDown();
            try {
                relache.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(tenue.await(10, TimeUnit.SECONDS)).isTrue();
        // la nuit a lu la fiche AVANT la modification (candidate), prend le verrou du centre puis attend la ligne de la fiche
        CompletableFuture<LibererLesPlacesEchues.ResultatDuCentre> nuit = CompletableFuture.supplyAsync(() -> monde().nuit().liberer(CENTRE));
        Thread.sleep(300);
        relache.countDown();
        fiche.get(20, TimeUnit.SECONDS);
        var resultat = nuit.get(20, TimeUnit.SECONDS);
        // elle relit la fiche validée sous verrou : la sortie est reportée, la place est conservée, aucun mouvement, aucun incident
        assertThat(resultat.incidents()).isEmpty();
        assertThat(resultat.liberees()).isZero();
        assertThat(aUnePlace(1)).isTrue();
        assertThat(mouvements()).isZero();
    }
}
