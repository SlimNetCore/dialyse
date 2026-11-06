package com.hemodialyse.backend.contexts.dossiermedical.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantesTest {

    private static final SeanceId SEANCE = new SeanceId(new UUID(0, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 1);

    @Test
    void should_be_empty_when_no_measure_was_taken() {
        assertThat(Constantes.vides().sontVides()).isTrue();
        assertThat(new Constantes(null, null, null, null, null, null, null).sontVides()).isTrue();
    }

    @Test
    void should_not_be_empty_as_soon_as_one_measure_exists() {
        BigDecimal un = BigDecimal.ONE;
        assertThat(new Constantes(un, null, null, null, null, null, null).sontVides()).isFalse();
        assertThat(new Constantes(null, un, null, null, null, null, null).sontVides()).isFalse();
        assertThat(new Constantes(null, null, "n/a", null, null, null, null).sontVides()).isFalse();
        assertThat(new Constantes(null, null, null, "12/8", null, null, null).sontVides()).isFalse();
        assertThat(new Constantes(null, null, null, null, 0, null, null).sontVides()).isFalse();
        assertThat(new Constantes(null, null, null, null, null, un, null).sontVides()).isFalse();
        assertThat(new Constantes(null, null, null, null, null, null, 0).sontVides()).isFalse();
    }

    @Test
    void should_keep_raw_values_without_any_bound_or_normalisation() {
        var brutes = new Constantes(new BigDecimal("-3.00"), BigDecimal.ZERO, "12/8", " ", -5, new BigDecimal("-100"), 99999);
        assertThat(brutes.tensionAvant()).isEqualTo("12/8");
        assertThat(brutes.tensionApres()).isEqualTo(" ");
        assertThat(brutes.poidsAvantKg()).isEqualByComparingTo("-3");
        assertThat(brutes.debitSangMlMin()).isEqualTo(-5);
    }

    @Test
    void should_use_empty_constants_for_a_session_without_sheet() {
        assertThat(ConstantesDeSeance.sansSaisie(SEANCE, JOUR).constantes()).isSameAs(Constantes.vides());
        assertThat(new ConstantesDeSeance(SEANCE, JOUR, null).constantes()).isSameAs(Constantes.vides());
    }

    @Test
    void should_reject_a_point_without_session_or_date() {
        assertThatThrownBy(() -> new ConstantesDeSeance(null, JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ConstantesDeSeance(SEANCE, null, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_reject_missing_identifiers() {
        assertThatThrownBy(() -> new PatientId(null)).isInstanceOf(SaisieInvalide.class).hasMessage("Patient requis");
        assertThatThrownBy(() -> new SeanceId(null)).isInstanceOf(SaisieInvalide.class).hasMessage("Séance requise");
    }
}
