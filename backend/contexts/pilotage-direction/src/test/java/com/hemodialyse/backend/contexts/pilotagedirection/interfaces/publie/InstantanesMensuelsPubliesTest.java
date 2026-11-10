package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerLesInstantanesDuMoisEcoule;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerUnInstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterUnInstantane;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HorlogeDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesEnMemoire;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ListerLesInstantanes;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ContenuFige;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KB-DIR-06 (e), lot L6 : un contenu d'instantané illisible est un refus métier 422 {@code INSTANTANE_ILLISIBLE} à la lecture (et non une
 * erreur technique 500) ; il reste listé (la liste n'ouvre pas le contenu). Horloge fixe : indépendant de l'heure réelle.
 */
class InstantanesMensuelsPubliesTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final HorlogeDuPilotage HORLOGE = new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));

    private final InstantanesEnMemoire instantanes = new InstantanesEnMemoire();
    private final InstantanesMensuelsPublies publie = new InstantanesMensuelsPublies(new ListerLesInstantanes(instantanes),
            new ConsulterUnInstantane(instantanes), new CapturerUnInstantaneMensuel(instantanes, (s, d, f) -> new ContenuFige("pas du json"), HORLOGE),
            new CapturerLesInstantanesDuMoisEcoule(() -> List.of(), instantanes, (s, d, f) -> new ContenuFige("{}"), HORLOGE), new ObjectMapper());

    private void figerEnBase(String mois, String contenu) {
        instantanes.enregistrer(InstantaneMensuel.figer(SOCIETE, MoisDInstantane.lire(mois), YearMonth.of(2026, 11), new ContenuFige(contenu)));
    }

    @Test
    void un_contenu_illisible_est_refuse_en_422_instantane_illisible_sans_detail_technique() {
        figerEnBase("2026-08", "pas du json");

        assertThatThrownBy(() -> publie.lire(SOCIETE, "2026-08")).isInstanceOfSatisfying(RegleDuPilotageViolee.class, e -> {
            assertThat(e.code()).isEqualTo("INSTANTANE_ILLISIBLE");
            assertThat(e.getMessage()).doesNotContain("Unrecognized", "token", "pas du json");
        });
    }

    @Test
    void un_contenu_illisible_reste_liste_et_ne_bloque_pas_la_lecture_des_autres_mois() {
        figerEnBase("2026-08", "pas du json");
        figerEnBase("2026-09", "{\"overview\":{\"totaux\":{\"seances\":3}},\"indicators\":{}}");

        assertThat(publie.lister(SOCIETE)).extracting(i -> i.mois()).containsExactly("2026-09", "2026-08");
        assertThat(publie.lire(SOCIETE, "2026-09").overview().path("totaux").path("seances").asInt()).isEqualTo(3);
    }

    @Test
    void la_capture_d_un_mois_deja_fige_et_illisible_est_refusee_de_la_meme_facon() {
        figerEnBase("2026-08", "pas du json");

        assertThatThrownBy(() -> publie.figer(SOCIETE, "2026-08")).isInstanceOfSatisfying(RegleDuPilotageViolee.class,
                e -> assertThat(e.code()).isEqualTo("INSTANTANE_ILLISIBLE"));
    }
}
