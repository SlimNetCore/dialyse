package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.transaction.UniteDeTravailSpring;
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
 * Contrat des séances sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Dseance.pg16.url=jdbc:postgresql://…} (script {@code seance-se1-pg16-rejeu.sh}, mot de passe par la variable
 * d'environnement {@code SEANCE_PG16_PASSWORD}).
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
class SeancesPostgresContractIT extends SeancesJdbcContrat {
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
     * KB-SEA-02 : deux connexions. La première tient le verrou consultatif du (centre, patient, jour) dans une transaction ouverte ; un
     * scan concurrent attend {@code lock_timeout} puis lève l'écriture concurrente (SQLSTATE 55P03 vu par l'adapter réel, 409) ; une fois
     * le verrou libéré, le second scan l'obtient.
     */
    @Test
    void un_second_scan_du_meme_jour_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        String cle = "seance-du-jour|" + CENTRE_A.valeur() + "|" + PATIENT.valeur() + "|" + JOUR;
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle);
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> {
                    new SeancesJdbc(jdbc).verrouillerLeJour(CENTRE_A, PATIENT, JOUR);
                    return null;
                })).isInstanceOf(EcritureConcurrente.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        assertThat(unite().executer(() -> {
            new SeancesJdbc(jdbc).verrouillerLeJour(CENTRE_A, PATIENT, JOUR);
            return "obtenu";
        })).isEqualTo("obtenu");
    }

    @Test
    void le_verrou_d_un_autre_jour_ou_d_un_autre_patient_n_attend_pas() throws Exception {
        String cle = "seance-du-jour|" + CENTRE_A.valeur() + "|" + PATIENT.valeur() + "|" + JOUR;
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle);
                tient.executeQuery();
                unite().executer(() -> {
                    new SeancesJdbc(jdbc).verrouillerLeJour(CENTRE_A, PATIENT, JOUR.plusDays(1));
                    new SeancesJdbc(jdbc).verrouillerLeJour(CENTRE_A, AUTRE_PATIENT, JOUR);
                    return null;
                });
            } finally {
                verrou.rollback();
            }
        }
    }

    /**
     * SE7a : la saisie du volet médical (cas d'usage réel sur les adapters réels) pendant qu'une autre connexion tient le verrou de la
     * séance : elle attend le délai puis lève l'écriture concurrente (409 à la route) et n'écrit AUCUN volet.
     */
    @Test
    void la_saisie_du_volet_medical_attend_le_verrou_de_la_seance_puis_leve_l_ecriture_concurrente_sans_rien_ecrire() throws Exception {
        nouveauBanc().seances().ajouter(Seance.creer(s(1), CENTRE_A, PATIENT, JOUR, T0));
        var session = new com.hemodialyse.backend.contexts.seance.application.SessionCourante() {
            public com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre(java.util.UUID demande) {
                return CENTRE_A;
            }

            public String utilisateur() {
                return "u-medecin";
            }

            public boolean estAdministrateur() {
                return false;
            }

            public boolean valideAuScan() {
                return false;
            }
        };
        var cas = new com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletMedical(session, new SeancesJdbc(jdbc),
                new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsMedicauxJdbc(jdbc), unite(),
                java.util.UUID::randomUUID, com.hemodialyse.backend.contexts.seance.application.HorlogeDuCentre.enUtc(
                java.time.Clock.fixed(T0, java.time.ZoneOffset.UTC)));
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT id FROM seances WHERE id = ? FOR UPDATE")) {
                tient.setObject(1, s(1).valeur());
                tient.executeQuery();
                assertThatThrownBy(() -> cas.executer(CENTRE_A.valeur(), s(1).valeur(), () -> new com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales("x", null, null, null, null, null)))
                        .isInstanceOf(EcritureConcurrente.class);
            } finally {
                verrou.rollback();
            }
        }
        assertThat(new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsMedicauxJdbc(jdbc).deLaSeance(CENTRE_A, s(1))).isEmpty();
    }

    /**
     * SE5 : deux impositions de forfait simultanées sur la même séance. La première tient le verrou de la ligne ({@code FOR UPDATE}) ; la
     * seconde attend le délai puis lève l'écriture concurrente (409) au lieu d'écraser en silence ; verrou libéré, elle l'obtient.
     */
    @Test
    void une_seconde_imposition_de_forfait_attend_le_verrou_de_la_ligne_puis_leve_l_ecriture_concurrente() throws Exception {
        nouveauBanc().seances().ajouter(Seance.creer(s(1), CENTRE_A, PATIENT, JOUR, T0));
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT id FROM seances WHERE id = ? FOR UPDATE")) {
                tient.setObject(1, s(1).valeur());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> new SeancesJdbc(jdbc).trouverPourModifier(CENTRE_A, s(1))))
                        .isInstanceOf(EcritureConcurrente.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        assertThat(unite().executer(() -> new SeancesJdbc(jdbc).trouverPourModifier(CENTRE_A, s(1)))).isPresent();
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
