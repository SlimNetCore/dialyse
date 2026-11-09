package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.EvolutionMensuelle.PointDuMois;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionMensuelleTest {

    @Test
    void les_mois_de_tous_les_centres_sont_cumules_et_tries_par_mois() {
        var points = List.of(new PointDuMois("2026-03", 10, 100.5, 119.5), new PointDuMois("2026-01", 4, 40, 47.6),
                new PointDuMois("2026-03", 5, 50.25, 60));

        var mois = EvolutionMensuelle.cumuler(points);

        assertThat(mois).extracting(m -> m.mois()).containsExactly("2026-01", "2026-03");
        assertThat(mois.get(1).seances()).isEqualTo(15);
        assertThat(mois.get(1).caHt()).isEqualTo(150.75);
        assertThat(mois.get(1).caTtc()).isEqualTo(179.5);
    }

    @Test
    void les_seances_cumulees_sont_arrondies_au_plus_proche_comme_le_legacy() {
        var mois = EvolutionMensuelle.cumuler(List.of(new PointDuMois("2026-02", 1.4, 0, 0), new PointDuMois("2026-02", 1.2, 0, 0)));

        assertThat(mois.getFirst().seances()).isEqualTo(3);
    }

    @Test
    void sans_point_il_ny_a_aucun_mois() {
        assertThat(EvolutionMensuelle.cumuler(List.of())).isEmpty();
    }
}
