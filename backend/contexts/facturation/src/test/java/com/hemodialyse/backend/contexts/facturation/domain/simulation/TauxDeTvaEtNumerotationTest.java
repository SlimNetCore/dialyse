package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TauxDeTvaEtNumerotationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("abcdef12-0000-0000-0000-00000000000a"));
    private static final LocalDate LE_20_OCTOBRE = LocalDate.of(2026, 10, 20);

    @Test
    void should_compute_the_vat_rounded_half_up_to_two_decimals() {
        assertThat(new TauxDeTva(new BigDecimal("9")).tvaSur(new BigDecimal("3500.00")).toPlainString()).isEqualTo("315.00");
        assertThat(new TauxDeTva(new BigDecimal("19")).tvaSur(new BigDecimal("33.33")).toPlainString()).isEqualTo("6.33");
        assertThat(new TauxDeTva(new BigDecimal("5.5")).tvaSur(new BigDecimal("0.10")).toPlainString()).isEqualTo("0.01");
        assertThat(new TauxDeTva(new BigDecimal("5")).tvaSur(new BigDecimal("0.10")).toPlainString()).isEqualTo("0.01");
        assertThat(new TauxDeTva(new BigDecimal("4")).tvaSur(new BigDecimal("0.10")).toPlainString()).isEqualTo("0.00");
    }

    @Test
    void should_charge_no_vat_without_a_rule() {
        assertThat(TauxDeTva.AUCUN.tvaSur(new BigDecimal("3500.00")).toPlainString()).isEqualTo("0.00");
    }

    @Test
    void should_refuse_a_negative_or_missing_rate() {
        assertThatThrownBy(() -> new TauxDeTva(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TauxDeTva(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_format_the_invoice_number_with_year_centre_and_sequence_tokens() {
        assertThat(new ModeleDeNumerotation("FAC-{YEAR}-{SEQ}").numero(CENTRE, LE_20_OCTOBRE, 42)).isEqualTo("FAC-2026-0042");
        assertThat(new ModeleDeNumerotation("{YYYY}/{YY}/{CENTER}/{SEQ6}").numero(CENTRE, LE_20_OCTOBRE, 7))
                .isEqualTo("2026/26/ABCDEF12/000007");
        assertThat(new ModeleDeNumerotation("  SIM-{SEQ2}  ").numero(CENTRE, LE_20_OCTOBRE, 123)).isEqualTo("SIM-123");
        assertThat(new ModeleDeNumerotation("A{SEQ}-{SEQ3}").numero(CENTRE, LE_20_OCTOBRE, 5)).isEqualTo("A0005-005");
    }

    @Test
    void should_refuse_a_non_positive_sequence_a_missing_centre_or_a_missing_date() {
        ModeleDeNumerotation modele = ModeleDeNumerotation.PAR_DEFAUT;
        assertThatThrownBy(() -> modele.numero(CENTRE, LE_20_OCTOBRE, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La sequence doit etre strictement positive");
        assertThatThrownBy(() -> modele.numero(null, LE_20_OCTOBRE, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le centre est obligatoire");
        assertThatThrownBy(() -> modele.numero(CENTRE, null, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La date de facturation est obligatoire");
    }

    @Test
    void should_number_the_nth_invoice_after_the_current_sequence() {
        NumerotationPrevisionnelle numerotation = new NumerotationPrevisionnelle(new ModeleDeNumerotation("SIM-{SEQ}"),
                LE_20_OCTOBRE, 41);

        assertThat(numerotation.numero(CENTRE, 1)).isEqualTo("SIM-0042");
        assertThat(numerotation.numero(CENTRE, 3)).isEqualTo("SIM-0044");
    }

    @Test
    void should_start_at_one_for_a_centre_that_has_issued_nothing() {
        assertThat(new NumerotationPrevisionnelle(ModeleDeNumerotation.PAR_DEFAUT, LE_20_OCTOBRE, 0).numero(CENTRE, 1))
                .isEqualTo("FAC-2026-0001");
    }

    @Test
    void should_refuse_an_incomplete_or_negative_numbering() {
        assertThatThrownBy(() -> new NumerotationPrevisionnelle(null, LE_20_OCTOBRE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NumerotationPrevisionnelle(ModeleDeNumerotation.PAR_DEFAUT, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NumerotationPrevisionnelle(ModeleDeNumerotation.PAR_DEFAUT, LE_20_OCTOBRE, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_bill_only_validated_or_signed_statuses_with_exact_case() {
        assertThat(StatutDeSeance.VALIDEE.estFacturable()).isTrue();
        assertThat(StatutDeSeance.SIGNEE.estFacturable()).isTrue();
        assertThat(StatutDeSeance.ABSENT.estFacturable()).isFalse();
        assertThat(new StatutDeSeance("validee").estFacturable()).isFalse();
        assertThat(new StatutDeSeance("FACTUREE").estFacturable()).isFalse();
        assertThatThrownBy(() -> new StatutDeSeance(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(StatutDeSeance.VALIDEE).hasToString("VALIDEE");
    }

    @Test
    void should_group_by_patient_or_by_patient_and_forfait() {
        assertThat(Regroupement.multiForfait(true)).isEqualTo(Regroupement.PAR_PATIENT);
        assertThat(Regroupement.multiForfait(false)).isEqualTo(Regroupement.PAR_PATIENT_ET_FORFAIT);
    }
}
