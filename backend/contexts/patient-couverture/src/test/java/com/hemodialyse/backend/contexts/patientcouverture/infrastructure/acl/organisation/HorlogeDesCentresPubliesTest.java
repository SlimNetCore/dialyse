package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le jour d'un centre est le jour de SON fuseau, jamais celui de la JVM ni d'UTC (KB-PAT-47, PL3 : ex {@code JourDuCentreLegacyTest}). L'horloge
 * est fixée à 23:30 UTC le 3 novembre : à Alger (UTC+1) il est déjà 00:30 le 4, à New York (UTC-5) il est encore 18:30 le 3, à Kiritimati
 * (UTC+14) il est 13:30 le 4. Un centre inconnu ou sans fuseau prend le fuseau par défaut.
 */
class HorlogeDesCentresPubliesTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-03T23:30:00Z"), ZoneOffset.UTC);
    private static final ZoneId PAR_DEFAUT = ZoneId.of("Africa/Algiers");
    private static final CentreId ALGER = new CentreId(new UUID(1, 1));
    private static final CentreId NEW_YORK = new CentreId(new UUID(1, 2));
    private static final CentreId KIRITIMATI = new CentreId(new UUID(1, 3));
    private static final CentreId SANS_FUSEAU = new CentreId(new UUID(1, 4));
    private static final CentreId INCONNU = new CentreId(new UUID(1, 99));

    private final Map<UUID, ZoneId> fuseaux = Map.of(ALGER.valeur(), ZoneId.of("Africa/Algiers"), NEW_YORK.valeur(),
            ZoneId.of("America/New_York"), KIRITIMATI.valeur(), ZoneId.of("Pacific/Kiritimati"));

    private final AnnuaireDesCentres annuaire = new AnnuaireDesCentres() {
        @Override
        public List<UUID> actifs() {
            return List.of();
        }

        @Override
        public List<UUID> tous() {
            return List.of();
        }

        @Override
        public List<CentreNomme> tousAvecNom() {
            return List.of();
        }

        @Override
        public Optional<String> nomDuCentre(UUID centre) {
            return Optional.empty();
        }

        @Override
        public Optional<ZoneId> fuseauHoraireDe(UUID centre) {
            return Optional.ofNullable(fuseaux.get(centre));
        }
    };

    private HorlogeDesCentresPublies horloge(ZoneId defaut, Clock horloge) {
        return new HorlogeDesCentresPublies(annuaire, horloge, defaut);
    }

    @Test
    void should_rendre_le_jour_du_fuseau_explicite_du_centre_et_non_celui_d_utc() {
        HorlogeDesCentresPublies jour = horloge(PAR_DEFAUT, HORLOGE);

        assertThat(jour.aujourdhui(ALGER)).as("00:30 le 4 à Alger").isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(jour.aujourdhui(NEW_YORK)).as("18:30 le 3 à New York").isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(jour.aujourdhui(KIRITIMATI)).as("13:30 le 4 à Kiritimati").isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(LocalDate.now(HORLOGE)).as("le jour d'UTC reste le 3").isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void should_se_replier_sur_le_fuseau_par_defaut_pour_un_centre_inconnu_ou_sans_fuseau() {
        HorlogeDesCentresPublies parDefautAlger = horloge(PAR_DEFAUT, HORLOGE);
        HorlogeDesCentresPublies parDefautNewYork = horloge(ZoneId.of("America/New_York"), HORLOGE);

        for (CentreId centre : new CentreId[]{INCONNU, SANS_FUSEAU}) {
            assertThat(parDefautAlger.aujourdhui(centre)).as("défaut Alger : " + centre).isEqualTo(LocalDate.of(2026, 11, 4));
            assertThat(parDefautNewYork.aujourdhui(centre)).as("défaut New York : " + centre).isEqualTo(LocalDate.of(2026, 11, 3));
        }
    }

    @Test
    void should_suivre_la_bascule_de_minuit_du_centre() {
        Clock avantMinuitAlger = Clock.fixed(Instant.parse("2026-11-03T22:59:00Z"), ZoneOffset.UTC);

        assertThat(horloge(PAR_DEFAUT, avantMinuitAlger).aujourdhui(ALGER)).as("23:59 à Alger").isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(horloge(PAR_DEFAUT, HORLOGE).aujourdhui(ALGER)).as("00:30 à Alger").isEqualTo(LocalDate.of(2026, 11, 4));
    }

    @Test
    void should_donner_l_instant_de_l_horloge() {
        assertThat(horloge(PAR_DEFAUT, HORLOGE).maintenant()).isEqualTo(Instant.parse("2026-11-03T23:30:00Z"));
    }
}
