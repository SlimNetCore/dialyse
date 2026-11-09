package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le risque immunologique retient toujours la pire des deux classes PRA ; une sérologie positive d'un marqueur non listé (CMV IgG) ne
 * donne jamais d'alerte. Repris de {@code KdigoGreffeEvaluationPolicyTest} (bc-medical, retiré par DM9).
 */
class EvaluationKdigoDuCandidatTest {

    @Test
    void le_risque_immunologique_est_faible_sous_20_intermediaire_de_20_a_80_eleve_au_dela() {
        assertThat(EvaluationKdigoDuCandidat.risqueImmunologique(new BigDecimal("19.9"), new BigDecimal("5")).niveau())
                .isEqualTo(NiveauDeRisqueKdigo.FAIBLE);
        assertThat(EvaluationKdigoDuCandidat.risqueImmunologique(new BigDecimal("20"), null).niveau()).isEqualTo(NiveauDeRisqueKdigo.INTERMEDIAIRE);
        assertThat(EvaluationKdigoDuCandidat.risqueImmunologique(new BigDecimal("80"), null).niveau()).isEqualTo(NiveauDeRisqueKdigo.INTERMEDIAIRE);
        assertThat(EvaluationKdigoDuCandidat.risqueImmunologique(new BigDecimal("10"), new BigDecimal("85")).niveau())
                .isEqualTo(NiveauDeRisqueKdigo.ELEVE);
    }

    @Test
    void le_risque_immunologique_retient_la_pire_des_deux_classes_et_est_non_evaluable_sans_valeur() {
        RisqueImmunologique pire = EvaluationKdigoDuCandidat.risqueImmunologique(new BigDecimal("5"), new BigDecimal("90"));
        assertThat(pire.valeur()).isEqualByComparingTo("90");
        assertThat(pire.code()).isEqualTo("PRA");
        assertThat(pire.unite()).isEqualTo("%");
        assertThat(pire.referenceKdigo()).isEqualTo("KDIGO-2020-candidat-greffe");
        RisqueImmunologique aucun = EvaluationKdigoDuCandidat.risqueImmunologique(null, null);
        assertThat(aucun.niveau()).isEqualTo(NiveauDeRisqueKdigo.NON_EVALUABLE);
        assertThat(aucun.valeur()).isNull();
    }

    @Test
    void la_fonction_renale_est_non_evaluable_sans_creatinine_positive_age_ou_sexe() {
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(null, 45, "M").stade()).isEqualTo(StadeDeMaladieRenale.NON_EVALUABLE);
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(BigDecimal.ZERO, 45, "M").stade()).isEqualTo(StadeDeMaladieRenale.NON_EVALUABLE);
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("1"), null, "M").stade()).isEqualTo(StadeDeMaladieRenale.NON_EVALUABLE);
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("1"), 45, null).egfrMlMin173m2()).isNull();
    }

    @Test
    void la_fonction_renale_estime_g5_pour_un_dialyse_chronique_et_g1_pour_un_jeune_adulte_sain() {
        FonctionRenale dialyse = EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("8.5"), 55, "M");
        assertThat(dialyse.stade()).isEqualTo(StadeDeMaladieRenale.G5);
        assertThat(dialyse.egfrMlMin173m2()).isLessThan(new BigDecimal("15"));
        assertThat(dialyse.referenceKdigo()).isEqualTo("KDIGO-CKD-EPI-2021");
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("0.8"), 30, "F").stade()).isEqualTo(StadeDeMaladieRenale.G1);
        // Arrondi à une décimale ; le sexe est reconnu sans casse (F, FEMININ, FEMME) : la valeur HTTP exacte est figée par le golden DM7.
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("1.1"), 50, "M").egfrMlMin173m2().scale()).isEqualTo(1);
        assertThat(EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("1.1"), 50, "femme").egfrMlMin173m2())
                .isEqualByComparingTo(EvaluationKdigoDuCandidat.fonctionRenale(new BigDecimal("1.1"), 50, "F").egfrMlMin173m2());
    }

    @Test
    void les_alertes_serologiques_ne_retiennent_que_les_positifs_des_marqueurs_listes() {
        List<AlerteSerologiqueKdigo> vih = EvaluationKdigoDuCandidat.alertesSerologiques(Map.of("VIH_AC", "POSITIF"));
        assertThat(vih).hasSize(1);
        assertThat(vih.get(0).marqueur()).isEqualTo("VIH_AC");
        assertThat(vih.get(0).message()).startsWith("Infection VIH connue");
        assertThat(EvaluationKdigoDuCandidat.alertesSerologiques(Map.of("VIH_AC", "NEGATIF"))).isEmpty();
        assertThat(EvaluationKdigoDuCandidat.alertesSerologiques(Map.of("CMV_IGG", "POSITIF"))).isEmpty();
        assertThat(EvaluationKdigoDuCandidat.alertesSerologiques(Map.of())).isEmpty();
        assertThat(EvaluationKdigoDuCandidat.alertesSerologiques(null)).isEmpty();
    }
}
