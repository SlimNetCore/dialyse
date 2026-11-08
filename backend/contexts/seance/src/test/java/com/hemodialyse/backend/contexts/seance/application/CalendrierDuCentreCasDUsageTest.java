package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.contrat.CalendrierDuCentreEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE7a : consulter, ajouter et retirer un jour du calendrier du centre, calendrier en mémoire (ADR 0094). */
class CalendrierDuCentreCasDUsageTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 15);

    private Banc banc;
    private CalendrierDuCentreEnMemoire calendrier;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        banc.session.administrateur();
        calendrier = new CalendrierDuCentreEnMemoire();
    }

    private AjouterUnJourAuCalendrier ajouter() {
        return new AjouterUnJourAuCalendrier(banc.session, calendrier, banc.identifiants());
    }

    @Test
    void should_add_a_holiday_with_an_identifier_drawn_by_the_application_and_list_it_in_its_month() {
        var ajoute = ajouter().executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, D, "Férié");

        assertThat(ajoute.id()).isEqualTo(new UUID(9, 100));
        var mois = new ConsulterLeCalendrier(banc.session, calendrier).executer(CENTRE_A.valeur(), 2026, 1);
        assertThat(mois.joursFeries()).extracting(JourDuCalendrier::libelle).containsExactly("Férié");
        assertThat(mois.fermetures()).isEmpty();
        assertThat(mois.mois()).isEqualTo(new MoisDuCalendrier(2026, 1));
    }

    @Test
    void should_refuse_a_second_day_of_the_same_nature_on_the_same_date_but_not_another_nature() {
        ajouter().executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, D, "Premier");

        assertThatThrownBy(() -> ajouter().executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, D, "Second"))
                .isInstanceOf(JourDejaAuCalendrier.class);
        ajouter().executer(CENTRE_A.valeur(), NatureDeJour.FERMETURE_EXCEPTIONNELLE, D, "Fermeture");
        assertThat(new ConsulterLeCalendrier(banc.session, calendrier).executer(CENTRE_A.valeur(), 2026, 1).fermetures()).hasSize(1);
    }

    @Test
    void should_refuse_a_missing_date_and_a_too_long_label_and_store_nothing() {
        assertThatThrownBy(() -> ajouter().executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, null, "x")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> ajouter().executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, D, "m".repeat(256)))
                .isInstanceOf(SaisieInvalide.class);

        assertThat(new ConsulterLeCalendrier(banc.session, calendrier).executer(CENTRE_A.valeur(), 2026, 1).joursFeries()).isEmpty();
    }

    @Test
    void should_refuse_another_centre_than_the_session_first_for_every_operation_and_write_nothing() {
        assertThatThrownBy(() -> ajouter().executer(CENTRE_B.valeur(), NatureDeJour.JOUR_FERIE, D, "x")).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> new RetirerUnJourDuCalendrier(banc.session, calendrier).executer(CENTRE_B.valeur(), NatureDeJour.JOUR_FERIE,
                new UUID(9, 1))).isInstanceOf(AccesRefuse.class);
        // 403 avant 400 : un mois hors bornes sur un autre centre est d'abord un refus d'accès
        assertThatThrownBy(() -> new ConsulterLeCalendrier(banc.session, calendrier).executer(CENTRE_B.valeur(), 2026, 13))
                .isInstanceOf(AccesRefuse.class);
        assertThat(calendrier.duMois(CENTRE_B, NatureDeJour.JOUR_FERIE, new MoisDuCalendrier(2026, 1))).isEmpty();
    }

    @Test
    void should_refuse_a_month_out_of_bounds_for_the_own_centre() {
        var consulter = new ConsulterLeCalendrier(banc.session, calendrier);

        assertThatThrownBy(() -> consulter.executer(CENTRE_A.valeur(), 2026, 0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> consulter.executer(CENTRE_A.valeur(), 1_000_000_000, 1)).isInstanceOf(SaisieInvalide.class);
        assertThat(consulter.executer(CENTRE_A.valeur(), 0, 1).joursFeries()).isEmpty();
    }

    @Test
    void should_say_whether_a_day_was_removed_and_treat_an_unknown_day_as_a_normal_answer() {
        var ajoute = ajouter().executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, D, "Férié");
        var retirer = new RetirerUnJourDuCalendrier(banc.session, calendrier);

        assertThat(retirer.executer(CENTRE_A.valeur(), NatureDeJour.FERMETURE_EXCEPTIONNELLE, ajoute.id())).isFalse();
        assertThat(retirer.executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, ajoute.id())).isTrue();
        assertThat(retirer.executer(CENTRE_A.valeur(), NatureDeJour.JOUR_FERIE, ajoute.id())).isFalse();
    }

    @Test
    void should_use_the_centre_of_the_session_when_none_is_asked() {
        ajouter().executer(null, NatureDeJour.JOUR_FERIE, D, "Férié");

        assertThat(new ConsulterLeCalendrier(banc.session, calendrier).executer(null, 2026, 1).joursFeries()).hasSize(1);
    }
}
