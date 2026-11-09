package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LiaisonDesSeancesJdbc;
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

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice seance-se7c2-ecritures-facturation (ARGENT) : le filet de course d'origine (module Facturation, ancienne ACL), REJOUÉ contre l'adapter
 * de la Séance (ADR 0097, lot c2b), assertions inchangées : deux émissions simultanées de
 * facture sur les mêmes séances ({@code LiaisonDesSeancesJdbc.lier}) sur un PostgreSQL 16 RÉEL (H2 n'est pas fiable pour la
 * concurrence). Seul le garde {@code facture_id IS NULL AND statut IN ('VALIDEE', 'SIGNEE')} de l'UPDATE protège : en lecture
 * validée, la seconde transaction attend le verrou de ligne de la première, relit la ligne validée, ne trouve plus rien à
 * modifier, échoue (0 ligne) et sa transaction est annulée.
 * <p>
 * Déterminisme : la première transaction garde son verrou ouvert tant que la seconde n'est pas observée EN ATTENTE DE VERROU dans
 * {@code pg_stat_activity} ; aucune temporisation fixe. Aucune horloge. Base JETABLE uniquement :
 * {@code -Dseance.pg16.url=jdbc:postgresql://…} ; le mot de passe vient de la variable d'environnement
 * {@code SEANCE_PG16_PASSWORD} (jamais sur la ligne de commande).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${seance.pg16.url}",
        "spring.datasource.username=${seance.pg16.user:seance_test}",
        "spring.datasource.password=${seance.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "seance.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LiaisonConcurrentePostgresContractIT {

    private static final UUID CENTRE_ID = UUID.fromString("99997c20-0000-0000-0000-00000000000a");
    private static final UUID CENTRE = CENTRE_ID;
    private static final UUID PATIENT = UUID.fromString("99997c20-0000-0000-0000-00000000a001");
    private static final String MESSAGE = "Une ou plusieurs seances ne sont plus eligibles a la validation";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void decor() {
        nettoyer();
        // Les contraintes V54 et V55 telles que Flyway les pose (NOT VALID) : d'autres contrats ont pu les retirer de la base jetable.
        TableDesSeances.poserLesContraintesDeProduction(jdbc);
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, "
                        + "type_patient, etat_patient) VALUES (?, ?, 'P-C2', 'Amrani', 'Lina', 'F', ?, 'ASS-C2', 'NON_VACANCIER', 'PERMANENT')",
                PATIENT, CENTRE_ID, Date.valueOf(LocalDate.of(2026, 1, 10)));
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM seances WHERE center_id = ?", CENTRE_ID);
        jdbc.update("DELETE FROM factures WHERE center_id = ?", CENTRE_ID);
        jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_ID);
    }

    /** Une facture émise du centre (KB-SEA-74, ADR 0114 : la liaison ne vise qu'une facture existante, la clé étrangère V54 aussi). */
    private UUID facture() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0)",
                id, CENTRE_ID, PATIENT, "F-" + id, Date.valueOf(LocalDate.of(2026, 8, 1)), Date.valueOf(LocalDate.of(2026, 8, 31)),
                Date.valueOf(LocalDate.of(2026, 9, 1)));
        return id;
    }

    private UUID seance(String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, facture_id) VALUES (?, ?, ?, ?, ?, NULL)",
                id, CENTRE_ID, PATIENT, Date.valueOf(LocalDate.of(2026, 8, 2)), statut);
        return id;
    }

    private String etat(UUID seance) {
        return jdbc.queryForObject("SELECT statut || '/' || COALESCE(CAST(facture_id AS VARCHAR), 'sans facture') FROM seances WHERE id = ?",
                String.class, seance);
    }

    /**
     * La première émission lie {@code premiere} et garde sa transaction ouverte ; la seconde lie {@code seconde} et se bloque sur
     * le verrou ; dès qu'elle est observée en attente, la première valide. Renvoie l'exception de la seconde.
     */
    private Throwable courseDeDeuxEmissions(UUID factureUne, List<UUID> premiere, UUID factureDeux, List<UUID> seconde)
            throws Exception {
        LiaisonDesSeancesJdbc liaison = new LiaisonDesSeancesJdbc(jdbc);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch verrouille = new CountDownLatch(1);
        CountDownLatch valider = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> une = pool.submit(() -> transaction.execute(statut -> {
                liaison.lier(CENTRE, factureUne, premiere);
                verrouille.countDown();
                try {
                    valider.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(verrouille.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> deux = pool.submit(() -> transaction.execute(statut -> {
                liaison.lier(CENTRE, factureDeux, seconde);
                return null;
            }));
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                    + "AND wait_event_type = 'Lock'", Integer.class) < 1) {
                assertThat(System.nanoTime()).as("la seconde émission doit se bloquer sur le verrou de ligne").isLessThan(limite);
                Thread.onSpinWait();
            }
            valider.countDown();
            une.get(30, TimeUnit.SECONDS);
            try {
                deux.get(30, TimeUnit.SECONDS);
                return null;
            } catch (ExecutionException e) {
                return e.getCause();
            }
        } finally {
            valider.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void de_deux_emissions_simultanees_sur_les_memes_seances_une_seule_gagne_et_l_autre_n_ecrit_rien() throws Exception {
        UUID s1 = seance("VALIDEE");
        UUID s2 = seance("SIGNEE");
        UUID gagnante = facture();
        UUID perdante = facture();

        Throwable echec = courseDeDeuxEmissions(gagnante, List.of(s1, s2), perdante, List.of(s1, s2));

        assertThat(echec).isInstanceOf(IllegalStateException.class).hasMessage(MESSAGE);
        assertThat(etat(s1)).isEqualTo("FACTUREE/" + gagnante);
        assertThat(etat(s2)).isEqualTo("FACTUREE/" + gagnante);
    }

    @Test
    void l_emission_perdante_est_annulee_en_entier_meme_pour_les_seances_libres_qu_elle_avait_deja_liees() throws Exception {
        UUID disputee = seance("VALIDEE");
        UUID libre = seance("VALIDEE");
        UUID gagnante = facture();
        UUID perdante = facture();

        Throwable echec = courseDeDeuxEmissions(gagnante, List.of(disputee), perdante, List.of(libre, disputee));

        assertThat(echec).isInstanceOf(IllegalStateException.class).hasMessage(MESSAGE);
        assertThat(etat(disputee)).isEqualTo("FACTUREE/" + gagnante);
        assertThat(etat(libre)).as("la liaison de la séance libre a été annulée avec la transaction de la perdante")
                .isEqualTo("VALIDEE/sans facture");
    }

    @Test
    void une_seconde_emission_apres_la_validation_de_la_premiere_est_refusee_sans_rien_ecrire() {
        UUID s1 = seance("VALIDEE");
        UUID gagnante = facture();
        LiaisonDesSeancesJdbc liaison = new LiaisonDesSeancesJdbc(jdbc);
        liaison.lier(CENTRE, gagnante, List.of(s1));

        assertThatThrownBy(() -> liaison.lier(CENTRE, facture(), List.of(s1)))
                .isInstanceOf(IllegalStateException.class).hasMessage(MESSAGE);
        assertThat(etat(s1)).isEqualTo("FACTUREE/" + gagnante);
    }

    /** KB-SEA-74 : la clé étrangère V54 (NOT VALID) refuse, sur PostgreSQL, un écrivain qui contournerait la garde de l'adapter. */
    @Test
    void la_cle_etrangere_refuse_un_identifiant_de_facture_inexistant_meme_ecrit_hors_de_l_adapter() {
        UUID s1 = seance("VALIDEE");

        assertThatThrownBy(() -> jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", UUID.randomUUID(), s1))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(etat(s1)).isEqualTo("VALIDEE/sans facture");
    }

    /** KB-SEA-74 : une facture encore liée à une séance ne se supprime pas (refus voulu). */
    @Test
    void une_facture_encore_liee_a_une_seance_ne_se_supprime_pas() {
        UUID s1 = seance("VALIDEE");
        UUID facture = facture();
        new LiaisonDesSeancesJdbc(jdbc).lier(CENTRE, facture, List.of(s1));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM factures WHERE id = ?", facture))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** KB-SEA-82 : le CHECK V55 (NOT VALID) refuse un statut non canonique, mais ne bloque pas une ligne sale déjà présente. */
    @Test
    void la_contrainte_de_statut_refuse_une_nouvelle_ecriture_non_canonique_sans_toucher_aux_lignes_deja_presentes() {
        assertThatThrownBy(() -> seance("validee")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> seance("VALIDEE ")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID canonique = seance("SIGNEE");
        assertThat(etat(canonique)).isEqualTo("SIGNEE/sans facture");
    }
}
