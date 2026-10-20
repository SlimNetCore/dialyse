package com.hemodialyse.backend.contexts.facturation.domain.facture;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LigneFactureTest {

    @Test
    void should_compute_the_line_amount_with_two_decimals() {
        LigneFacture ligne = new LigneFacture(UUID.randomUUID(), "Forfait HD", new BigDecimal("3500"), 3);

        assertThat(ligne.prixUnitaireHt()).isEqualTo(new BigDecimal("3500.00"));
        assertThat(ligne.montantHt()).isEqualTo(new BigDecimal("10500.00"));
    }

    @Test
    void should_round_the_unit_price_half_up() {
        LigneFacture ligne = new LigneFacture(UUID.randomUUID(), "Forfait", new BigDecimal("10.005"), 1);

        assertThat(ligne.prixUnitaireHt()).isEqualTo(new BigDecimal("10.01"));
    }

    @Test
    void should_accept_a_free_forfait() {
        assertThat(new LigneFacture(UUID.randomUUID(), "Forfait offert", BigDecimal.ZERO, 1).montantHt())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void should_default_the_label_when_missing() {
        assertThat(new LigneFacture(UUID.randomUUID(), null, BigDecimal.ONE, 1).libelle()).isEqualTo("Forfait");
    }

    @Test
    void should_refuse_a_missing_or_negative_price() {
        assertThatThrownBy(() -> new LigneFacture(UUID.randomUUID(), "F", null, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le prix unitaire HT est invalide");
        assertThatThrownBy(() -> new LigneFacture(UUID.randomUUID(), "F", new BigDecimal("-0.01"), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le prix unitaire HT est invalide");
    }

    @Test
    void should_refuse_a_line_without_seance() {
        assertThatThrownBy(() -> new LigneFacture(UUID.randomUUID(), "F", BigDecimal.ONE, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le nombre de seances doit etre strictement positif");
    }
}
