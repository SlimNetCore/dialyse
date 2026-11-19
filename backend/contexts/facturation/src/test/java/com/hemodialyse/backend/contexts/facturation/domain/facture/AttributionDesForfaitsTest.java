package com.hemodialyse.backend.contexts.facturation.domain.facture;

import com.hemodialyse.backend.contexts.facturation.domain.facture.AttributionDesForfaits.SeanceAAttribuer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RG-FAC-048 : attribution sans recalcul des lignes figées aux séances d'une facture. */
class AttributionDesForfaitsTest {

    private static final UUID HD = UUID.fromString("00000000-0000-0000-0000-0000000f0001");
    private static final UUID PREMIUM = UUID.fromString("00000000-0000-0000-0000-0000000f0002");
    private static final LigneFacture LIGNE_HD = new LigneFacture(HD, "Hemodialyse standard", new BigDecimal("8000"), 2);
    private static final LigneFacture LIGNE_PREMIUM = new LigneFacture(PREMIUM, "HD Premium", new BigDecimal("9500"), 1);

    private static SeanceAAttribuer seance(int jour, UUID surcharge) {
        return new SeanceAAttribuer(UUID.randomUUID(), LocalDate.of(2026, 10, jour), "FACTUREE", surcharge);
    }

    @Test
    void should_give_every_seance_the_single_line_of_a_one_line_invoice() {
        List<SeanceAttribuee> attribuees = AttributionDesForfaits.attribuer(List.of(LIGNE_HD), List.of(seance(1, null), seance(3, null)));

        assertThat(attribuees).extracting(SeanceAttribuee::ligne).containsExactly(LIGNE_HD, LIGNE_HD);
        assertThat(attribuees).allMatch(SeanceAttribuee::estAttribuee);
    }

    @Test
    void should_give_a_seance_with_an_imposed_forfait_the_line_of_that_forfait_then_the_others_the_only_line_left() {
        SeanceAAttribuer premium = seance(5, PREMIUM);
        List<SeanceAttribuee> attribuees = AttributionDesForfaits.attribuer(List.of(LIGNE_HD, LIGNE_PREMIUM),
                List.of(seance(1, null), seance(3, null), premium));

        assertThat(attribuees).extracting(SeanceAttribuee::ligne).containsExactly(LIGNE_HD, LIGNE_HD, LIGNE_PREMIUM);
        assertThat(attribuees.get(2).seance()).isEqualTo(premium.seance());
        assertThat(attribuees.get(2).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(attribuees.get(2).statut()).isEqualTo("FACTUREE");
    }

    @Test
    void should_leave_the_forfait_not_attributable_when_two_lines_still_have_seances_to_attribute() {
        List<SeanceAttribuee> attribuees = AttributionDesForfaits.attribuer(List.of(LIGNE_HD, LIGNE_PREMIUM),
                List.of(seance(1, null), seance(3, null), seance(5, null)));

        assertThat(attribuees).extracting(SeanceAttribuee::ligne).containsOnlyNulls();
        assertThat(attribuees).noneMatch(SeanceAttribuee::estAttribuee);
    }

    @Test
    void should_leave_the_forfait_not_attributable_once_the_quantities_are_exhausted_or_the_imposed_forfait_has_no_line() {
        List<SeanceAttribuee> attribuees = AttributionDesForfaits.attribuer(List.of(LIGNE_HD),
                List.of(seance(1, null), seance(2, null), seance(3, null), seance(4, PREMIUM)));

        assertThat(attribuees).extracting(SeanceAttribuee::ligne).containsExactly(LIGNE_HD, LIGNE_HD, null, null);
    }

    @Test
    void should_still_give_the_imposed_forfait_line_when_its_quantity_is_already_used() {
        List<SeanceAttribuee> attribuees = AttributionDesForfaits.attribuer(List.of(LIGNE_PREMIUM),
                List.of(seance(1, PREMIUM), seance(2, PREMIUM)));

        assertThat(attribuees).extracting(SeanceAttribuee::ligne).containsExactly(LIGNE_PREMIUM, LIGNE_PREMIUM);
    }

    @Test
    void should_attribute_nothing_without_line_or_without_seance() {
        assertThat(AttributionDesForfaits.attribuer(List.of(), List.of(seance(1, null)))).extracting(SeanceAttribuee::ligne)
                .containsOnlyNulls();
        assertThat(AttributionDesForfaits.attribuer(List.of(LIGNE_HD), List.of())).isEmpty();
    }
}
