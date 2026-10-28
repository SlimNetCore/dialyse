package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduction du langage publié de la Comptabilité (règle de TVA applicable) vers le taux de la Facturation.
 */
class TauxDeTvaComptabiliteTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000a"));
    private static final LocalDate LE = LocalDate.of(2026, 8, 31);

    @Test
    void should_ask_the_rule_of_the_hemodialysis_services_at_the_given_date_and_take_its_rate() {
        List<String> demandes = new ArrayList<>();
        ParametrageFiscal fiscal = (centre, type, date) -> {
            demandes.add(centre + "/" + type + "/" + date);
            return Optional.of(new ParametrageFiscal.RegleTvaApplicable(type, new BigDecimal("9.00"), false,
                    LocalDate.of(2000, 1, 1), null, null));
        };

        TauxDeTva taux = new TauxDeTvaComptabilite(fiscal).aLaDate(CENTRE, LE);

        assertThat(taux.pourcentage()).isEqualByComparingTo("9");
        assertThat(demandes).containsExactly(CENTRE.valeur() + "/HEMODIALYSE/2026-08-31");
    }

    @Test
    void should_charge_no_vat_when_no_rule_applies() {
        ParametrageFiscal aucuneRegle = (centre, type, date) -> Optional.empty();

        assertThat(new TauxDeTvaComptabilite(aucuneRegle).aLaDate(CENTRE, LE)).isEqualTo(TauxDeTva.AUCUN);
    }

    @Test
    void should_take_the_rate_of_an_exempt_rule_as_it_is() {
        ParametrageFiscal exoneree = (centre, type, date) -> Optional.of(new ParametrageFiscal.RegleTvaApplicable(type,
                BigDecimal.ZERO, true, LocalDate.of(2000, 1, 1), null, "Exonération"));

        assertThat(new TauxDeTvaComptabilite(exoneree).aLaDate(CENTRE, LE).pourcentage()).isEqualByComparingTo("0");
    }
}
