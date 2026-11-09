package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation.JourDuCentrePublie;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DateTimeException;
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
 * SE7f (ADR 0100) puis O4 lot A (ADR 0107) : le jour du centre lu dans le langage publié d'Organisation : fuseau du centre, défaut sinon,
 * jamais le jour de la JVM. Mêmes scénarios que l'ancien {@code JourDuCentreJdbcTest}.
 */
class JourDuCentrePublieTest {

    private static final UUID ALGER = new UUID(0, 1);
    private static final UUID TOKYO = new UUID(0, 2);
    private static final UUID SANS_FUSEAU = new UUID(0, 3);
    private static final UUID ILLISIBLE = new UUID(0, 4);
    private static final Map<UUID, String> FUSEAUX = Map.of(ALGER, "Africa/Algiers", TOKYO, "Asia/Tokyo", SANS_FUSEAU, "   ", ILLISIBLE, "Mars/Olympus");

    /** 2026-06-15 20:00 UTC : déjà le 16 à Tokyo (05:00), encore le 15 à Alger (21:00). */
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-06-15T20:00:00Z"), ZoneOffset.UTC);

    /** Annuaire de test : ne publie un fuseau que s'il est lisible, comme le langage publié. */
    private static final AnnuaireDesCentres ANNUAIRE = new AnnuaireDesCentres() {
        @Override
        public List<UUID> actifs() {
            return List.of();
        }

        @Override
        public List<UUID> tous() {
            return List.of();
        }

        @Override
        public Optional<ZoneId> fuseauHoraireDe(UUID centre) {
            String identifiant = FUSEAUX.get(centre);
            if (identifiant == null || identifiant.isBlank()) return Optional.empty();
            try {
                return Optional.of(ZoneId.of(identifiant.trim()));
            } catch (DateTimeException e) {
                return Optional.empty();
            }
        }
    };

    private static JourDuCentrePublie jour() {
        return new JourDuCentrePublie(ANNUAIRE, HORLOGE, ZoneId.of("Africa/Algiers"));
    }

    @Test
    void should_give_today_in_the_centre_time_zone() {
        assertThat(jour().apply(TOKYO)).isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(jour().apply(ALGER)).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void should_fall_back_on_the_default_zone_for_an_unknown_centre_a_blank_or_an_unreadable_zone() {
        assertThat(jour().apply(SANS_FUSEAU)).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(jour().apply(ILLISIBLE)).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(jour().apply(new UUID(0, 9))).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(jour().apply(null)).isEqualTo(LocalDate.of(2026, 6, 15));
    }
}
