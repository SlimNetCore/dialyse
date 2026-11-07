package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.SerologiesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PREUVE TRANSACTIONNELLE RÉELLE (H2, vraie transaction Spring, adapter JDBC) : la sérologie et la réévaluation de la place du patient
 * sont deux écritures d'une même transaction. Un refus de la Planification (patient inconnu) ou une défaillance après l'écriture ne
 * laisse AUCUNE sérologie, y compris pour un résultat négatif ; la correction est annulée de la même façon ; sans refus tout est
 * enregistré. La suppression d'une ligne absente n'écrit rien.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-serologie-transaction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SerologieEtReevaluationTransactionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d4a1"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000d4001");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS serologies_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, conduite_a_tenir TEXT, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_prelevement DATE NOT NULL, date_prochain_controle DATE, "
                + "laboratoire VARCHAR(255), marqueur VARCHAR(15) NOT NULL, patient_id UUID NOT NULL, resultat VARCHAR(15) NOT NULL, "
                + "titre NUMERIC(10, 3), unite VARCHAR(20), updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.update("DELETE FROM serologies_patient");
    }

    @AfterEach
    void apres() {
        jdbc.update("DELETE FROM serologies_patient");
    }

    private IsolementDesPatients isolement(RuntimeException refus) {
        return new IsolementDesPatients() {
            @Override
            public void verrouillerLesPlaces(CentreId centre, PatientId patient) {
            }

            @Override
            public void reevaluer(CentreId centre, PatientId patient) {
                if (refus != null) throw refus;
            }
        };
    }

    private EnregistrerUneSerologie enregistrer(RuntimeException refus) {
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, null);
        return new EnregistrerUneSerologie(demande -> CENTRE, new SerologiesJdbc(jdbc), isolement(refus), unite, UUID::randomUUID,
                HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC)));
    }

    private int compter() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM serologies_patient", Integer.class);
    }

    @Test
    void un_refus_de_la_planification_annule_la_serologie_meme_negative() {
        var refus = new IllegalStateException("PLACEMENT_PATIENT_INTROUVABLE");
        assertThatThrownBy(() -> enregistrer(refus).executer(null, PATIENT, "TPHA", "NEGATIF", null, null, null, null, null, null)).isSameAs(refus);
        assertThat(compter()).isZero();
    }

    @Test
    void une_serologie_acceptee_est_enregistree_avec_la_reevaluation() {
        var s = enregistrer(null).executer(null, PATIENT, "VIH_AC", "POSITIF", null, null, null, null, null, "Isoler");
        assertThat(compter()).isEqualTo(1);
        assertThat(s.datePrelevement()).hasToString("2026-01-10");
    }

    @Test
    void un_refus_a_la_correction_annule_la_correction() {
        var s = enregistrer(null).executer(null, PATIENT, "VIH_AC", "POSITIF", null, null, null, null, null, "Isoler");
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, null);
        var refus = new IllegalStateException("PLACEMENT_PATIENT_INTROUVABLE");
        var corriger = new CorrigerUneSerologie(demande -> CENTRE, new SerologiesJdbc(jdbc), isolement(refus), unite,
                HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-11T10:00:00Z"), ZoneOffset.UTC)));
        assertThatThrownBy(() -> corriger.executer(null, PATIENT, s.id(), "NEGATIF", null)).isSameAs(refus);
        assertThat(jdbc.queryForObject("SELECT resultat FROM serologies_patient WHERE id = ?", String.class, s.id())).isEqualTo("POSITIF");
    }

    @Test
    void supprimer_une_serologie_absente_ne_supprime_rien_et_le_dit() {
        var s = enregistrer(null).executer(null, PATIENT, "TPHA", "NEGATIF", null, null, null, null, null, null);
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, null);
        var supprimer = new SupprimerUneSerologie(demande -> CENTRE, new SerologiesJdbc(jdbc), unite);
        assertThat(supprimer.executer(null, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(compter()).isEqualTo(1);
        assertThat(supprimer.executer(null, PATIENT, s.id())).isTrue();
        assertThat(compter()).isZero();
    }
}
