package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Effectif masqué et suppression complémentaire du total (KB-MED-104, corrigé le 2026-11-09). */
class EffectifMasqueTest {

    @Test
    void un_effectif_de_un_a_quatre_est_masque_zero_et_cinq_sont_publies() {
        assertThat(EffectifMasque.masquer(0)).isEqualTo(0L);
        assertThat(EffectifMasque.masquer(1)).isNull();
        assertThat(EffectifMasque.masquer(4)).isNull();
        assertThat(EffectifMasque.masquer(5)).isEqualTo(5L);
        assertThat(EffectifMasque.estMasque(0)).isFalse();
        assertThat(EffectifMasque.estMasque(4)).isTrue();
        assertThat(EffectifMasque.estMasque(5)).isFalse();
    }

    @Test
    void le_total_est_supprime_des_qu_un_seul_centre_est_masque() {
        assertThat(EffectifMasque.consolider(List.of(5L, 3L))).isNull();
        assertThat(EffectifMasque.consolider(List.of(50L, 1L, 20L))).isNull();
        assertThat(EffectifMasque.composeDeMasques(List.of(50L, 1L))).isTrue();
    }

    @Test
    void le_total_est_supprime_quand_tous_les_centres_sont_masques() {
        assertThat(EffectifMasque.consolider(List.of(2L, 4L))).isNull();
    }

    @Test
    void le_total_est_publie_quand_aucun_centre_n_est_masque_y_compris_a_cinq_pile() {
        assertThat(EffectifMasque.consolider(List.of(5L, 5L))).isEqualTo(10L);
        assertThat(EffectifMasque.consolider(List.of(5L, 0L))).isEqualTo(5L);
        assertThat(EffectifMasque.composeDeMasques(List.of(5L, 0L))).isFalse();
    }

    @Test
    void zero_reste_zero_et_une_societe_sans_centre_a_un_total_nul() {
        assertThat(EffectifMasque.consolider(List.of(0L, 0L))).isEqualTo(0L);
        assertThat(EffectifMasque.consolider(List.of())).isEqualTo(0L);
    }

    @Test
    void une_societe_a_un_seul_centre_masque_n_a_pas_de_total() {
        assertThat(EffectifMasque.consolider(List.of(3L))).isNull();
        assertThat(EffectifMasque.consolider(List.of(7L))).isEqualTo(7L);
    }
}
