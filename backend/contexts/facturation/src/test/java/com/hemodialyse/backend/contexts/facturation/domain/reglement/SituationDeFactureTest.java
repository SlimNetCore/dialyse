package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SituationDeFactureTest {

    @ParameterizedTest(name = "{0} sur {1} : {2} / {3}")
    @CsvSource({
            "0.00,1000.00,NON_REGLEE,RESTE,1000.00,0.00",
            "400.00,1000.00,PARTIELLEMENT_REGLEE,RESTE,600.00,0.00",
            "0.01,1000.00,PARTIELLEMENT_REGLEE,RESTE,999.99,0.00",
            "1000.00,1000.00,REGLEE,REGLE,0.00,0.00",
            "1000.01,1000.00,REGLEE,TROP_PERCU,0.00,0.01",
            "1500.00,1000.00,REGLEE,TROP_PERCU,0.00,500.00",
            "-100.00,1000.00,PARTIELLEMENT_REGLEE,RESTE,1100.00,0.00",
            "5.00,0.00,REGLEE,TROP_PERCU,0.00,5.00"})
    void should_derive_the_situation_from_the_signed_total_against_the_invoice(String regle, String total, String etat,
                                                                              String solde, String reste, String tropPercu) {
        SituationDeFacture situation = SituationDeFacture.evaluer(new BigDecimal(total), new BigDecimal(regle));

        assertThat(situation.etat()).isEqualTo(EtatDeReglement.valueOf(etat));
        assertThat(situation.solde()).isEqualTo(TypeDeSolde.valueOf(solde));
        assertThat(situation.reste()).isEqualByComparingTo(reste);
        assertThat(situation.tropPercu()).isEqualByComparingTo(tropPercu);
    }

    @Test
    void should_consider_an_invoice_with_nothing_net_paid_as_not_settled() {
        assertThat(SituationDeFacture.evaluer(new BigDecimal("1000"), BigDecimal.ZERO).etat()).isEqualTo(EtatDeReglement.NON_REGLEE);
        assertThat(SituationDeFacture.evaluer(new BigDecimal("1000"), null).etat()).isEqualTo(EtatDeReglement.NON_REGLEE);
        assertThat(SituationDeFacture.evaluer(null, null).etat()).isEqualTo(EtatDeReglement.NON_REGLEE);
    }

    @Test
    void should_compare_at_the_cent() {
        assertThat(SituationDeFacture.evaluer(new BigDecimal("1000.004"), new BigDecimal("1000.00")).solde())
                .isEqualTo(TypeDeSolde.REGLE);
        assertThat(SituationDeFacture.evaluer(new BigDecimal("1000.00"), new BigDecimal("999.996")).solde())
                .isEqualTo(TypeDeSolde.REGLE);
    }

    @Test
    void should_refuse_an_invoice_with_a_negative_total() {
        assertThatThrownBy(() -> new FactureARegler(UUID.randomUUID(), new BigDecimal("-0.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le montant facture doit etre positif");
        assertThatThrownBy(() -> new FactureARegler(UUID.randomUUID(), null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FactureARegler(null, BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new FactureARegler(UUID.randomUUID(), BigDecimal.ZERO, null).dejaRegle()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @CsvSource({"BANQUE,false", "CHEQUE,false", "CAISSE,true", "ESPECES,true", "caisse,true", " espèces ,", "  ,", "virement,"})
    void should_read_the_mode_and_tell_whether_it_enters_the_cash_desk(String texte, Boolean enCaisse) {
        if (enCaisse == null && (texte == null || texte.isBlank())) {
            assertThat(ModeDeReglement.depuis(texte)).isNull();
        } else if (enCaisse == null) {
            assertThatThrownBy(() -> ModeDeReglement.depuis(texte)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mode de reglement inconnu");
        } else {
            assertThat(ModeDeReglement.depuis(texte).enCaisse()).isEqualTo(enCaisse);
        }
    }

    @Test
    void should_read_an_absent_mode_as_none() {
        assertThat(ModeDeReglement.depuis(null)).isNull();
    }
}
