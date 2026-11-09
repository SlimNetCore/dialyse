package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletMedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des volets médicaux : joué contre le double en mémoire, H2 et PostgreSQL 16 (les trois sous-classes). */
abstract class VoletsMedicauxContrat {

    static final CentreId A = new CentreId(new UUID(0, 0xA));
    static final CentreId B = new CentreId(new UUID(0, 0xB));
    private static final SeanceId SEANCE = new SeanceId(new UUID(2, 1));
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-15T11:30:00Z");

    protected abstract VoletsMedicaux volets();

    private static ObservationsMedicales complet() {
        return new ObservationsMedicales("Prescription", "Bonne", "RAS", "Normaux", "Aucun", "Stable");
    }

    @Test
    void should_find_nothing_for_a_session_without_a_volet_or_of_another_centre() {
        var v = volets();
        v.enregistrer(VoletMedical.ouvrir(new UUID(8, 1), A, SEANCE, complet(), T0));

        assertThat(v.deLaSeance(A, new SeanceId(new UUID(2, 2)))).isEmpty();
        assertThat(v.deLaSeance(B, SEANCE)).isEmpty();
    }

    @Test
    void should_store_a_first_volet_with_its_six_observations_and_its_dates() {
        var v = volets();
        v.enregistrer(VoletMedical.ouvrir(new UUID(8, 1), A, SEANCE, complet(), T0));

        var lu = v.deLaSeance(A, SEANCE).orElseThrow();
        assertThat(lu.id()).isEqualTo(new UUID(8, 1));
        assertThat(lu.observations()).isEqualTo(complet());
        assertThat(lu.creeLe()).isEqualTo(T0);
        assertThat(lu.modifieLe()).isEqualTo(T0);
    }

    @Test
    void should_replace_every_observation_and_the_modification_date_but_never_the_creation_date_nor_the_identity() {
        var v = volets();
        v.enregistrer(VoletMedical.ouvrir(new UUID(8, 1), A, SEANCE, complet(), T0));
        var lu = v.deLaSeance(A, SEANCE).orElseThrow();
        lu.remplacerLesObservations(new ObservationsMedicales(null, null, null, null, null, "Revue"), T1);
        v.enregistrer(lu);

        var apres = v.deLaSeance(A, SEANCE).orElseThrow();
        assertThat(apres.id()).isEqualTo(new UUID(8, 1));
        assertThat(apres.observations()).isEqualTo(new ObservationsMedicales(null, null, null, null, null, "Revue"));
        assertThat(apres.creeLe()).isEqualTo(T0);
        assertThat(apres.modifieLe()).isEqualTo(T1);
    }

    @Test
    void should_erase_the_volet_of_the_session_in_the_centre_only_and_ignore_a_missing_one() {
        var v = volets();
        v.enregistrer(VoletMedical.ouvrir(new UUID(8, 1), A, SEANCE, complet(), T0));
        v.enregistrer(VoletMedical.ouvrir(new UUID(8, 2), A, new SeanceId(new UUID(2, 2)), complet(), T0));

        v.effacer(B, SEANCE);
        assertThat(v.deLaSeance(A, SEANCE)).isPresent();
        v.effacer(A, SEANCE);
        v.effacer(A, SEANCE);

        assertThat(v.deLaSeance(A, SEANCE)).isEmpty();
        assertThat(v.deLaSeance(A, new SeanceId(new UUID(2, 2)))).isPresent();
    }

    @Test
    void should_keep_a_very_long_text_untouched_the_column_being_unbounded() {
        var v = volets();
        String long100000 = "x".repeat(100_000);
        v.enregistrer(VoletMedical.ouvrir(new UUID(8, 1), A, SEANCE, new ObservationsMedicales(long100000, null, null, null, null, null), T0));

        assertThat(v.deLaSeance(A, SEANCE).orElseThrow().observations().prescription()).isEqualTo(long100000);
    }
}
