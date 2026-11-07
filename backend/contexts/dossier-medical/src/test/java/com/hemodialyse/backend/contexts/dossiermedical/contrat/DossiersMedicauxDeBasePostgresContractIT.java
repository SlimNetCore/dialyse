package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
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
 * Contrat des dossiers médicaux de base sur un PostgreSQL 16 RÉEL, plus les verrous (ligne : {@code FOR UPDATE} ; consultatif :
 * {@code dossier-medical|}) avec deux connexions. Base JETABLE uniquement ({@code -Ddossiermedical.pg16.url=…}).
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
class DossiersMedicauxDeBasePostgresContractIT extends DossiersMedicauxDeBaseJdbcContrat {
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
    void une_seconde_ecriture_sur_un_dossier_verrouille_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        var d = dossier(CENTRE_A, PATIENT, "x");
        dossiers().ajouter(d);
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT id FROM dossier_medical_patient WHERE id = ? FOR UPDATE")) {
                tient.setObject(1, d.id());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> dossiers().duPatientEnVerrouillant(CENTRE_A, PATIENT)))
                        .isInstanceOf(EcritureConcurrenteSurUnDossierMedical.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        var mis = unite().executer(() -> {
            var charge = dossiers().duPatientEnVerrouillant(CENTRE_A, PATIENT).orElseThrow();
            charge.remplacer("y", null, null, null, null, null, T0.plusSeconds(5));
            dossiers().enregistrer(charge);
            return charge;
        });
        assertThat(mis.nephropathieInitiale()).isEqualTo("y");
    }

    @Test
    void le_verrou_consultatif_d_enregistrement_tenu_par_une_autre_transaction_leve_l_ecriture_concurrente_apres_le_delai() throws Exception {
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, "dossier-medical|" + PATIENT.valeur());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> {
                    dossiers().verrouillerLEnregistrement(PATIENT);
                    return null;
                })).isInstanceOf(EcritureConcurrenteSurUnDossierMedical.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        unite().executer(() -> {
            dossiers().verrouillerLEnregistrement(PATIENT);
            return null;
        });
    }

    @BeforeEach
    void avant() {
        creer(jdbc);
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
