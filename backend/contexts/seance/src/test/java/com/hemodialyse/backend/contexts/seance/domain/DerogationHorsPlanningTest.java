package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerogationHorsPlanningTest {

    @Test
    void should_trim_the_precision_and_keep_the_motif() {
        var derogation = new DerogationHorsPlanning(MotifHorsPlanning.RATTRAPAGE, "  Rattrapage apres un voyage ", false);
        assertThat(derogation.precision()).isEqualTo("Rattrapage apres un voyage");
        assertThat(derogation.motif()).isEqualTo(MotifHorsPlanning.RATTRAPAGE);
    }

    @Test
    void should_turn_a_blank_precision_into_none() {
        assertThat(new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, "   ", true).precision()).isNull();
        assertThat(new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, null, true).precision()).isNull();
    }

    @Test
    void should_refuse_a_missing_motif_with_the_legacy_code_and_message() {
        assertThatThrownBy(() -> new DerogationHorsPlanning(null, "x", false))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_DEROGATION_MOTIF_REQUIS");
                    assertThat(e).hasMessage("Le motif est obligatoire pour confirmer une séance hors planning");
                });
    }

    @Test
    void should_require_a_precision_for_the_motif_autre() {
        assertThatThrownBy(() -> new DerogationHorsPlanning(MotifHorsPlanning.AUTRE, " ", false))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_DEROGATION_PRECISION_REQUISE");
                    assertThat(e).hasMessage("Précisez le motif « autre » pour confirmer une séance hors planning");
                });
        assertThat(new DerogationHorsPlanning(MotifHorsPlanning.AUTRE, "Panne du poste", false).precision()).isEqualTo("Panne du poste");
    }

    @Test
    void should_accept_255_characters_and_refuse_256_as_an_invalid_input() {
        assertThat(new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, "x".repeat(255), false).precision()).hasSize(255);
        assertThatThrownBy(() -> new DerogationHorsPlanning(MotifHorsPlanning.URGENCE, "x".repeat(256), false))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_read_the_motif_ignoring_case_and_edge_spaces_and_refuse_an_unknown_one() {
        assertThat(MotifHorsPlanning.lire(" urgence ")).isEqualTo(MotifHorsPlanning.URGENCE);
        assertThatThrownBy(() -> MotifHorsPlanning.lire("VACANCES"))
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("SEANCE_DEROGATION_MOTIF_REQUIS");
                    assertThat(e).hasMessage("Motif de séance hors planning inconnu : VACANCES");
                });
    }
}
