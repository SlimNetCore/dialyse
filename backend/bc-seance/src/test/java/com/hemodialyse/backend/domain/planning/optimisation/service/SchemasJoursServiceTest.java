package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.service.SchemasJoursService.Schema;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.DIMANCHE;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemasJoursServiceTest {

    @Test
    void should_rank_the_best_spaced_patterns_first_and_drop_the_poorly_spaced_ones() {
        List<Schema> schemas = SchemasJoursService.candidats(3, EnumSet.allOf(JourSemaine.class));

        assertThat(schemas).isNotEmpty().hasSizeLessThanOrEqualTo(SchemasJoursService.SCHEMAS_MAX);
        int meilleur = schemas.getFirst().espacement();
        assertThat(schemas).allSatisfy(s -> {
            assertThat(s.jours()).hasSize(3);
            assertThat(s.espacement()).isBetween(meilleur - SchemasJoursService.ECART_ESPACEMENT_TOLERE, meilleur);
        });
        assertThat(schemas).noneMatch(s -> s.jours().containsAll(Set.of(LUNDI, MARDI, MERCREDI)));
    }

    @Test
    void should_only_use_the_opening_days_of_the_center() {
        Set<JourSemaine> ouverts = EnumSet.of(DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI);

        assertThat(SchemasJoursService.candidats(2, ouverts)).allSatisfy(s -> assertThat(ouverts).containsAll(s.jours()));
        assertThat(SchemasJoursService.candidats(6, ouverts)).as("pas assez de jours ouverts").isEmpty();
    }

    @Test
    void should_reject_an_impossible_number_of_sessions() {
        assertThatThrownBy(() -> SchemasJoursService.candidats(0, EnumSet.allOf(JourSemaine.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SchemasJoursService.candidats(8, EnumSet.allOf(JourSemaine.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
