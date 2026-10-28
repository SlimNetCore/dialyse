package com.hemodialyse.backend.contexts.absencespatients.infrastructure;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.legacycouverture.ForfaitsDesPatientsJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Règle de choix du forfait du patient (KB-ABS-16, reproduite du legacy) : effectif prioritaire même hors période, prise en
 * charge couvrante d'abord, repli sur la plus récente portant un forfait sans condition de date, forfait d'un autre centre ignoré.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:absences-acl-forfaits;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ForfaitsDesPatientsJdbcTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0x9000, 1));
    private static final CentreId AUTRE = new CentreId(new UUID(0x9000, 2));
    private static final PatientId PATIENT = new PatientId(new UUID(0x9001, 1));
    private static final UUID F_HD = new UUID(0x9002, 1);
    private static final UUID F_PREMIUM = new UUID(0x9002, 2);
    private static final UUID F_AUTRE = new UUID(0x9002, 3);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @Autowired
    private JdbcTemplate jdbc;

    private ForfaitsDesPatientsJdbc forfaits() {
        return new ForfaitsDesPatientsJdbc(jdbc);
    }

    @BeforeEach
    void preparer() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS forfait (id UUID PRIMARY KEY, center_id UUID NOT NULL, libelle VARCHAR(255), prix DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS prise_en_charge (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_debut_demande DATE, date_fin_demande DATE, date_debut_effectif DATE, date_fin_effectif DATE, "
                + "forfait_demande_id UUID, forfait_effectif_id UUID, created_at TIMESTAMP)");
        jdbc.update("DELETE FROM prise_en_charge");
        jdbc.update("DELETE FROM forfait");
        forfait(F_HD, CENTRE, "Forfait HD", "10000.00");
        forfait(F_PREMIUM, CENTRE, "Forfait Premium", "15000.00");
        forfait(F_AUTRE, AUTRE, "Forfait autre centre", "1.00");
    }

    private void forfait(UUID id, CentreId centre, String libelle, String prix) {
        jdbc.update("INSERT INTO forfait (id, center_id, libelle, prix) VALUES (?, ?, ?, ?)", id, centre.valeur(), libelle, new BigDecimal(prix));
    }

    private void pec(String debutEffectif, String finEffectif, String debutDemande, UUID demande, UUID effectif, int rang) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_debut_effectif, date_fin_effectif, "
                        + "forfait_demande_id, forfait_effectif_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), CENTRE.valeur(), PATIENT.valeur(), debutDemande == null ? null : Date.valueOf(debutDemande),
                debutEffectif == null ? null : Date.valueOf(debutEffectif), finEffectif == null ? null : Date.valueOf(finEffectif),
                demande, effectif, Timestamp.valueOf("2026-01-" + String.format("%02d", rang) + " 08:00:00"));
    }

    @Test
    void sans_prise_en_charge_aucun_forfait() {
        assertThat(forfaits().applicable(CENTRE, PATIENT, JOUR)).isEmpty();
    }

    @Test
    void le_forfait_effectif_de_la_prise_en_charge_couvrante_prime_sur_le_demande() {
        pec("2020-01-01", null, null, F_PREMIUM, F_HD, 1);

        var forfait = forfaits().applicable(CENTRE, PATIENT, JOUR).orElseThrow();

        assertThat(forfait.libelle()).isEqualTo("Forfait HD");
        assertThat(forfait.prixHt()).isEqualByComparingTo("10000.00");
    }

    @Test
    void a_egalite_la_prise_en_charge_la_plus_recemment_creee_gagne() {
        pec("2020-01-01", null, null, null, F_HD, 1);
        pec("2020-01-01", null, null, null, F_PREMIUM, 9);

        assertThat(forfaits().applicable(CENTRE, PATIENT, JOUR).orElseThrow().libelle()).isEqualTo("Forfait Premium");
    }

    @Test
    void sans_prise_en_charge_couvrante_repli_sur_la_plus_recente_portant_un_forfait_sans_condition_de_date() {
        pec("2020-01-01", "2020-12-31", null, null, F_HD, 1);
        pec(null, null, null, null, null, 9);

        assertThat(forfaits().applicable(CENTRE, PATIENT, JOUR).orElseThrow().libelle()).isEqualTo("Forfait HD");
    }

    @Test
    void les_bornes_de_la_periode_sont_incluses_et_la_periode_demandee_couvre_aussi() {
        pec("2020-01-01", "2026-06-15", null, null, F_HD, 1);
        pec(null, null, "2026-06-16", F_PREMIUM, null, 9);

        assertThat(forfaits().applicable(CENTRE, PATIENT, JOUR).orElseThrow().libelle()).isEqualTo("Forfait HD");
        assertThat(forfaits().applicable(CENTRE, PATIENT, JOUR.plusDays(1)).orElseThrow().libelle()).isEqualTo("Forfait Premium");
    }

    @Test
    void un_forfait_d_un_autre_centre_est_ignore() {
        pec("2020-01-01", null, null, null, F_AUTRE, 1);

        assertThat(forfaits().applicable(CENTRE, PATIENT, JOUR)).isEmpty();
    }
}
