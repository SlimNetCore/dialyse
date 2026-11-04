package com.hemodialyse.backend.infrastructure.acl.centres;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le jour d'un centre est le jour de SON fuseau, jamais celui de la JVM ni d'UTC (KB-PAT-47). L'horloge est fixée à 23:30 UTC le
 * 3 novembre : à Alger (UTC+1) il est déjà 00:30 le 4, à New York (UTC-5) il est encore 18:30 le 3, à Kiritimati (UTC+14) il est
 * 13:30 le 4.
 */
class JourDuCentreLegacyTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-03T23:30:00Z"), ZoneOffset.UTC);
    private static final ZoneId PAR_DEFAUT = ZoneId.of("Africa/Algiers");
    private static final UUID ALGER = new UUID(1, 1);
    private static final UUID NEW_YORK = new UUID(1, 2);
    private static final UUID KIRITIMATI = new UUID(1, 3);
    private static final UUID SANS_FUSEAU = new UUID(1, 4);
    private static final UUID FUSEAU_ILLISIBLE = new UUID(1, 5);
    private static final UUID FUSEAU_VIDE = new UUID(1, 6);
    private static final UUID INCONNU = new UUID(1, 99);

    private final Map<UUID, String> fuseaux = Map.of(ALGER, "Africa/Algiers", NEW_YORK, " America/New_York ", KIRITIMATI,
            "Pacific/Kiritimati", FUSEAU_ILLISIBLE, "Mars/Phobos", FUSEAU_VIDE, "  ");

    private JourDuCentreLegacy jour(ZoneId defaut) {
        return new JourDuCentreLegacy(centre -> Optional.ofNullable(fuseaux.get(centre)).filter(f -> !f.isBlank()), HORLOGE, defaut);
    }

    @Test
    void should_rendre_le_jour_du_fuseau_explicite_du_centre_et_non_celui_d_utc() {
        JourDuCentreLegacy jour = jour(PAR_DEFAUT);

        assertThat(jour.aujourdhui(ALGER)).as("00:30 le 4 à Alger").isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(jour.aujourdhui(NEW_YORK)).as("18:30 le 3 à New York (espaces ignorés)").isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(jour.aujourdhui(KIRITIMATI)).as("13:30 le 4 à Kiritimati").isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(LocalDate.now(HORLOGE)).as("le jour d'UTC reste le 3").isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void should_se_replier_sur_le_fuseau_par_defaut_pour_un_centre_inconnu_sans_fuseau_ou_au_fuseau_illisible() {
        JourDuCentreLegacy parDefautAlger = jour(PAR_DEFAUT);
        JourDuCentreLegacy parDefautNewYork = jour(ZoneId.of("America/New_York"));

        for (UUID centre : new UUID[]{INCONNU, SANS_FUSEAU, FUSEAU_ILLISIBLE, FUSEAU_VIDE}) {
            assertThat(parDefautAlger.aujourdhui(centre)).as("défaut Alger : " + centre).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(parDefautNewYork.aujourdhui(centre)).as("défaut New York : " + centre).isEqualTo(LocalDate.of(2026, 11, 3));
        }
        assertThat(parDefautAlger.aujourdhui(null)).as("centre absent").isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    void should_exposer_le_fuseau_retenu() {
        JourDuCentreLegacy jour = jour(PAR_DEFAUT);

        assertThat(jour.fuseauDe(NEW_YORK)).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(jour.fuseauDe(FUSEAU_ILLISIBLE)).isEqualTo(PAR_DEFAUT);
        assertThat(jour.fuseauDe(null)).isEqualTo(PAR_DEFAUT);
    }

    @Test
    void should_suivre_la_bascule_de_minuit_du_centre() {
        Clock avantMinuitAlger = Clock.fixed(Instant.parse("2026-11-03T22:59:00Z"), ZoneOffset.UTC);
        JourDuCentreLegacy avant = new JourDuCentreLegacy(centre -> Optional.of("Africa/Algiers"), avantMinuitAlger, PAR_DEFAUT);
        JourDuCentreLegacy apres = new JourDuCentreLegacy(centre -> Optional.of("Africa/Algiers"), HORLOGE, PAR_DEFAUT);

        assertThat(avant.aujourdhui(ALGER)).as("23:59 à Alger").isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(apres.aujourdhui(ALGER)).as("00:30 à Alger").isEqualTo(LocalDate.of(2026, 11, 4));
    }
}
