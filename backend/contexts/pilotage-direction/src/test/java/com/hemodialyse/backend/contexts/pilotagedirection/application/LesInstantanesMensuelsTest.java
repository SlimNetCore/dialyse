package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ContenuFige;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des instantanés mensuels (PD2), avec des doubles en mémoire ; horloge fixée au 2026-11-09 (indépendant de l'heure réelle). */
class LesInstantanesMensuelsTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final HorlogeDuPilotage HORLOGE =
            new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

    private final InstantanesEnMemoire instantanes = new InstantanesEnMemoire();
    private final List<String> constitutions = new ArrayList<>();
    private final ContenuDuMoisAFiger contenu = (societe, debut, fin) -> {
        constitutions.add(societe + " " + debut + ".." + fin);
        return new ContenuFige("{\"bornes\":\"" + debut + ".." + fin + "\"}");
    };

    private String codeDuRefus(Runnable action) {
        try {
            action.run();
            return "aucun refus";
        } catch (RegleDuPilotageViolee e) {
            return e.code();
        }
    }

    @Test
    void la_capture_fige_le_mois_ecoule_avec_ses_bornes_et_le_relit() {
        var capturer = new CapturerUnInstantaneMensuel(instantanes, contenu, HORLOGE);

        var fige = capturer.execute(SOCIETE, "2026-10");

        assertThat(fige.mois()).isEqualTo("2026-10");
        assertThat(fige.contenu()).isEqualTo("{\"bornes\":\"2026-10-01..2026-10-31\"}");
        assertThat(fige.genereLe()).isNotNull();
    }

    @Test
    void la_seconde_capture_est_idempotente_et_ne_recalcule_rien() {
        var capturer = new CapturerUnInstantaneMensuel(instantanes, contenu, HORLOGE);
        var premiere = capturer.execute(SOCIETE, "2026-10");

        var seconde = capturer.execute(SOCIETE, "2026-10");

        assertThat(seconde).isEqualTo(premiere);
        assertThat(constitutions).hasSize(1);
        assertThat(instantanes.lister(SOCIETE)).hasSize(1);
    }

    @Test
    void la_capture_refuse_le_mois_courant_le_futur_le_trop_ancien_et_l_illisible_sans_rien_constituer() {
        var capturer = new CapturerUnInstantaneMensuel(instantanes, contenu, HORLOGE);

        assertThat(codeDuRefus(() -> capturer.execute(SOCIETE, "2026-11"))).isEqualTo("MOIS_NON_TERMINE");
        assertThat(codeDuRefus(() -> capturer.execute(SOCIETE, "2026-12"))).isEqualTo("MOIS_NON_TERMINE");
        assertThat(codeDuRefus(() -> capturer.execute(SOCIETE, "2021-10"))).isEqualTo("MOIS_TROP_ANCIEN");
        assertThat(codeDuRefus(() -> capturer.execute(SOCIETE, "2026-13"))).isEqualTo("MOIS_INVALIDE");
        assertThat(codeDuRefus(() -> capturer.execute(SOCIETE, "2021-11"))).isEqualTo("aucun refus");
        assertThat(constitutions).hasSize(1);
    }

    @Test
    void la_lecture_d_un_mois_non_fige_ou_futur_est_introuvable_et_celle_d_un_mois_illisible_invalide() {
        var consulter = new ConsulterUnInstantane(instantanes);

        assertThat(codeDuRefus(() -> consulter.execute(SOCIETE, "2026-10"))).isEqualTo("SNAPSHOT_INTROUVABLE");
        assertThat(codeDuRefus(() -> consulter.execute(SOCIETE, "2027-01"))).isEqualTo("SNAPSHOT_INTROUVABLE");
        assertThat(codeDuRefus(() -> consulter.execute(SOCIETE, "2026-13"))).isEqualTo("MOIS_INVALIDE");
    }

    @Test
    void la_liste_est_cloisonnee_par_societe() {
        new CapturerUnInstantaneMensuel(instantanes, contenu, HORLOGE).execute(SOCIETE, "2026-10");

        assertThat(new ListerLesInstantanes(instantanes).execute(SOCIETE)).hasSize(1);
        assertThat(new ListerLesInstantanes(instantanes).execute(UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_tache_du_premier_fige_le_mois_precedent_des_societes_actives_et_se_rejoue_sans_effet() {
        UUID autre = UUID.randomUUID();
        var tache = new CapturerLesInstantanesDuMoisEcoule(() -> List.of(SOCIETE, autre), instantanes, contenu, HORLOGE);

        assertThat(tache.execute()).isEqualTo(2);
        assertThat(instantanes.lister(SOCIETE)).extracting(InstantanesMensuels.InstantaneListe::mois).containsExactly("2026-10");
        assertThat(tache.execute()).isZero();
        assertThat(constitutions).hasSize(2);
    }

    @Test
    void l_echec_d_une_societe_n_empeche_pas_les_suivantes() {
        UUID enPanne = UUID.randomUUID();
        ContenuDuMoisAFiger capricieux = (societe, debut, fin) -> {
            if (societe.equals(enPanne)) throw new IllegalStateException("indicateurs indisponibles");
            return contenu.constituer(societe, debut, fin);
        };
        var tache = new CapturerLesInstantanesDuMoisEcoule(() -> List.of(enPanne, SOCIETE), instantanes, capricieux, HORLOGE);

        assertThat(tache.execute()).isEqualTo(1);
        assertThat(instantanes.lister(enPanne)).isEmpty();
        assertThat(instantanes.lister(SOCIETE)).hasSize(1);
    }

    @Test
    void un_instantane_existant_n_est_jamais_remplace_par_la_capture() {
        var capturer = new CapturerUnInstantaneMensuel(instantanes, contenu, HORLOGE);
        capturer.execute(SOCIETE, "2026-10");

        assertThatThrownBy(() -> capturer.execute(SOCIETE, "2026-11")).isInstanceOf(RegleDuPilotageViolee.class);
        assertThat(new ConsulterUnInstantane(instantanes).execute(SOCIETE, "2026-10").contenu()).contains("2026-10-01..2026-10-31");
    }
}
