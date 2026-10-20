package com.hemodialyse.backend.contexts.facturation.domain.seance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MotifDAbsenceTest {

    @Test
    void should_read_a_reason_by_its_name_ignoring_surrounding_spaces() {
        assertThat(MotifDAbsence.depuis(" MALADIE ")).isEqualTo(MotifDAbsence.MALADIE);
        assertThat(MotifDAbsence.depuis("NON_JUSTIFIEE")).isEqualTo(MotifDAbsence.NON_JUSTIFIEE);
    }

    @Test
    void should_refuse_a_missing_reason() {
        assertThatThrownBy(() -> MotifDAbsence.depuis(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le motif d'exclusion est obligatoire");
        assertThatThrownBy(() -> MotifDAbsence.depuis(" ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le motif d'exclusion est obligatoire");
    }

    @Test
    void should_refuse_an_unknown_reason() {
        assertThatThrownBy(() -> MotifDAbsence.depuis("PARESSE")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Motif d'exclusion inconnu : PARESSE");
        assertThatThrownBy(() -> MotifDAbsence.depuis("maladie")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_require_a_comment_only_for_the_reason_autre() {
        for (MotifDAbsence motif : MotifDAbsence.values()) {
            assertThat(motif.commentaireRequis()).as(motif.name()).isEqualTo(motif == MotifDAbsence.AUTRE);
        }
    }
}
