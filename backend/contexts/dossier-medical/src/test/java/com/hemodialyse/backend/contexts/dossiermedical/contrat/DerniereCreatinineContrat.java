package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la dernière créatinine publiée (SE7f, ADR 0100 ; KB-SEA-96 reproduit) : joué contre le double en mémoire, H2 et PostgreSQL 16.
 * Mêmes cas que le golden {@code characterization/seance/se7f/analyses}.
 */
abstract class DerniereCreatinineContrat {

    static final UUID CENTRE_A = new UUID(0, 0xA);
    static final UUID CENTRE_B = new UUID(0, 0xB);
    static final UUID PATIENT = new UUID(1, 1);
    static final LocalDate D = LocalDate.of(2026, 1, 1);

    protected abstract DerniereCreatininePubliee creatinine();

    /** Pose une analyse (créatinine seule, nulle possible) du patient dans le centre à la date. */
    protected abstract void analyse(UUID centre, LocalDate prelevement, String creatinine);

    @Test
    void sans_analyse_il_n_y_a_pas_de_creatinine() {
        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).isEmpty();
    }

    @Test
    void la_plus_recente_du_centre_est_retenue_et_une_analyse_plus_recente_d_un_autre_centre_est_ignoree() {
        analyse(CENTRE_A, D.plusDays(10), "1.0");
        analyse(CENTRE_B, D.plusDays(40), "9.0");
        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("1.0");

        analyse(CENTRE_A, D.plusDays(20), "9.0");
        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("9.0");
        assertThat(creatinine().duPatient(CENTRE_B, PATIENT)).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("9.0");
    }

    @Test
    void une_analyse_sans_creatinine_est_ignoree_meme_plus_recente() {
        analyse(CENTRE_A, D.plusDays(20), "9.0");
        analyse(CENTRE_A, D.plusDays(30), null);

        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("9.0");
    }

    /** KB-SEA-96, reproduit : une créatinine à 0 est une valeur, elle masque les mesures valides antérieures. */
    @Test
    void une_creatinine_a_zero_plus_recente_est_retenue() {
        analyse(CENTRE_A, D.plusDays(20), "9.0");
        analyse(CENTRE_A, D.plusDays(35), "0");

        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("0");
    }

    /** KB-SEA-96, reproduit : à date égale, l'une des deux (ordre du SGBD, non imposé). */
    @Test
    void a_date_egale_l_une_des_deux_creatinines_est_retenue() {
        analyse(CENTRE_A, D.plusDays(60), "1.0");
        analyse(CENTRE_A, D.plusDays(60), "9.0");

        BigDecimal retenue = creatinine().duPatient(CENTRE_A, PATIENT).orElseThrow();
        assertThat(retenue.compareTo(new BigDecimal("1.0")) == 0 || retenue.compareTo(new BigDecimal("9.0")) == 0).isTrue();
    }

    @Test
    void un_autre_patient_du_centre_n_est_pas_lu() {
        analyse(CENTRE_A, D.plusDays(10), "1.0");
        assertThat(creatinine().duPatient(CENTRE_A, new UUID(1, 2))).isEmpty();
    }
}
