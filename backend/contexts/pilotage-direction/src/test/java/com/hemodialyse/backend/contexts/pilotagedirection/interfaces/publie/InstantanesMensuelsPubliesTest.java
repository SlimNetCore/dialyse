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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   ", "{}", "[]", "null", "42", "\"x\"", "{\"overview\":{\"v\":1e999999}}"})
    void un_contenu_vide_non_objet_ou_non_fini_est_refuse_en_422_instantane_illisible(String contenu) {
        figerEnBase("2026-08", contenu);

        assertThatThrownBy(() -> publie.lire(SOCIETE, "2026-08")).isInstanceOfSatisfying(RegleDuPilotageViolee.class,
                e -> assertThat(e.code()).isEqualTo("INSTANTANE_ILLISIBLE"));
    }

    @Test
    void un_centre_de_un_a_quatre_patients_stocke_en_clair_est_masque_a_la_relecture_et_son_total_supprime() {
        figerEnBase("2026-08", "{\"overview\":{\"seuilAnonymat\":2,\"totaux\":{\"patients\":13},\"centres\":"
                + "[{\"nom\":\"Petit\",\"patients\":3},{\"nom\":\"Grand\",\"patients\":10},{\"nom\":\"Vide\",\"patients\":0}]}}");

        var vue = publie.lire(SOCIETE, "2026-08").overview();

        assertThat(vue.path("centres").get(0).path("patients").isNull()).isTrue();
        assertThat(vue.path("centres").get(1).path("patients").asInt()).isEqualTo(10);
        assertThat(vue.path("centres").get(2).path("patients").asInt()).isZero();
        assertThat(vue.path("totaux").path("patients").isNull()).isTrue();
    }

    @Test
    void une_ligne_dont_le_mois_n_est_pas_un_mois_lisible_n_est_pas_listee() {
        figerEnBase("2026-09", "{}");
        instantanes.inserer(SOCIETE, "abc", "{}");
        instantanes.inserer(SOCIETE, "2026-1", "{}");

        assertThat(publie.lister(SOCIETE)).extracting(i -> i.mois()).containsExactly("2026-09");
    }

    @Test
    void la_capture_horodate_avec_l_horloge_du_pilotage() {
        var cree = new CapturerUnInstantaneMensuel(instantanes, (s, d, f) -> new ContenuFige("{}"), HORLOGE).execute(SOCIETE, "2026-10");

        assertThat(cree.genereLe().toInstant()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
    }

    @Test
    void la_capture_d_un_mois_deja_fige_et_illisible_est_refusee_de_la_meme_facon() {
        figerEnBase("2026-08", "pas du json");

        assertThatThrownBy(() -> publie.figer(SOCIETE, "2026-08")).isInstanceOfSatisfying(RegleDuPilotageViolee.class,
                e -> assertThat(e.code()).isEqualTo("INSTANTANE_ILLISIBLE"));
    }
}
