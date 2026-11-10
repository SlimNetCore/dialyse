package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la dernière créatinine publiée (SE7f, ADR 0100 ; KB-SEA-96 : départage déterministe corrigé, créatinine à 0 reproduite et close
 * sans changement, ADR 0117) : joué contre le double en mémoire, H2 et PostgreSQL 16. Mêmes cas que le golden
 * {@code characterization/seance/se7f/analyses}.
 */
abstract class DerniereCreatinineContrat {

    static final UUID CENTRE_A = new UUID(0, 0xA);
    static final UUID CENTRE_B = new UUID(0, 0xB);
    static final UUID PATIENT = new UUID(1, 1);
    static final LocalDate D = LocalDate.of(2026, 1, 1);
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract DerniereCreatininePubliee creatinine();

    /** Pose une analyse (créatinine seule, nulle possible) du patient dans le centre à la date, d'identifiant et de création donnés. */
    protected abstract void analyse(UUID id, UUID centre, LocalDate prelevement, String creatinine, Instant creeLe);

    protected void analyse(UUID centre, LocalDate prelevement, String creatinine) {
        analyse(UUID.randomUUID(), centre, prelevement, creatinine, T0);
    }

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

    /**
     * KB-SEA-96 (b), reproduit et CLOS sans changement (ADR 0117) : une créatinine à 0 est une valeur, elle masque les mesures valides
     * antérieures ; le domaine la juge non évaluable ({@code EvaluationKdigoDuCandidat}), ce qui ne produit aucun stade faux. Refuser le 0 à la
     * saisie est une décision médicale, à valider avant le 2026-12-15.
     */
    @Test
    void une_creatinine_a_zero_plus_recente_est_retenue() {
        analyse(CENTRE_A, D.plusDays(20), "9.0");
        analyse(CENTRE_A, D.plusDays(35), "0");

        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("0");
    }

    /**
     * KB-SEA-96 (a) corrigé le 2026-11-10 (ADR 0117) : à date de prélèvement égale, la dernière saisie fait foi (création la plus récente,
     * création inconnue en dernier, puis identifiant le plus grand), quel que soit l'ordre d'insertion ; une analyse sans créatinine est sautée.
     * À confirmer par les médecins (alternative : la créatinine la plus haute, prudence clinique).
     */
    @Test
    void a_date_egale_la_derniere_saisie_fait_foi_quel_que_soit_l_ordre_d_insertion() {
        analyse(new UUID(5, 1), CENTRE_A, D.plusDays(60), "9.0", T0);
        analyse(new UUID(5, 2), CENTRE_A, D.plusDays(60), "1.0", T0.plusSeconds(3600));
        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(InstanceOfAssertFactories.BIG_DECIMAL)
                .as("créée plus tard").isEqualByComparingTo("1.0");

        analyse(new UUID(5, 3), CENTRE_A, D.plusDays(60), "2.0", null);
        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(InstanceOfAssertFactories.BIG_DECIMAL)
                .as("création inconnue en dernier").isEqualByComparingTo("1.0");

        analyse(new UUID(5, 4), CENTRE_A, D.plusDays(60), null, T0.plusSeconds(7200));
        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(InstanceOfAssertFactories.BIG_DECIMAL)
                .as("la saisie la plus récente est sans créatinine : sautée").isEqualByComparingTo("1.0");
    }

    @Test
    void a_date_et_creation_egales_l_identifiant_le_plus_grand_departage() {
        analyse(new UUID(5, 7), CENTRE_A, D.plusDays(61), "7.0", T0);
        analyse(new UUID(5, 9), CENTRE_A, D.plusDays(61), "9.0", T0);
        analyse(new UUID(5, 8), CENTRE_A, D.plusDays(61), "8.0", T0);

        assertThat(creatinine().duPatient(CENTRE_A, PATIENT)).get().asInstanceOf(InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("9.0");
    }

    @Test
    void un_autre_patient_du_centre_n_est_pas_lu() {
        analyse(CENTRE_A, D.plusDays(10), "1.0");
        assertThat(creatinine().duPatient(CENTRE_A, new UUID(1, 2))).isEmpty();
    }
}
