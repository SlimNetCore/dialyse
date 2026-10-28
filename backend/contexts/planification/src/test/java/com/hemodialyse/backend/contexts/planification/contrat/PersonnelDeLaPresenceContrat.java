package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du personnel de la présence et des centres actifs : le double en mémoire et les adaptateurs JDBC (H2 et PostgreSQL 16) passent
 * les mêmes tests.
 */
abstract class PersonnelDeLaPresenceContrat {
    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997814-0000-0000-0000-0000000000a1"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99997814-0000-0000-0000-0000000000a2"));
    protected static final UUID SALLE = UUID.fromString("99997814-0000-0000-0000-0000000000b1");
    protected static final UUID CRENEAU = UUID.fromString("99997814-0000-0000-0000-0000000000c1");
    private static final LocalDate DU = LocalDate.of(2026, 11, 1);
    private static final LocalDate AU = LocalDate.of(2026, 11, 7);

    protected abstract PersonnelDeLaPresence personnel();

    protected abstract CentresActifs centresActifs();

    protected abstract void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif, boolean habilite);

    protected abstract void affectation(CentreId centre, UUID infirmier, Set<DayOfWeek> jours);

    protected abstract void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin);

    protected abstract void remplacement(CentreId centre, UUID infirmier, LocalDate date);

    protected abstract void ratio(CentreId centre, Integer valeur);

    protected abstract void centre(CentreId centre, boolean actif);

    @Test
    void should_read_only_the_active_nurses_of_the_center_sorted_by_name_then_first_name() {
        UUID benali = UUID.randomUUID();
        UUID amraniSara = UUID.randomUUID();
        UUID amraniKarim = UUID.randomUUID();
        infirmier(CENTRE, benali, "Benali", null, true, true);
        infirmier(CENTRE, amraniSara, "Amrani", "Sara", true, false);
        infirmier(CENTRE, amraniKarim, "Amrani", "Karim", true, false);
        infirmier(CENTRE, UUID.randomUUID(), "Zebda", "Inactif", false, false);
        infirmier(AUTRE, UUID.randomUUID(), "Etranger", "Autre", true, false);

        Personnel lu = personnel().lire(CENTRE, DU, AU);

        assertThat(lu.infirmiers()).extracting(i -> i.id()).containsExactly(amraniKarim, amraniSara, benali);
        assertThat(lu.infirmiers().get(2).habiliteIsolement()).isTrue();
        assertThat(lu.infirmiers().get(2).nomAffiche()).isEqualTo("Benali");
        assertThat(lu.infirmiers().get(0).nomAffiche()).isEqualTo("Karim Amrani");
    }

    @Test
    void should_read_the_rotation_of_active_nurses_only() {
        UUID actif = UUID.randomUUID();
        UUID inactif = UUID.randomUUID();
        infirmier(CENTRE, actif, "Actif", null, true, false);
        infirmier(CENTRE, inactif, "Inactif", null, false, false);
        affectation(CENTRE, actif, Set.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY));
        affectation(CENTRE, inactif, Set.of(DayOfWeek.MONDAY));

        Personnel lu = personnel().lire(CENTRE, DU, AU);

        assertThat(lu.affectations()).hasSize(1);
        assertThat(lu.affectations().getFirst().infirmier()).isEqualTo(actif);
        assertThat(lu.affectations().getFirst().salle()).isEqualTo(SALLE);
        assertThat(lu.affectations().getFirst().jours()).containsExactlyInAnyOrder(JourDeLaSemaine.DIMANCHE, JourDeLaSemaine.LUNDI);
    }

    @Test
    void should_read_the_absences_that_touch_the_period_and_the_replacements_inside_it() {
        UUID a = UUID.randomUUID();
        infirmier(CENTRE, a, "Amrani", null, true, false);
        absence(CENTRE, a, LocalDate.of(2026, 10, 20), DU);            // finit le premier jour : touche
        absence(CENTRE, a, AU, LocalDate.of(2026, 11, 30));            // commence le dernier jour : touche
        absence(CENTRE, a, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));   // avant
        absence(CENTRE, a, LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 9));    // après
        remplacement(CENTRE, a, DU);
        remplacement(CENTRE, a, AU);
        remplacement(CENTRE, a, DU.minusDays(1));
        remplacement(CENTRE, a, AU.plusDays(1));

        Personnel lu = personnel().lire(CENTRE, DU, AU);

        assertThat(lu.absences()).hasSize(2);
        assertThat(lu.remplacements()).extracting(r -> r.date()).containsExactlyInAnyOrder(DU, AU);
    }

    @Test
    void should_keep_the_data_of_each_center_apart() {
        UUID a = UUID.randomUUID();
        infirmier(AUTRE, a, "Etranger", null, true, false);
        affectation(AUTRE, a, Set.of(DayOfWeek.MONDAY));
        absence(AUTRE, a, DU, AU);
        remplacement(AUTRE, a, DU);

        Personnel lu = personnel().lire(CENTRE, DU, AU);

        assertThat(lu.infirmiers()).isEmpty();
        assertThat(lu.affectations()).isEmpty();
        assertThat(lu.absences()).isEmpty();
        assertThat(lu.remplacements()).isEmpty();
    }

    @Test
    void should_read_the_ratio_or_fall_back_on_the_default_and_flag_an_unusable_value() {
        assertThat(personnel().lire(CENTRE, DU, AU).ratio()).isEqualTo(new RatioDeLaPresence(4, false));
        ratio(CENTRE, 6);
        assertThat(personnel().lire(CENTRE, DU, AU).ratio()).isEqualTo(new RatioDeLaPresence(6, false));
        for (Integer illisible : new Integer[]{0, -3, 21, 100}) {
            ratio(CENTRE, illisible);
            assertThat(personnel().lire(CENTRE, DU, AU).ratio()).isEqualTo(new RatioDeLaPresence(4, true));
        }
        ratio(CENTRE, null);
        assertThat(personnel().lire(CENTRE, DU, AU).ratio()).isEqualTo(new RatioDeLaPresence(4, false));
    }

    @Test
    void should_list_the_active_centers_only() {
        centre(CENTRE, true);
        centre(AUTRE, false);
        assertThat(centresActifs().liste()).contains(CENTRE).doesNotContain(AUTRE);
    }
}
