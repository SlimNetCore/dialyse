package com.hemodialyse.backend.contexts.absencespatients.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodeDeDetectionTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 22);

    @Test
    void la_fenetre_de_la_nuit_va_de_J_moins_7_a_hier_sans_aujourd_hui() {
        PeriodeDeDetection periode = PeriodeDeDetection.recente(AUJOURDHUI, 7);

        assertThat(periode.du()).isEqualTo(AUJOURDHUI.minusDays(7));
        assertThat(periode.au()).isEqualTo(AUJOURDHUI.minusDays(1));
        assertThat(periode.jours()).hasSize(7).doesNotContain(AUJOURDHUI);
        assertThat(periode.finRamenee()).isFalse();
    }

    @Test
    void une_fenetre_recente_a_au_moins_un_jour_et_au_plus_366() {
        assertThatThrownBy(() -> PeriodeDeDetection.recente(AUJOURDHUI, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeriodeDeDetection.recente(AUJOURDHUI, 367)).isInstanceOf(IllegalArgumentException.class);
        assertThat(PeriodeDeDetection.recente(AUJOURDHUI, 1).jours()).containsExactly(AUJOURDHUI.minusDays(1));
        assertThat(PeriodeDeDetection.recente(AUJOURDHUI, 366).jours()).hasSize(366);
    }

    @Test
    void une_fin_posterieure_a_hier_est_ramenee_a_hier_et_le_signale() {
        PeriodeDeDetection periode = PeriodeDeDetection.demandee(AUJOURDHUI.minusDays(5), AUJOURDHUI.plusDays(3), AUJOURDHUI);

        assertThat(periode.au()).isEqualTo(AUJOURDHUI.minusDays(1));
        assertThat(periode.finRamenee()).isTrue();
    }

    @Test
    void une_fin_hier_ou_avant_n_est_pas_ramenee() {
        assertThat(PeriodeDeDetection.demandee(AUJOURDHUI.minusDays(5), AUJOURDHUI.minusDays(1), AUJOURDHUI).finRamenee()).isFalse();
        assertThat(PeriodeDeDetection.demandee(AUJOURDHUI.minusDays(5), AUJOURDHUI.minusDays(2), AUJOURDHUI).au())
                .isEqualTo(AUJOURDHUI.minusDays(2));
    }

    @Test
    void un_debut_aujourd_hui_ou_futur_est_refuse_meme_si_la_fin_est_future() {
        assertThatThrownBy(() -> PeriodeDeDetection.demandee(AUJOURDHUI, AUJOURDHUI.plusDays(4), AUJOURDHUI))
                .isInstanceOfSatisfying(RegleDAbsenceViolee.class, e -> assertThat(e.code()).isEqualTo("ABSENCE_PERIODE_INVALIDE"));
        assertThatThrownBy(() -> PeriodeDeDetection.demandee(AUJOURDHUI.plusDays(1), AUJOURDHUI.plusDays(2), AUJOURDHUI))
                .isInstanceOf(RegleDAbsenceViolee.class);
    }

    @Test
    void un_debut_apres_la_fin_est_refuse() {
        assertThatThrownBy(() -> PeriodeDeDetection.demandee(AUJOURDHUI.minusDays(2), AUJOURDHUI.minusDays(3), AUJOURDHUI))
                .isInstanceOfSatisfying(RegleDAbsenceViolee.class, e -> assertThat(e.code()).isEqualTo("ABSENCE_PERIODE_INVALIDE"));
    }

    @Test
    void la_duree_maximale_est_de_366_jours_inclus() {
        LocalDate hier = AUJOURDHUI.minusDays(1);

        assertThat(PeriodeDeDetection.demandee(hier.minusDays(365), hier, AUJOURDHUI).jours()).hasSize(366);
        assertThatThrownBy(() -> PeriodeDeDetection.demandee(hier.minusDays(366), hier, AUJOURDHUI))
                .isInstanceOfSatisfying(RegleDAbsenceViolee.class, e -> assertThat(e.code()).isEqualTo("ABSENCE_PERIODE_TROP_LONGUE"));
    }

    @Test
    void une_periode_a_un_debut_et_une_fin() {
        assertThatThrownBy(() -> new PeriodeDeDetection(null, AUJOURDHUI, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PeriodeDeDetection(AUJOURDHUI, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PeriodeDeDetection(AUJOURDHUI, AUJOURDHUI.minusDays(1), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
