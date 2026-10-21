package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglementTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996000-0000-0000-0000-0000000000a1"));
    private static final UUID FACTURE = UUID.fromString("99996000-0000-0000-0000-0000000f0001");
    private static final UUID ID = UUID.fromString("abcdef12-0000-0000-0000-000000000001");
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 20);

    private static FactureARegler facture(String regle) {
        return new FactureARegler(FACTURE, new BigDecimal("1000.00"), new BigDecimal(regle));
    }

    private static Reglement enregistrer(String montant, String dejaRegle) {
        return Reglement.enregistrer(ID, CENTRE, facture(dejaRegle), new BigDecimal(montant), JOUR, "auteur", null, null, null);
    }

    @Test
    void should_record_a_payment_with_the_code_made_of_the_first_eight_characters_of_its_identity() {
        Reglement reglement = enregistrer("250.00", "0");

        assertThat(reglement.code()).isEqualTo("REG-ABCDEF12");
        assertThat(reglement.facture()).isEqualTo(FACTURE);
        assertThat(reglement.centre()).isEqualTo(CENTRE);
        assertThat(reglement.date()).isEqualTo(JOUR);
        assertThat(reglement.auteur()).isEqualTo("auteur");
        assertThat(reglement.estUnRemboursement()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"10.005,10.01", "10.004,10.00", "-10.005,-10.01", "-10.004,-10.00", "0.005,0.01", "999999999999.994,999999999999.99"})
    void should_round_the_amount_to_the_cent_half_up(String saisi, String attendu) {
        Reglement reglement = Reglement.enregistrer(ID, CENTRE, facture("1000000000000.00"), new BigDecimal(saisi), JOUR, "a",
                null, null, null);

        assertThat(reglement.montant()).isEqualByComparingTo(attendu);
        assertThat(reglement.montant().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"0", "0.00", "0.004", "-0.004"})
    void should_refuse_an_amount_that_is_null_before_or_after_rounding(String saisi) {
        assertThatThrownBy(() -> enregistrer(saisi, "100.00")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le montant du reglement ne peut pas etre nul");
    }

    @Test
    void should_refuse_a_missing_amount() {
        assertThatThrownBy(() -> Reglement.exigerUnMontantNonNul(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_a_payment_above_the_remaining_amount_as_an_overpayment() {
        Reglement reglement = enregistrer("5000.00", "900.00");

        assertThat(reglement.montant()).isEqualByComparingTo("5000.00");
    }

    @Test
    void should_accept_a_refund_up_to_the_total_already_paid() {
        Reglement reglement = enregistrer("-400.00", "400.00");

        assertThat(reglement.estUnRemboursement()).isTrue();
        assertThat(reglement.montant()).isEqualByComparingTo("-400.00");
    }

    @Test
    void should_refuse_a_refund_above_the_total_already_paid() {
        assertThatThrownBy(() -> enregistrer("-400.01", "400.00")).isInstanceOf(RemboursementSuperieurAuTotalRegle.class)
                .hasMessageContaining("400.01").hasMessageContaining("400.00");
    }

    @Test
    void should_refuse_a_refund_when_nothing_was_paid_or_the_total_paid_is_negative() {
        assertThatThrownBy(() -> enregistrer("-1.00", "0")).isInstanceOf(RemboursementSuperieurAuTotalRegle.class);
        assertThatThrownBy(() -> enregistrer("-1.00", "-50.00")).isInstanceOf(RemboursementSuperieurAuTotalRegle.class)
                .hasMessageContaining("(0.00)");
    }

    @Test
    void should_compare_the_refund_after_rounding() {
        assertThat(enregistrer("-400.004", "400.00").montant()).isEqualByComparingTo("-400.00");
        assertThatThrownBy(() -> enregistrer("-400.005", "400.00")).isInstanceOf(RemboursementSuperieurAuTotalRegle.class);
    }

    @Test
    void should_default_the_author_to_system_when_unknown() {
        assertThat(Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, null, null, null, null).auteur())
                .isEqualTo("system");
        assertThat(Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, "  ", null, null, null).auteur())
                .isEqualTo("system");
        assertThat(enregistrer("10", "0").auteur()).isEqualTo("auteur");
    }

    @Test
    void should_keep_the_mode_the_reference_and_the_idempotency_key_trimmed() {
        Reglement reglement = Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, "a", ModeDeReglement.CHEQUE,
                "  CHQ-1  ", " cle-1 ");

        assertThat(reglement.mode()).isEqualTo(ModeDeReglement.CHEQUE);
        assertThat(reglement.reference()).isEqualTo("CHQ-1");
        assertThat(reglement.cleDIdempotence()).isEqualTo("cle-1");
    }

    @Test
    void should_treat_a_blank_reference_and_key_as_absent() {
        Reglement reglement = Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, "a", null, " ", "");

        assertThat(reglement.reference()).isNull();
        assertThat(reglement.cleDIdempotence()).isNull();
    }

    @Test
    void should_accept_a_reference_of_the_maximal_length_and_refuse_a_longer_one() {
        String limite = "x".repeat(Reglement.LONGUEUR_MAXIMALE);

        assertThat(Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, "a", null, limite, limite).reference())
                .hasSize(120);
        assertThatThrownBy(() -> Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, "a", null, limite + "x", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reference");
        assertThatThrownBy(() -> Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, JOUR, "a", null, null, limite + "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cle");
    }

    @Test
    void should_refuse_a_payment_without_date_or_identities() {
        assertThatThrownBy(() -> Reglement.enregistrer(ID, CENTRE, facture("0"), BigDecimal.TEN, null, "a", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("date");
        assertThatThrownBy(() -> new Reglement(null, FACTURE, CENTRE, BigDecimal.TEN, JOUR, "a", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reglement(ID, null, CENTRE, BigDecimal.TEN, JOUR, "a", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reglement(ID, FACTURE, null, BigDecimal.TEN, JOUR, "a", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_keep_the_code_of_a_stored_payment_even_when_it_has_none() {
        assertThat(new Reglement(ID, FACTURE, CENTRE, BigDecimal.TEN, JOUR, "a", null, null, null, null).code()).isNull();
        assertThat(new Reglement(ID, FACTURE, CENTRE, BigDecimal.TEN, JOUR, "a", "REP-1", null, null, null).code())
                .isEqualTo("REP-1");
    }

    @Test
    void should_not_allow_a_zero_payment_to_be_rebuilt() {
        assertThatThrownBy(() -> new Reglement(ID, FACTURE, CENTRE, BigDecimal.ZERO, JOUR, "a", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reglement(ID, FACTURE, CENTRE, null, JOUR, "a", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
