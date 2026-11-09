package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Répartition face à la cible : masque sous le seuil d'anonymat, taux face à la cible. */
class RepartitionFaceALaCibleTest {

    @Test
    void sous_le_seuil_d_anonymat_tout_est_masque() {
        RepartitionFaceALaCible r = RepartitionFaceALaCible.de(List.of(new BigDecimal("1.3"), new BigDecimal("1.0")), CiblesKdigo.KT_V);
        assertThat(r.evalues()).isNull();
        assertThat(r.pctDansCible()).isNull();
        assertThat(r.pctSousCible()).isNull();
        assertThat(r.pctAuDessus()).isNull();
    }

    @Test
    void zero_patient_evalue_reste_zero_sans_taux() {
        RepartitionFaceALaCible r = RepartitionFaceALaCible.de(List.of(), CiblesKdigo.KT_V);
        assertThat(r.evalues()).isZero();
        assertThat(r.pctDansCible()).isNull();
    }

    @Test
    void les_taux_suivent_la_cible_kdigo_a_une_decimale() {
        List<BigDecimal> values = List.of(new BigDecimal("1.3"), new BigDecimal("1.2"), new BigDecimal("1.5"), new BigDecimal("1.1"),
                new BigDecimal("0.9"));
        RepartitionFaceALaCible r = RepartitionFaceALaCible.de(values, CiblesKdigo.KT_V);
        assertThat(r.evalues()).isEqualTo(5L);
        assertThat(r.pctDansCible()).isEqualTo(new BigDecimal("60.0"));
        assertThat(r.pctSousCible()).isEqualTo(new BigDecimal("40.0"));
        assertThat(r.pctAuDessus()).isEqualTo(new BigDecimal("0.0"));
    }

    @Test
    void l_effectif_masque_remplace_1_a_4_par_null_et_laisse_zero_et_5() {
        assertThat(EffectifMasque.masquer(0)).isZero();
        assertThat(EffectifMasque.masquer(1)).isNull();
        assertThat(EffectifMasque.masquer(4)).isNull();
        assertThat(EffectifMasque.masquer(5)).isEqualTo(5L);
        assertThat(EffectifMasque.sousLeSeuil(4)).isTrue();
        assertThat(EffectifMasque.sousLeSeuil(5)).isFalse();
    }
}
