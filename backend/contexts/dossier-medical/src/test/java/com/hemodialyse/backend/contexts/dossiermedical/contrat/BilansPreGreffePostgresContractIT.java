package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des bilans pré-greffe sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Ddossiermedical.pg16.url=jdbc:postgresql://…} (script {@code dossier-medical-dm7-pg16-rejeu.sh}).
 */
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
class BilansPreGreffePostgresContractIT extends BilansPreGreffeJdbcContrat {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;

    private UniteDeTravailSpring unite() {
        return new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, Duration.ofMillis(500));
    }

    /** La première connexion tient la ligne ; la seconde attend {@code lock_timeout} (55P03) puis lève l'écriture concurrente (KB-MED-91). */
    @Test
    void une_seconde_ecriture_sur_un_bilan_verrouille_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        bilans().ouvrir(bilan(CENTRE_A, PATIENT, T0));
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement(
                    "SELECT id FROM bilans_pre_greffe WHERE patient_id = ? AND center_id = ? FOR UPDATE")) {
                tient.setObject(1, PATIENT.valeur());
                tient.setObject(2, CENTRE_A.valeur());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> bilans().trouverEnVerrouillant(CENTRE_A, PATIENT)))
                        .isInstanceOf(EcritureConcurrenteSurUnBilanPreGreffe.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        var change = unite().executer(() -> {
            var charge = bilans().trouverEnVerrouillant(CENTRE_A, PATIENT).orElseThrow();
            charge.changerLeStatut(StatutDuBilanPreGreffe.BILAN_EN_COURS, JOUR, T0.plusSeconds(5));
            bilans().enregistrer(charge);
            return charge;
        });
        assertThat(change.statut()).isEqualTo(StatutDuBilanPreGreffe.BILAN_EN_COURS);
    }

    /** Un seul bilan par patient : le verrou consultatif d'ouverture tenu par une autre transaction fait échouer l'ouverture concurrente. */
    @Test
    void l_ouverture_concurrente_du_bilan_d_un_patient_attend_le_verrou_consultatif_puis_leve_l_ecriture_concurrente() throws Exception {
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, "bilan-pre-greffe|" + CENTRE_A.valeur() + "|" + PATIENT.valeur());
                tient.execute();
                assertThatThrownBy(() -> unite().executer(() -> {
                    bilans().verrouillerLOuverture(CENTRE_A, PATIENT);
                    return null;
                })).isInstanceOf(EcritureConcurrenteSurUnBilanPreGreffe.class);
            } finally {
                verrou.rollback();
            }
        }
        unite().executer(() -> {
            bilans().verrouillerLOuverture(CENTRE_A, PATIENT);
            return null;
        });
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
