package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision.Analyse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R30 : coût de possession, ratio de maintenance (4 décimales), coût par heure d'indisponibilité, réforme recommandée. */
class AnalyseDeDecisionTest {

    private final AnalyseDeDecision analyse = new AnalyseDeDecision(new BigDecimal("0.60"));

    private static BigDecimal d(String valeur) {
        return valeur == null ? null : new BigDecimal(valeur);
    }

    private Analyse analyser(StatutEquipement statut, String prix, String cumule, String periode, double heures) {
        return analyse.analyser(statut, d(prix), d(cumule), d(periode), heures);
    }

    @Test
    void should_compute_the_possession_cost_the_ratio_and_the_cost_per_unavailable_hour() {
        Analyse a = analyser(StatutEquipement.EN_SERVICE, "1000000", "300000", "100000", 50);

        assertThat(a.coutPossession()).isEqualByComparingTo("1300000");
        assertThat(a.ratioMaintenance()).isEqualByComparingTo("0.3000");
        assertThat(a.ratioMaintenance().scale()).isEqualTo(4);
        assertThat(a.coutParHeureIndisponibilite()).isEqualByComparingTo("2000");
        assertThat(a.coutParHeureIndisponibilite().scale()).isEqualTo(2);
        assertThat(a.reformeRecommandee()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1.00,3.00,0.3333", "2.00,3.00,0.6667", "0.01,100000.00,0.0000", "125.00,100.00,1.2500",
            "1.00,8.00,0.1250", "0.00005,1.00,0.0001", "0.00004,1.00,0.0000"})
    void should_round_the_displayed_ratio_to_four_decimals_half_up(String cumule, String prix, String attendu) {
        assertThat(analyser(StatutEquipement.EN_SERVICE, prix, cumule, "0", 0).ratioMaintenance()).isEqualTo(d(attendu));
    }

    @ParameterizedTest
    @CsvSource({"599.94,false", "599.95,false", "599.99,false", "599.999999,false", "600.00,true", "600.01,true", "5000,true"})
    void should_compare_the_threshold_to_the_exact_ratio_and_not_to_the_rounded_one(String cumule, boolean recommandee) {
        // KB-GMAO-72 corrigé : 0,59995 s'affiche 0,6000 mais reste sous le seuil de 0,60
        Analyse a = analyser(StatutEquipement.EN_SERVICE, "1000.00", cumule, "0", 0);

        assertThat(a.reformeRecommandee()).isEqualTo(recommandee);
    }

    @Test
    void should_show_a_rounded_ratio_of_0_6000_while_not_recommending_the_reform_below_the_threshold() {
        Analyse a = analyser(StatutEquipement.EN_SERVICE, "1000.00", "599.96", "0", 0);

        assertThat(a.ratioMaintenance()).isEqualTo(d("0.6000"));
        assertThat(a.reformeRecommandee()).isFalse();
    }

    @Test
    void should_recommend_the_reform_when_the_ratio_equals_the_threshold() {
        Analyse a = analyser(StatutEquipement.EN_SERVICE, "1000000", "600000", "0", 0);

        assertThat(a.reformeRecommandee()).isTrue();
        assertThat(a.coutParHeureIndisponibilite()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"0.00", "-10.00"})
    void should_leave_the_ratio_unknown_and_never_recommend_for_a_zero_or_negative_price(String prix) {
        Analyse a = analyser(StatutEquipement.EN_SERVICE, prix, "999.00", "0", 0);

        assertThat(a.ratioMaintenance()).isNull();
        assertThat(a.reformeRecommandee()).isFalse();
        assertThat(a.coutPossession()).isEqualByComparingTo(d(prix).add(d("999.00")));
    }

    @Test
    void should_leave_the_ratio_unknown_when_the_price_is_unknown() {
        Analyse a = analyser(StatutEquipement.EN_SERVICE, null, "5000", "0", 0);

        assertThat(a.ratioMaintenance()).isNull();
        assertThat(a.reformeRecommandee()).isFalse();
        assertThat(a.coutPossession()).isEqualByComparingTo("5000");
    }

    @Test
    void should_always_recommend_the_reform_of_an_equipment_to_retire() {
        assertThat(analyser(StatutEquipement.A_REFORMER, null, "0", "0", 0).reformeRecommandee()).isTrue();
        assertThat(analyser(StatutEquipement.A_REFORMER, "1000.00", "100.00", "0", 0).reformeRecommandee()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"REFORME", "DESACTIF"})
    void should_never_recommend_the_reform_of_a_retired_or_deactivated_equipment(StatutEquipement statut) {
        assertThat(analyser(statut, "100.00", "500.00", "0", 0).reformeRecommandee()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE"})
    void should_recommend_the_reform_of_an_active_equipment_over_the_threshold(StatutEquipement statut) {
        assertThat(analyser(statut, "1000.00", "700.00", "0", 0).reformeRecommandee()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"100.00,3.0,33.33", "100.00,7.0,14.29", "100.00,0.016666666666666666,6000.00", "0.005,1.0,0.01",
            "0.004,1.0,0.00"})
    void should_round_the_cost_per_hour_half_up_to_two_decimals(String periode, double heures, String attendu) {
        assertThat(analyser(StatutEquipement.EN_SERVICE, "1000", "0", periode, heures).coutParHeureIndisponibilite())
                .isEqualTo(d(attendu));
    }

    @Test
    void should_have_no_cost_per_hour_without_unavailable_time() {
        assertThat(analyser(StatutEquipement.EN_SERVICE, "1000", "0", "100", 0).coutParHeureIndisponibilite()).isNull();
    }

    @Test
    void should_treat_missing_costs_as_zero() {
        Analyse a = analyse.analyser(StatutEquipement.EN_SERVICE, d("1000"), null, null, 2);

        assertThat(a.coutPossession()).isEqualByComparingTo("1000");
        assertThat(a.ratioMaintenance()).isEqualByComparingTo("0");
        assertThat(a.coutParHeureIndisponibilite()).isEqualByComparingTo("0");
    }

    @Test
    void should_reject_a_threshold_that_is_not_strictly_positive() {
        assertThatThrownBy(() -> new AnalyseDeDecision(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalyseDeDecision(new BigDecimal("-0.1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalyseDeDecision(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
