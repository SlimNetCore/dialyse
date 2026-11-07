package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.transaction.UniteDeTravailSpring;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat des étapes du bilan pré-greffe sur un PostgreSQL 16 RÉEL. Base JETABLE uniquement ({@code -Ddossiermedical.pg16.url=…}). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${dossiermedical.pg16.url}",
        "spring.datasource.username=${dossiermedical.pg16.user:dossier_medical_test}",
        "spring.datasource.password=${dossiermedical.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "dossiermedical.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EtapesDeBilanPostgresContractIT extends EtapesDeBilanJdbcContrat {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;

    private UniteDeTravailSpring unite() {
        return new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, Duration.ofMillis(500));
    }

    @Test
    void une_seconde_ecriture_sur_une_etape_verrouillee_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        var e = etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "x", T0);
        etapes().ajouter(e);
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT id FROM etapes_bilan_pre_greffe WHERE id = ? FOR UPDATE")) {
                tient.setObject(1, e.id());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, e.id())))
                        .isInstanceOf(EcritureConcurrenteSurUneEtapeDeBilan.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        var mise = unite().executer(() -> {
            var chargee = etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, e.id()).orElseThrow();
            chargee.mettreAJour(StatutDEtape.FAIT, LocalDate.of(2026, 11, 1), null, null, null, null, T0.plusSeconds(5));
            etapes().enregistrer(chargee);
            return chargee;
        });
        assertThat(mise.statut()).isEqualTo(StatutDEtape.FAIT);
    }

    /** Deux générations simultanées de la liste standard ne doivent rien doubler : la seconde attend le verrou consultatif du patient. */
    @Test
    void la_generation_concurrente_attend_le_verrou_consultatif_du_patient_puis_leve_l_ecriture_concurrente() throws Exception {
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, "etapes-pre-greffe|" + CENTRE_A.valeur() + "|" + PATIENT.valeur());
                tient.execute();
                assertThatThrownBy(() -> unite().executer(() -> {
                    etapes().verrouillerLaGeneration(CENTRE_A, PATIENT);
                    return null;
                })).isInstanceOf(EcritureConcurrenteSurUneEtapeDeBilan.class);
            } finally {
                verrou.rollback();
            }
        }
    }

    @BeforeEach
    void avant() {
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
