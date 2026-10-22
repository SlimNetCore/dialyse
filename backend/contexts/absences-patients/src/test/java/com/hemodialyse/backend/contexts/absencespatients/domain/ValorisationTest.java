package com.hemodialyse.backend.contexts.absencespatients.domain;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valorisation figée d'une absence (A9, RG-ABS-030 à 032) : le forfait est hors taxe, le TTC en découle, les deux arrondis sont
 * à deux décimales (demi vers le haut), sans forfait la valeur est nulle, le taux est rendu à l'échelle de la base.
 */
class ValorisationTest {

    private static final ForfaitId FORFAIT = new ForfaitId(new UUID(1, 1));

    @ParameterizedTest
    @CsvSource({
            "10000.00, 19, 10000.00, 11900.00, 19.00",
            "10000.00, 0, 10000.00, 10000.00, 0.00",
            "10000.00, 9.5, 10000.00, 10950.00, 9.50",
            "10.10, 5, 10.10, 10.61, 5.00",
            "0.05, 10, 0.05, 0.06, 10.00",
            "4500.005, 19, 4500.01, 5355.01, 19.00",
            "4500.004, 19, 4500.00, 5355.00, 19.00",
            "0, 19, 0.00, 0.00, 19.00",
            "100.00, 150, 100.00, 250.00, 150.00"})
    void le_ttc_decoule_du_prix_hors_taxe_et_du_taux_avec_deux_arrondis(String prix, String taux, String ht, String ttc,
                                                                         String tauxRendu) {
        Valorisation valeur = Valorisation.depuis(new ForfaitApplicable(FORFAIT, "Forfait", new BigDecimal(prix)), new BigDecimal(taux));

        assertThat(valeur.montantHt()).isEqualTo(new BigDecimal(ht));
        assertThat(valeur.montantTtc()).isEqualTo(new BigDecimal(ttc));
        assertThat(valeur.tauxDeTva()).isEqualTo(new BigDecimal(tauxRendu));
        assertThat(valeur.forfait()).isEqualTo(FORFAIT);
        assertThat(valeur.libelleDuForfait()).isEqualTo("Forfait");
    }

    @Test
    void sans_forfait_la_valeur_est_nulle_mais_garde_le_taux() {
        Valorisation valeur = Valorisation.depuis(null, new BigDecimal("19"));

        assertThat(valeur.forfait()).isNull();
        assertThat(valeur.libelleDuForfait()).isNull();
        assertThat(valeur.montantHt()).isEqualTo(new BigDecimal("0.00"));
        assertThat(valeur.montantTtc()).isEqualTo(new BigDecimal("0.00"));
        assertThat(valeur.tauxDeTva()).isEqualTo(new BigDecimal("19.00"));
    }

    @Test
    void un_taux_zero_est_rendu_avec_l_echelle_de_la_base() {
        assertThat(Valorisation.depuis(null, BigDecimal.ZERO).tauxDeTva().toPlainString()).isEqualTo("0.00");
    }

    @Test
    void aucun_montant_ni_taux_negatif() {
        ForfaitApplicable forfait = new ForfaitApplicable(FORFAIT, "Forfait", new BigDecimal("100"));
        BigDecimal un = BigDecimal.ONE;
        BigDecimal moinsUn = BigDecimal.ONE.negate();

        assertThatThrownBy(() -> new Valorisation(FORFAIT, "F", moinsUn, un, un)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Valorisation(FORFAIT, "F", un, moinsUn, un)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Valorisation(FORFAIT, "F", un, un, moinsUn)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Valorisation(FORFAIT, "F", null, un, un)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Valorisation(FORFAIT, "F", un, null, un)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Valorisation(FORFAIT, "F", un, un, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Valorisation.depuis(forfait, moinsUn)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ForfaitApplicable(null, "F", un)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ForfaitApplicable(FORFAIT, "F", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
