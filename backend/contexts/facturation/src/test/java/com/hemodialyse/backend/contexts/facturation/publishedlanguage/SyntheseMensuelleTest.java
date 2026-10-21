package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Caisse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheseMensuelleTest {

    private static SyntheseMensuelle avec(List<Caisse> caisses) {
        return new SyntheseMensuelle(UUID.randomUUID(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), caisses, List.of(),
                List.of(), 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void should_have_one_column_per_fund_code_in_reading_order() {
        SyntheseMensuelle synthese = avec(List.of(new Caisse("CASNOS", "CASNOS Centre 1"), new Caisse("CNAS", "CNAS Centre 1")));

        assertThat(synthese.colonnes()).extracting(Caisse::code).containsExactly("CASNOS", "CNAS");
    }

    @Test
    void should_share_the_column_of_the_first_when_two_funds_have_the_same_code() {
        SyntheseMensuelle synthese = avec(List.of(new Caisse("CNAS", "Premiere"), new Caisse("CNAS", "Seconde")));

        assertThat(synthese.colonnes()).containsExactly(new Caisse("CNAS", "Premiere"));
    }

    @Test
    void should_fall_back_to_a_single_unknown_column_when_there_is_no_invoice() {
        assertThat(avec(List.of()).colonnes()).containsExactly(new Caisse("INCONNU", "Inconnu"));
    }
}
