package com.hemodialyse.backend.domain.medical.kdigo.service;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationCible;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.StatutCible;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le test le plus important du module : chaque cible clinique doit classer correctement une
 * valeur en dessous, dans, et au-dessus de la cible KDIGO — bornes incluses — et une valeur
 * absente doit toujours être {@link StatutCible#NON_EVALUABLE}, jamais un faux « hors cible ».
 */
class KdigoEvaluationPolicyTest {

    @Test
    void hemoglobine_should_be_sous_cible_below_10() {
        EvaluationCible eval = KdigoEvaluationPolicy.evaluerHemoglobine(new BigDecimal("9.9"));
        assertThat(eval.statut()).isEqualTo(StatutCible.SOUS_CIBLE);
    }

    @Test
    void hemoglobine_should_be_dans_cible_at_lower_bound_inclusive() {
        EvaluationCible eval = KdigoEvaluationPolicy.evaluerHemoglobine(new BigDecimal("10"));
        assertThat(eval.statut()).isEqualTo(StatutCible.DANS_CIBLE);
    }

    @Test
    void hemoglobine_should_be_dans_cible_at_upper_bound_inclusive() {
        EvaluationCible eval = KdigoEvaluationPolicy.evaluerHemoglobine(new BigDecimal("11.5"));
        assertThat(eval.statut()).isEqualTo(StatutCible.DANS_CIBLE);
    }

    @Test
    void hemoglobine_should_be_au_dessus_cible_above_11_5() {
        EvaluationCible eval = KdigoEvaluationPolicy.evaluerHemoglobine(new BigDecimal("13"));
        assertThat(eval.statut()).isEqualTo(StatutCible.AU_DESSUS_CIBLE);
    }

    @Test
    void hemoglobine_should_be_non_evaluable_when_null() {
        EvaluationCible eval = KdigoEvaluationPolicy.evaluerHemoglobine(null);
        assertThat(eval.statut()).isEqualTo(StatutCible.NON_EVALUABLE);
    }

    @Test
    void ferritine_has_no_upper_bound() {
        assertThat(KdigoEvaluationPolicy.evaluerFerritine(new BigDecimal("199")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerFerritine(new BigDecimal("200")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerFerritine(new BigDecimal("5000")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
    }

    @Test
    void coefficient_saturation_transferrine_should_flag_below_20_percent() {
        assertThat(KdigoEvaluationPolicy.evaluerCoefficientSaturationTransferrine(new BigDecimal("19.9")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerCoefficientSaturationTransferrine(new BigDecimal("20")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
    }

    @Test
    void kt_v_should_flag_below_1_2() {
        assertThat(KdigoEvaluationPolicy.evaluerKtV(new BigDecimal("1.1")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerKtV(new BigDecimal("1.2")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerKtV(new BigDecimal("1.6")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
    }

    @Test
    void phosphore_should_have_both_bounds() {
        assertThat(KdigoEvaluationPolicy.evaluerPhosphore(new BigDecimal("2.4")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerPhosphore(new BigDecimal("3.5")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerPhosphore(new BigDecimal("4.6")).statut())
                .isEqualTo(StatutCible.AU_DESSUS_CIBLE);
    }

    @Test
    void calcium_should_have_both_bounds() {
        assertThat(KdigoEvaluationPolicy.evaluerCalcium(new BigDecimal("8.3")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerCalcium(new BigDecimal("9.0")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerCalcium(new BigDecimal("10.3")).statut())
                .isEqualTo(StatutCible.AU_DESSUS_CIBLE);
    }

    @Test
    void pth_should_have_both_bounds() {
        assertThat(KdigoEvaluationPolicy.evaluerPth(new BigDecimal("129")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerPth(new BigDecimal("300")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerPth(new BigDecimal("601")).statut())
                .isEqualTo(StatutCible.AU_DESSUS_CIBLE);
    }

    @Test
    void albumine_should_flag_below_4() {
        assertThat(KdigoEvaluationPolicy.evaluerAlbumine(new BigDecimal("3.9")).statut())
                .isEqualTo(StatutCible.SOUS_CIBLE);
        assertThat(KdigoEvaluationPolicy.evaluerAlbumine(new BigDecimal("4.0")).statut())
                .isEqualTo(StatutCible.DANS_CIBLE);
    }

    @Test
    void evaluation_should_carry_the_kdigo_reference_for_traceability() {
        EvaluationCible eval = KdigoEvaluationPolicy.evaluerHemoglobine(new BigDecimal("11"));
        assertThat(eval.referenceKdigo()).isEqualTo("KDIGO-2012-anemia");
        assertThat(eval.code()).isEqualTo("HEMOGLOBINE");
        assertThat(eval.unite()).isEqualTo("g/dL");
    }
}
