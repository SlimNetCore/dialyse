package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat du calendrier du centre : joué contre le double en mémoire, H2 et PostgreSQL 16 (les trois sous-classes). Une nature = une
 * table : les deux ne se mélangent jamais.
 */
abstract class CalendrierDuCentreContrat {

    static final CentreId A = new CentreId(new UUID(0, 0xA));
    static final CentreId B = new CentreId(new UUID(0, 0xB));
    private static final MoisDuCalendrier JANVIER = new MoisDuCalendrier(2026, 1);

    protected abstract CalendrierDuCentre calendrier();

    private static JourDuCalendrier ferie(CentreId centre, int n, LocalDate jour, String libelle) {
        return new JourDuCalendrier(new UUID(3, n), centre, jour, NatureDeJour.JOUR_FERIE, libelle);
    }

    private static JourDuCalendrier fermeture(CentreId centre, int n, LocalDate jour, String motif) {
        return new JourDuCalendrier(new UUID(4, n), centre, jour, NatureDeJour.FERMETURE_EXCEPTIONNELLE, motif);
    }

    @Test
    void should_list_the_days_of_the_month_oldest_first_with_both_bounds_included() {
        var c = calendrier();
        c.ajouter(ferie(A, 1, LocalDate.of(2026, 1, 31), "Dernier"));
        c.ajouter(ferie(A, 2, LocalDate.of(2026, 1, 1), "Premier"));
        c.ajouter(ferie(A, 3, LocalDate.of(2025, 12, 31), "Veille"));
        c.ajouter(ferie(A, 4, LocalDate.of(2026, 2, 1), "Lendemain"));

        assertThat(c.duMois(A, NatureDeJour.JOUR_FERIE, JANVIER)).extracting(JourDuCalendrier::libelle).containsExactly("Premier", "Dernier");
    }

    @Test
    void should_keep_holidays_and_closures_apart_and_never_mix_two_centres() {
        var c = calendrier();
        c.ajouter(ferie(A, 1, LocalDate.of(2026, 1, 10), "Férié A"));
        c.ajouter(fermeture(A, 2, LocalDate.of(2026, 1, 10), "Fermeture A"));
        c.ajouter(ferie(B, 3, LocalDate.of(2026, 1, 10), "Férié B"));

        assertThat(c.duMois(A, NatureDeJour.JOUR_FERIE, JANVIER)).extracting(JourDuCalendrier::libelle).containsExactly("Férié A");
        assertThat(c.duMois(A, NatureDeJour.FERMETURE_EXCEPTIONNELLE, JANVIER)).extracting(JourDuCalendrier::libelle)
                .containsExactly("Fermeture A");
        assertThat(c.duMois(B, NatureDeJour.FERMETURE_EXCEPTIONNELLE, JANVIER)).isEmpty();
    }

    @Test
    void should_refuse_a_second_day_of_the_same_nature_on_the_same_date_for_the_same_centre_only() {
        var c = calendrier();
        c.ajouter(ferie(A, 1, LocalDate.of(2026, 1, 10), "Premier"));

        assertThatThrownBy(() -> c.ajouter(ferie(A, 2, LocalDate.of(2026, 1, 10), "Second"))).isInstanceOf(JourDejaAuCalendrier.class);
        c.ajouter(fermeture(A, 3, LocalDate.of(2026, 1, 10), "Autre nature"));
        c.ajouter(ferie(B, 4, LocalDate.of(2026, 1, 10), "Autre centre"));
        assertThat(c.duMois(A, NatureDeJour.JOUR_FERIE, JANVIER)).extracting(JourDuCalendrier::libelle).containsExactly("Premier");
    }

    @Test
    void should_store_a_missing_label_and_a_label_of_exactly_255_characters() {
        var c = calendrier();
        c.ajouter(ferie(A, 1, LocalDate.of(2026, 1, 10), null));
        c.ajouter(fermeture(A, 2, LocalDate.of(2026, 1, 11), "m".repeat(255)));

        assertThat(c.duMois(A, NatureDeJour.JOUR_FERIE, JANVIER).get(0).libelle()).isNull();
        assertThat(c.duMois(A, NatureDeJour.FERMETURE_EXCEPTIONNELLE, JANVIER).get(0).libelle()).hasSize(255);
    }

    @Test
    void should_accept_a_day_in_year_1900() {
        var c = calendrier();
        c.ajouter(ferie(A, 1, LocalDate.of(1900, 1, 1), "Ancien"));

        assertThat(c.duMois(A, NatureDeJour.JOUR_FERIE, new MoisDuCalendrier(1900, 1))).hasSize(1);
    }

    @Test
    void should_remove_only_the_day_of_that_nature_and_that_centre_and_say_so() {
        var c = calendrier();
        c.ajouter(ferie(A, 1, LocalDate.of(2026, 1, 10), "Férié A"));
        c.ajouter(ferie(B, 2, LocalDate.of(2026, 1, 10), "Férié B"));

        assertThat(c.retirer(A, NatureDeJour.FERMETURE_EXCEPTIONNELLE, new UUID(3, 1))).isFalse();
        assertThat(c.retirer(A, NatureDeJour.JOUR_FERIE, new UUID(3, 2))).isFalse();
        assertThat(c.retirer(A, NatureDeJour.JOUR_FERIE, new UUID(3, 99))).isFalse();
        assertThat(c.duMois(B, NatureDeJour.JOUR_FERIE, JANVIER)).hasSize(1);
        assertThat(c.retirer(A, NatureDeJour.JOUR_FERIE, new UUID(3, 1))).isTrue();
        assertThat(c.retirer(A, NatureDeJour.JOUR_FERIE, new UUID(3, 1))).isFalse();
        assertThat(c.duMois(A, NatureDeJour.JOUR_FERIE, JANVIER)).isEmpty();
    }
}
