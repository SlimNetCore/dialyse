package com.hemodialyse.backend.contexts.facturation.domain.seance;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeanceDeFacturationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000a"));
    private static final UUID ABSENCE = UUID.fromString("99996800-0000-0000-0000-0000000ab001");

    private static SeanceDeFacturation seance(String statut, boolean facturee) {
        return new SeanceDeFacturation(UUID.randomUUID(), CENTRE, UUID.randomUUID(), LocalDate.of(2026, 8, 2),
                new StatutDeSeance(statut), facturee, null, null);
    }

    private static SeanceDeFacturation exclue(String statutAvant, boolean facturee) {
        return new SeanceDeFacturation(UUID.randomUUID(), CENTRE, UUID.randomUUID(), LocalDate.of(2026, 8, 2),
                StatutDeSeance.ABSENT, facturee, statutAvant == null ? null : new StatutDeSeance(statutAvant), ABSENCE);
    }

    // ---- exclusion

    @Test
    void should_exclude_a_validated_seance_and_remember_its_status() {
        Exclusion exclusion = seance("VALIDEE", false).exclure(MotifDAbsence.MALADIE, "  grippe  ");

        assertThat(exclusion.statutAvant()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(exclusion.motif()).isEqualTo(MotifDAbsence.MALADIE);
        assertThat(exclusion.commentaire()).isEqualTo("grippe");
    }

    @Test
    void should_exclude_a_signed_seance_and_remember_its_status() {
        assertThat(seance("SIGNEE", false).exclure(MotifDAbsence.VOYAGE, null).statutAvant()).isEqualTo(StatutDeSeance.SIGNEE);
    }

    @Test
    void should_keep_no_comment_when_it_is_blank() {
        assertThat(seance("VALIDEE", false).exclure(MotifDAbsence.MALADIE, "   ").commentaire()).isNull();
    }

    @Test
    void should_refuse_to_exclude_a_seance_that_is_not_validated_or_signed() {
        for (String statut : new String[]{"BROUILLON", "ABSENT", "FACTUREE", "validee"}) {
            assertThatThrownBy(() -> seance(statut, false).exclure(MotifDAbsence.MALADIE, null))
                    .as(statut).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Seance introuvable ou non modifiable pour exclusion");
        }
    }

    @Test
    void should_refuse_to_exclude_a_seance_already_invoiced() {
        assertThatThrownBy(() -> seance("VALIDEE", true).exclure(MotifDAbsence.MALADIE, null))
                .isInstanceOf(IllegalStateException.class).hasMessage("Seance introuvable ou non modifiable pour exclusion");
    }

    @Test
    void should_require_a_reason_to_exclude() {
        assertThatThrownBy(() -> seance("VALIDEE", false).exclure(null, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Le motif d'exclusion est obligatoire");
    }

    @Test
    void should_require_a_comment_with_the_reason_autre() {
        assertThatThrownBy(() -> seance("VALIDEE", false).exclure(MotifDAbsence.AUTRE, " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Un commentaire est obligatoire pour le motif AUTRE");
        assertThat(seance("VALIDEE", false).exclure(MotifDAbsence.AUTRE, "déménagement").commentaire()).isEqualTo("déménagement");
    }

    @Test
    void should_check_the_state_of_the_seance_before_the_reason() {
        assertThatThrownBy(() -> seance("FACTUREE", false).exclure(null, null)).isInstanceOf(IllegalStateException.class);
    }

    // ---- réinclusion

    @Test
    void should_reinclude_an_excluded_seance_with_its_former_status_and_give_back_the_absence_to_remove() {
        Reinclusion reinclusion = exclue("SIGNEE", false).reinclure();

        assertThat(reinclusion.statutRetabli()).isEqualTo(StatutDeSeance.SIGNEE);
        assertThat(reinclusion.absenceARetirer()).isEqualTo(ABSENCE);
    }

    @Test
    void should_refuse_to_reinclude_a_seance_that_was_not_excluded_by_the_facturation() {
        assertThatThrownBy(() -> seance("VALIDEE", false).reinclure()).isInstanceOf(IllegalStateException.class)
                .hasMessage("Seance introuvable ou non reinclusable");
        assertThatThrownBy(() -> seance("ABSENT", false).reinclure()).isInstanceOf(IllegalStateException.class)
                .hasMessage("Seance introuvable ou non reinclusable");
    }

    @Test
    void should_refuse_to_reinclude_an_invoiced_seance() {
        assertThatThrownBy(() -> exclue("VALIDEE", true).reinclure()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_refuse_to_reinclude_when_the_remembered_status_is_not_billable() {
        assertThatThrownBy(() -> exclue("BROUILLON", false).reinclure()).isInstanceOf(IllegalStateException.class);
    }

    // ---- changement de forfait

    @Test
    void should_allow_a_forfait_change_on_a_validated_or_signed_seance_not_invoiced() {
        seance("VALIDEE", false).verifierQueLeForfaitPeutChanger();
        seance("SIGNEE", false).verifierQueLeForfaitPeutChanger();
    }

    @Test
    void should_refuse_a_forfait_change_on_a_seance_invoiced_or_excluded() {
        for (SeanceDeFacturation seance : new SeanceDeFacturation[]{seance("VALIDEE", true), seance("FACTUREE", false),
                seance("BROUILLON", false), exclue("VALIDEE", false)}) {
            assertThatThrownBy(seance::verifierQueLeForfaitPeutChanger).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Seance introuvable ou non modifiable pour changement forfait");
        }
    }
}
