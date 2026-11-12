package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PL4 (KB-PL4-3, REPRODUIT) : la tension arterielle saisie en texte est lue en systolique et diastolique ; 2 ou 3 chiffres de part et
 * d'autre de {@code /} ou {@code -} (suffixe libre) ; tout autre texte est illisible et rendu sans valeur, sans signal.
 */
class TensionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "130/80|130|80",
            "120-70|120|70",
            "140/90 mmHg|140|90",
            "  95 / 60|95|60",
            "99/100|99|100",
            "130/80/7|130|80"})
    void should_lire_la_systolique_et_la_diastolique(String brut, int systolique, int diastolique) {
        Tension tension = Tension.lire(brut);

        assertThat(tension.systolique()).isEqualTo(systolique);
        assertThat(tension.diastolique()).isEqualTo(diastolique);
        assertThat(tension.estLisible()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9/5", "1000/50", "bizarre", "130", "", "   ", "/80", "ab/cd"})
    void should_rendre_une_tension_illisible_sans_valeur(String brut) {
        Tension tension = Tension.lire(brut);

        assertThat(tension).isEqualTo(Tension.ILLISIBLE);
        assertThat(tension.systolique()).isNull();
        assertThat(tension.diastolique()).isNull();
        assertThat(tension.estLisible()).isFalse();
    }

    @Test
    void should_rendre_une_tension_absente_sans_valeur() {
        assertThat(Tension.lire(null)).isEqualTo(Tension.ILLISIBLE);
    }
}
