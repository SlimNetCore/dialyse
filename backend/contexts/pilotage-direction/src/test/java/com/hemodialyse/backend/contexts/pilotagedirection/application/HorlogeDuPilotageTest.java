package com.hemodialyse.backend.contexts.pilotagedirection.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB-DIR-03 (lot L6, ADR 0119) : « aujourd'hui » du Pilotage suit le fuseau de RÉFÉRENCE de l'application ({@code Africa/Algiers}, celui que
 * les autres contextes appliquent à un centre sans fuseau), pas celui de la JVM ; l'horodatage de constat reste en UTC. Les horloges sont
 * fixes : aucun test ne dépend de l'heure réelle.
 */
class HorlogeDuPilotageTest {

    private static final Instant TARD_LE_SOIR_EN_UTC = Instant.parse("2026-11-09T23:30:00Z");
    private static final ZoneId ALGER = ZoneId.of("Africa/Algiers");

    @Test
    void aujourdhui_est_la_date_dans_le_fuseau_de_lhorloge() {
        assertThat(new HorlogeDuPilotage(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.UTC)).aujourdhui()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(new HorlogeDuPilotage(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.ofHours(1))).aujourdhui())
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void lhorodatage_de_constat_est_en_utc_quel_que_soit_le_fuseau_de_lhorloge() {
        var maintenant = new HorlogeDuPilotage(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.ofHours(1))).maintenant();

        assertThat(maintenant.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(maintenant.toInstant()).isEqualTo(TARD_LE_SOIR_EN_UTC);
    }

    @Test
    void le_fuseau_de_reference_est_africa_algiers_par_defaut() {
        assertThat(HorlogeDuPilotage.FUSEAU_DE_REFERENCE).isEqualTo("Africa/Algiers");
        assertThat(HorlogeDuPilotage.PROPRIETE_DU_FUSEAU).endsWith(":Africa/Algiers}}");
    }

    @Test
    void a_23h30_utc_le_jour_du_pilotage_est_deja_le_lendemain_a_alger() {
        var horloge = HorlogeDuPilotage.auFuseauDeReference(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.UTC), ALGER);

        assertThat(horloge.aujourdhui()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void l_horloge_de_reference_ne_depend_pas_du_fuseau_de_l_horloge_des_instants() {
        var depuisUtc = HorlogeDuPilotage.auFuseauDeReference(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.UTC), ALGER);
        var depuisTokyo = HorlogeDuPilotage.auFuseauDeReference(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneId.of("Asia/Tokyo")), ALGER);

        assertThat(depuisTokyo.aujourdhui()).isEqualTo(depuisUtc.aujourdhui());
        assertThat(depuisTokyo.maintenant()).isEqualTo(depuisUtc.maintenant());
    }

    @Test
    void l_horodatage_de_constat_reste_en_utc_avec_le_fuseau_de_reference() {
        var maintenant = HorlogeDuPilotage.auFuseauDeReference(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.UTC), ALGER).maintenant();

        assertThat(maintenant.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(maintenant.toInstant()).isEqualTo(TARD_LE_SOIR_EN_UTC);
    }

    @Test
    void sans_fuseau_de_reference_le_fuseau_de_la_jvm_ne_compte_pas_pour_un_test_a_horloge_fixe() {
        // garde de régression : le fuseau d'une JVM en UTC+1 ne change rien à l'horloge de référence
        var horloge = HorlogeDuPilotage.auFuseauDeReference(Clock.fixed(TARD_LE_SOIR_EN_UTC, ZoneOffset.ofHours(-5)), ALGER);

        assertThat(horloge.aujourdhui()).isEqualTo(LocalDate.of(2026, 11, 10));
    }
}
