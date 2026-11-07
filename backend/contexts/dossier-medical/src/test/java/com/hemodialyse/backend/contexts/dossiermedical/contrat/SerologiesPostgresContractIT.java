package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
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
 * Contrat des sérologies sur un PostgreSQL 16 RÉEL (schéma Flyway V1..Vn). Base JETABLE uniquement :
 * {@code -Ddossiermedical.pg16.url=jdbc:postgresql://…} (script {@code dossier-medical-dm4-pg16-rejeu.sh}).
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
class SerologiesPostgresContractIT extends SerologiesJdbcContrat {

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
     * KB-MED-49 / 56 : deux connexions. La première tient le verrou de la ligne (SELECT ... FOR UPDATE, transaction ouverte) ; une
     * correction par une seconde connexion attend {@code lock_timeout} puis lève l'écriture concurrente (SQLSTATE 55P03 vu par
     * l'adapter réel) ; une fois le verrou libéré, la même lecture réussit.
     */
    @Test
    void une_seconde_ecriture_sur_une_serologie_verrouillee_attend_le_delai_puis_leve_l_ecriture_concurrente() throws Exception {
        var s = serologie(CENTRE_A, PATIENT, MarqueurSerologique.AG_HBS, LocalDate.of(2026, 1, 3), T0);
        serologies().ajouter(s);
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT id FROM serologies_patient WHERE id = ? FOR UPDATE")) {
                tient.setObject(1, s.id());
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id())))
                        .isInstanceOf(EcritureConcurrenteSurUneSerologie.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        var libre = unite().executer(() -> {
            var chargee = serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id()).orElseThrow();
            chargee.corrigerLeResultat(ResultatSerologique.NEGATIF, null, T0.plusSeconds(60));
            serologies().enregistrerLaCorrection(chargee);
            return chargee;
        });
        assertThat(libre.resultat()).isEqualTo(ResultatSerologique.NEGATIF);
    }

    /**
     * KB-MED-56 : deux enregistrements simultanés du même triplet (patient, marqueur, date) se suivent. La première connexion tient le
     * verrou consultatif de transaction ; la seconde attend {@code lock_timeout} puis lève l'écriture concurrente (409) ; un autre
     * triplet n'attend pas ; une fois le verrou libéré, la vérification d'unicité aboutit.
     */
    @Test
    void deux_enregistrements_du_meme_triplet_se_suivent_et_le_second_obtient_l_ecriture_concurrente() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 3);
        String cle = "serologie|" + CENTRE_A.valeur() + "|" + PATIENT.valeur() + "|" + MarqueurSerologique.TPHA.name() + "|" + date;
        try (Connection verrou = dataSource.getConnection()) {
            verrou.setAutoCommit(false);
            try (PreparedStatement tient = verrou.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                tient.setString(1, cle);
                tient.executeQuery();
                long debut = System.nanoTime();
                assertThatThrownBy(() -> unite().executer(() -> serologies().existeEnVerrouillant(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, date)))
                        .isInstanceOf(EcritureConcurrenteSurUneSerologie.class);
                assertThat(Duration.ofNanos(System.nanoTime() - debut)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
                long autre = System.nanoTime();
                assertThat(unite().executer(() -> serologies().existeEnVerrouillant(CENTRE_A, PATIENT, MarqueurSerologique.AG_HBS, date))).isFalse();
                assertThat(Duration.ofNanos(System.nanoTime() - autre)).isLessThan(Duration.ofMillis(400));
            } finally {
                verrou.rollback();
            }
        }
        assertThat(unite().executer(() -> serologies().existeEnVerrouillant(CENTRE_A, PATIENT, MarqueurSerologique.TPHA, date))).isFalse();
    }

    @Test
    void un_titre_a_quatre_decimales_est_arrondi_a_trois_et_les_bornes_de_colonne_sont_celles_du_domaine() {
        var s = com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie.enregistrer(java.util.UUID.randomUUID(), PATIENT, CENTRE_A,
                MarqueurSerologique.TPHA, ResultatSerologique.NEGATIF, new java.math.BigDecimal("9999999.9994"), "x".repeat(20), AUJOURDHUI,
                AUJOURDHUI, "y".repeat(255), null, null, T0);
        serologies().ajouter(s);
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, s.id()).orElseThrow().titre()).isEqualByComparingTo("9999999.999");
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
