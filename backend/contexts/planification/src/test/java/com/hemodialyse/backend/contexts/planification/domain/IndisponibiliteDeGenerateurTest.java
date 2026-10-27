package com.hemodialyse.backend.contexts.planification.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndisponibiliteDeGenerateurTest {

    @Test
    void should_signal_a_generator_leaving_service_whatever_the_new_status() {
        for (String statut : List.of("HORS_SERVICE", "EN_MAINTENANCE", "A_REFORMER", "REFORME")) {
            assertThat(IndisponibiliteDeGenerateur.aSignaler("EN_SERVICE", statut)).as(statut).isTrue();
        }
    }

    @Test
    void should_not_signal_a_creation_a_return_to_service_or_a_change_between_unavailable_statuses() {
        assertThat(IndisponibiliteDeGenerateur.aSignaler(null, "HORS_SERVICE")).isFalse();
        assertThat(IndisponibiliteDeGenerateur.aSignaler("EN_SERVICE", "EN_SERVICE")).isFalse();
        assertThat(IndisponibiliteDeGenerateur.aSignaler("HORS_SERVICE", "REFORME")).isFalse();
        assertThat(IndisponibiliteDeGenerateur.aSignaler("EN_SERVICE", null)).isFalse();
    }

    @Test
    void should_quote_at_most_five_patients() {
        assertThat(IndisponibiliteDeGenerateur.citer(List.of("A", "B", "C", "D", "E", "F", "G"))).containsExactly("A", "B", "C", "D", "E");
        assertThat(IndisponibiliteDeGenerateur.citer(List.of())).isEmpty();
    }
}
