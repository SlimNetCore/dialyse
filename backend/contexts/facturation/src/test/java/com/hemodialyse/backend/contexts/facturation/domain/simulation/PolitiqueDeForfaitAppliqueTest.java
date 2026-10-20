package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PolitiqueDeForfaitAppliqueTest {

    private static final LocalDate LE_15_AOUT = LocalDate.of(2026, 8, 15);
    private static final ForfaitApplique HD = forfait("Forfait HD", "3500.00");
    private static final ForfaitApplique PREMIUM = forfait("Forfait HD Premium", "5200.00");
    private static final ForfaitApplique SURCHARGE = forfait("Forfait imposé", "100.00");

    private static ForfaitApplique forfait(String libelle, String prix) {
        return new ForfaitApplique(UUID.randomUUID(), libelle, new BigDecimal(prix));
    }

    private static PriseEnChargeTarifee effective(ForfaitApplique forfait, String debut, String fin, int jourDeCreation) {
        return new PriseEnChargeTarifee(forfait, true, LocalDate.parse(debut), fin == null ? null : LocalDate.parse(fin), null, null,
                Instant.parse("2026-01-%02dT08:00:00Z".formatted(jourDeCreation)));
    }

    private static PriseEnChargeTarifee demandee(ForfaitApplique forfait, String debut, String fin, int jourDeCreation) {
        return new PriseEnChargeTarifee(forfait, false, null, null, LocalDate.parse(debut), fin == null ? null : LocalDate.parse(fin),
                Instant.parse("2026-01-%02dT08:00:00Z".formatted(jourDeCreation)));
    }

    @Test
    void should_apply_the_forfait_imposed_on_the_seance_before_any_prise_en_charge() {
        ForfaitApplique retenu = PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.of(SURCHARGE),
                List.of(effective(HD, "2026-01-01", "2026-12-31", 1)));

        assertThat(retenu).isEqualTo(SURCHARGE);
    }

    @Test
    void should_apply_the_forfait_of_the_prise_en_charge_covering_the_date() {
        ForfaitApplique retenu = PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(),
                List.of(effective(HD, "2026-01-01", "2026-12-31", 1)));

        assertThat(retenu).isEqualTo(HD);
    }

    @Test
    void should_apply_the_requested_forfait_when_only_the_request_period_covers_the_date() {
        ForfaitApplique retenu = PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(),
                List.of(demandee(PREMIUM, "2026-01-01", "2026-12-31", 1)));

        assertThat(retenu).isEqualTo(PREMIUM);
    }

    @Test
    void should_cover_the_start_and_end_dates_and_an_open_ended_period() {
        PriseEnChargeTarifee pec = effective(HD, "2026-08-15", "2026-08-15", 1);
        assertThat(PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(), List.of(pec))).isEqualTo(HD);

        PriseEnChargeTarifee ouverte = effective(PREMIUM, "2026-08-01", null, 1);
        assertThat(PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(), List.of(ouverte))).isEqualTo(PREMIUM);
    }

    @Test
    void should_prefer_a_prise_en_charge_with_an_effective_forfait_then_the_most_recent_one() {
        PriseEnChargeTarifee demandeeRecente = demandee(PREMIUM, "2026-01-01", "2026-12-31", 20);
        PriseEnChargeTarifee effectiveAncienne = effective(HD, "2026-01-01", "2026-12-31", 2);
        PriseEnChargeTarifee effectiveRecente = effective(SURCHARGE, "2026-01-01", "2026-12-31", 10);

        assertThat(PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(), List.of(demandeeRecente, effectiveAncienne)))
                .isEqualTo(HD);
        assertThat(PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(),
                List.of(effectiveAncienne, effectiveRecente, demandeeRecente))).isEqualTo(SURCHARGE);
    }

    @Test
    void should_fall_back_on_the_most_recent_prise_en_charge_even_when_it_has_ended() {
        ForfaitApplique retenu = PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(),
                List.of(effective(HD, "2026-01-01", "2026-06-30", 1)));

        assertThat(retenu).isEqualTo(HD);
    }

    @Test
    void should_prefer_a_covering_prise_en_charge_to_a_more_recent_one_that_does_not_cover() {
        ForfaitApplique retenu = PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(), List.of(
                effective(HD, "2026-01-01", "2026-12-31", 1), effective(PREMIUM, "2026-01-01", "2026-06-30", 25)));

        assertThat(retenu).isEqualTo(HD);
    }

    @Test
    void should_apply_the_undefined_forfait_at_zero_without_any_prise_en_charge() {
        ForfaitApplique retenu = PolitiqueDeForfaitApplique.pour(LE_15_AOUT, Optional.empty(), List.of());

        assertThat(retenu).isEqualTo(ForfaitApplique.NON_DEFINI);
        assertThat(retenu.libelle()).isEqualTo("Forfait non defini");
        assertThat(retenu.prixHt()).isEqualTo(BigDecimal.ZERO);
        assertThat(retenu.id()).isNull();
    }

    @Test
    void should_price_a_forfait_without_price_at_zero() {
        assertThat(new ForfaitApplique(UUID.randomUUID(), "Sans prix", null).prixHt()).isEqualTo(BigDecimal.ZERO);
    }
}
