package com.hemodialyse.backend.contexts.absencespatients.infrastructure;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.comptabilite.TauxDeTvaComptabilite;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal.RegleTvaApplicable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction de la règle de TVA du langage publié de la Comptabilité en taux d'absence : type HEMODIALYSE exact, règle exonérée ou
 * absente = 0 % (KB-CPT-19 reproduit).
 */
class TauxDeTvaComptabiliteTest {

    private static final CentreId CENTRE = new CentreId(new UUID(1, 1));
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 1);

    private static RegleTvaApplicable regle(String taux, boolean exonere) {
        return new RegleTvaApplicable("HEMODIALYSE", new BigDecimal(taux), exonere, LocalDate.of(2000, 1, 1), null, null);
    }

    @Test
    void le_taux_de_la_regle_du_type_hemodialyse_est_retenu() {
        var taux = new TauxDeTvaComptabilite((centre, type, date) ->
                "HEMODIALYSE".equals(type) && centre.equals(CENTRE.valeur()) && date.equals(JOUR)
                        ? Optional.of(regle("19.00", false)) : Optional.empty());

        assertThat(taux.pour(CENTRE, JOUR)).isEqualByComparingTo("19");
        assertThat(taux.pour(CENTRE, JOUR.plusDays(1))).isEqualByComparingTo("0");
        assertThat(taux.pour(new CentreId(new UUID(1, 2)), JOUR)).isEqualByComparingTo("0");
    }

    @Test
    void une_regle_exoneree_se_lit_zero_pour_cent() {
        var taux = new TauxDeTvaComptabilite((centre, type, date) -> Optional.of(regle("19.00", true)));

        assertThat(taux.pour(CENTRE, JOUR)).isEqualByComparingTo("0");
    }
}
