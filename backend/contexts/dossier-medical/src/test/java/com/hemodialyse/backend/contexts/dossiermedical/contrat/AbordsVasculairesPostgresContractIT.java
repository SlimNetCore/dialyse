package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAbordVasculaire;
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

/**
 * Contrat des abords vasculaires sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Ddossiermedical.pg16.url=jdbc:postgresql://…} (script {@code dossier-medical-dm5-pg16-rejeu.sh}).
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
class AbordsVasculairesPostgresContractIT extends AbordsVasculairesJdbcContrat {
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlatformTransactionManager transactions;

    private UniteDeTravailSpring unite() {
        return new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, Duration.ofMillis(500));
    }

    /**
     * KB-MED-62 : deux connexions. La première tient le verrou de la ligne (SELECT ... FOR UPDATE, transaction ouverte) ; un remplacement
     * par une seconde connexion attend {@code lock_timeout} puis lève l'écriture concurrente (SQLSTATE 55P03 vu par l'adapter réel) ; une
     * fois le verrou libéré, le remplacement aboutit.
     */
    @Test
    void une_seconde_ecriture_sur_un_abord_verrouille_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        var a = abord(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0);
        abords().ajouter(a);
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT id FROM abords_vasculaires WHERE id = ? FOR UPDATE")) {
                tient.setObject(1, a.id());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> abords().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id())))
                        .isInstanceOf(EcritureConcurrenteSurUnAbordVasculaire.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        var remplace = unite().executer(() -> {
            var chargee = abords().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
            chargee.remplacer("PTFE", null, null, LocalDate.of(2026, 3, 1), null, true, null, T0.plusSeconds(60));
            abords().enregistrerLeRemplacement(chargee);
            return chargee;
        });
        assertThat(remplace.type()).isEqualTo("PTFE");
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
