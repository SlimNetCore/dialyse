package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les règles du mois d'un instantané mensuel (PD2, ADR 0103). Indépendant de l'heure : le mois courant est toujours passé en paramètre. */
class LeMoisDUnInstantaneTest {

    private static final YearMonth COURANT = YearMonth.of(2026, 11);

    private static String codeDuRefus(Runnable action) {
        try {
            action.run();
            return "aucun refus";
        } catch (RegleDuPilotageViolee e) {
            return e.code();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "abcd-ef", "2026-1", "2026-01-01", "202601", "2026-00", "", " 2026-01"})
    void un_mois_illisible_est_refuse_comme_mois_invalide(String texte) {
        assertThat(codeDuRefus(() -> MoisDInstantane.lire(texte))).isEqualTo("MOIS_INVALIDE");
    }

    @Test
    void un_mois_absent_est_refuse_comme_mois_invalide() {
        assertThat(codeDuRefus(() -> MoisDInstantane.lire(null))).isEqualTo("MOIS_INVALIDE");
    }

    @Test
    void un_mois_lisible_garde_son_ecriture_et_ses_bornes() {
        MoisDInstantane fevrier = MoisDInstantane.lire("2024-02");

        assertThat(fevrier.texte()).isEqualTo("2024-02");
        assertThat(fevrier.debut()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(fevrier.fin()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void le_mois_courant_et_un_mois_futur_ne_sont_pas_terminés() {
        assertThat(codeDuRefus(() -> MoisDInstantane.lire("2026-11").exigerCapturable(COURANT))).isEqualTo("MOIS_NON_TERMINE");
        assertThat(codeDuRefus(() -> MoisDInstantane.lire("2026-12").exigerCapturable(COURANT))).isEqualTo("MOIS_NON_TERMINE");
    }

    @Test
    void le_mois_precedent_est_capturable() {
        assertThat(codeDuRefus(() -> MoisDInstantane.lire("2026-10").exigerCapturable(COURANT))).isEqualTo("aucun refus");
    }

    @Test
    void soixante_mois_en_arriere_est_la_borne_incluse_et_soixante_et_un_est_trop_ancien() {
        assertThat(codeDuRefus(() -> new MoisDInstantane(COURANT.minusMonths(60)).exigerCapturable(COURANT))).isEqualTo("aucun refus");
        assertThat(codeDuRefus(() -> new MoisDInstantane(COURANT.minusMonths(61)).exigerCapturable(COURANT))).isEqualTo("MOIS_TROP_ANCIEN");
    }

    @Test
    void le_mois_que_fige_la_tache_du_premier_est_le_precedent() {
        assertThat(MoisDInstantane.precedent(YearMonth.of(2026, 1)).texte()).isEqualTo("2025-12");
    }

    @Test
    void un_instantane_ne_se_fige_que_pour_un_mois_ecoule() {
        UUID societe = UUID.randomUUID();
        ContenuFige contenu = new ContenuFige("{}");

        InstantaneMensuel fige = InstantaneMensuel.figer(societe, MoisDInstantane.lire("2026-10"), COURANT, contenu);

        assertThat(fige.societeId()).isEqualTo(societe);
        assertThat(fige.mois().texte()).isEqualTo("2026-10");
        assertThat(fige.contenu()).isEqualTo(contenu);
        assertThatThrownBy(() -> InstantaneMensuel.figer(societe, MoisDInstantane.lire("2026-11"), COURANT, contenu))
                .isInstanceOf(RegleDuPilotageViolee.class);
    }

    @Test
    void la_tache_du_premier_fige_le_mois_precedent() {
        InstantaneMensuel fige = InstantaneMensuel.figerLeMoisEcoule(UUID.randomUUID(), COURANT, new ContenuFige("{}"));

        assertThat(fige.mois().texte()).isEqualTo("2026-10");
    }
}
