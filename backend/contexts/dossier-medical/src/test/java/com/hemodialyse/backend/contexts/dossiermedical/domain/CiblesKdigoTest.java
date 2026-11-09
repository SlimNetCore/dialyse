package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaque cible classe une valeur en dessous, dans et au-dessus de la cible (bornes incluses) ; une valeur absente est NON_EVALUABLE, jamais
 * un faux hors cible. Repris de {@code KdigoEvaluationPolicyTest} (bc-medical, retiré par DM9).
 */
class CiblesKdigoTest {

    @Test
    void l_hemoglobine_est_sous_cible_sous_10_dans_la_cible_aux_bornes_et_au_dessus_au_dela_de_11_5() {
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("9.9")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("10")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("11.5")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.hemoglobine(new BigDecimal("13")).statut()).isEqualTo(StatutDeCible.AU_DESSUS_CIBLE);
        assertThat(EvaluationDeCible.hemoglobine(null).statut()).isEqualTo(StatutDeCible.NON_EVALUABLE);
    }

    @Test
    void la_ferritine_le_cst_le_kt_v_et_l_albumine_n_ont_pas_de_borne_haute() {
        assertThat(EvaluationDeCible.ferritine(new BigDecimal("199")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.ferritine(new BigDecimal("200")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.ferritine(new BigDecimal("5000")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.coefficientDeSaturation(new BigDecimal("19.9")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.coefficientDeSaturation(new BigDecimal("20")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.ktV(new BigDecimal("1.1")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.ktV(new BigDecimal("1.2")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.ktV(new BigDecimal("1.6")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.albumine(new BigDecimal("3.9")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.albumine(new BigDecimal("4.0")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
    }

    @Test
    void le_phosphore_le_calcium_et_la_pth_ont_deux_bornes() {
        assertThat(EvaluationDeCible.phosphore(new BigDecimal("2.4")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.phosphore(new BigDecimal("3.5")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.phosphore(new BigDecimal("4.6")).statut()).isEqualTo(StatutDeCible.AU_DESSUS_CIBLE);
        assertThat(EvaluationDeCible.calcium(new BigDecimal("8.3")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.calcium(new BigDecimal("9.0")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.calcium(new BigDecimal("10.3")).statut()).isEqualTo(StatutDeCible.AU_DESSUS_CIBLE);
        assertThat(EvaluationDeCible.pth(new BigDecimal("129")).statut()).isEqualTo(StatutDeCible.SOUS_CIBLE);
        assertThat(EvaluationDeCible.pth(new BigDecimal("300")).statut()).isEqualTo(StatutDeCible.DANS_CIBLE);
        assertThat(EvaluationDeCible.pth(new BigDecimal("601")).statut()).isEqualTo(StatutDeCible.AU_DESSUS_CIBLE);
    }

    @Test
    void l_evaluation_porte_la_reference_kdigo_le_code_et_l_unite_pour_etre_expliquee() {
        EvaluationDeCible e = EvaluationDeCible.hemoglobine(new BigDecimal("11"));
        assertThat(e.referenceKdigo()).isEqualTo("KDIGO-2012-anemia");
        assertThat(e.code()).isEqualTo("HEMOGLOBINE");
        assertThat(e.unite()).isEqualTo("g/dL");
        assertThat(e.borneMin()).isEqualByComparingTo("10");
        assertThat(e.borneMax()).isEqualByComparingTo("11.5");
    }
}
