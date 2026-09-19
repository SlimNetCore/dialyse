package com.hemodialyse.backend.domain.medical.kdigo.service;

import com.hemodialyse.backend.domain.medical.kdigo.valueobject.AlerteSerologieKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationEgfr;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationRisqueKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.NiveauRisqueKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.StadeCkd;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le risque immunologique retient toujours la pire des deux classes PRA, jamais une moyenne ;
 * une sérologie positive sur un marqueur non listé (ex. CMV IgG, simple témoin d'exposition
 * ancienne) ne doit jamais générer d'alerte.
 */
class KdigoGreffeEvaluationPolicyTest {

    @Test
    void risqueImmunologique_should_be_faible_below_20() {
        EvaluationRisqueKdigo eval =
                KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(new BigDecimal("19.9"), new BigDecimal("5"));
        assertThat(eval.niveau()).isEqualTo(NiveauRisqueKdigo.FAIBLE);
    }

    @Test
    void risqueImmunologique_should_be_intermediaire_at_lower_bound_inclusive() {
        EvaluationRisqueKdigo eval =
                KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(new BigDecimal("20"), null);
        assertThat(eval.niveau()).isEqualTo(NiveauRisqueKdigo.INTERMEDIAIRE);
    }

    @Test
    void risqueImmunologique_should_be_eleve_above_80() {
        EvaluationRisqueKdigo eval =
                KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(new BigDecimal("10"), new BigDecimal("85"));
        assertThat(eval.niveau()).isEqualTo(NiveauRisqueKdigo.ELEVE);
    }

    @Test
    void risqueImmunologique_should_retain_worst_of_the_two_classes() {
        EvaluationRisqueKdigo eval =
                KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(new BigDecimal("5"), new BigDecimal("90"));
        assertThat(eval.valeur()).isEqualByComparingTo("90");
        assertThat(eval.niveau()).isEqualTo(NiveauRisqueKdigo.ELEVE);
    }

    @Test
    void risqueImmunologique_should_be_non_evaluable_when_both_null() {
        EvaluationRisqueKdigo eval = KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(null, null);
        assertThat(eval.niveau()).isEqualTo(NiveauRisqueKdigo.NON_EVALUABLE);
    }

    @Test
    void fonctionRenale_should_be_non_evaluable_without_creatinine() {
        EvaluationEgfr eval = KdigoGreffeEvaluationPolicy.evaluerFonctionRenale(null, 45, "M");
        assertThat(eval.stade()).isEqualTo(StadeCkd.NON_EVALUABLE);
    }

    @Test
    void fonctionRenale_should_estimate_stage_g5_for_a_typical_dialysis_patient() {
        // Créatinine élevée typique d'un patient en hémodialyse chronique.
        EvaluationEgfr eval = KdigoGreffeEvaluationPolicy.evaluerFonctionRenale(new BigDecimal("8.5"), 55, "M");
        assertThat(eval.stade()).isEqualTo(StadeCkd.G5);
        assertThat(eval.egfrMlMin173m2()).isLessThan(new BigDecimal("15"));
    }

    @Test
    void fonctionRenale_should_estimate_stage_g1_for_a_healthy_young_adult() {
        EvaluationEgfr eval = KdigoGreffeEvaluationPolicy.evaluerFonctionRenale(new BigDecimal("0.8"), 30, "F");
        assertThat(eval.stade()).isEqualTo(StadeCkd.G1);
    }

    @Test
    void serologies_should_flag_positive_vih() {
        List<AlerteSerologieKdigo> alertes =
                KdigoGreffeEvaluationPolicy.evaluerSerologies(Map.of("VIH_AC", "POSITIF"));
        assertThat(alertes).hasSize(1);
        assertThat(alertes.get(0).marqueur()).isEqualTo("VIH_AC");
    }

    @Test
    void serologies_should_not_flag_negative_results() {
        List<AlerteSerologieKdigo> alertes =
                KdigoGreffeEvaluationPolicy.evaluerSerologies(Map.of("VIH_AC", "NEGATIF"));
        assertThat(alertes).isEmpty();
    }

    @Test
    void serologies_should_not_flag_positive_cmv_igg_as_it_only_reflects_past_exposure() {
        List<AlerteSerologieKdigo> alertes =
                KdigoGreffeEvaluationPolicy.evaluerSerologies(Map.of("CMV_IGG", "POSITIF"));
        assertThat(alertes).isEmpty();
    }

    @Test
    void serologies_should_handle_empty_map() {
        assertThat(KdigoGreffeEvaluationPolicy.evaluerSerologies(Map.of())).isEmpty();
    }
}
