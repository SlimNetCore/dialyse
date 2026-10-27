package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.CasePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.SemainePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.StatutDeCase;
import com.hemodialyse.backend.contexts.planification.domain.Presence.TypeDeConflit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.DIMANCHE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.ISO;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.MARDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.SALLE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.SALLE_B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePresence.SOIR;
import static org.assertj.core.api.Assertions.assertThat;

class PresenceDeLaSemaineTest {

    private static CasePresence lacase(SemainePresence s, Presence.Salle salle, Presence.Creneau creneau, JourDeLaSemaine jour) {
        return s.cases().stream().filter(c -> c.salleId().equals(salle.id()) && c.creneauId().equals(creneau.id()) && c.jour() == jour)
                .findFirst().orElseThrow();
    }

    @Test
    void should_round_the_required_staff_up_and_require_nobody_without_patient() {
        assertThat(PresenceDeLaSemaine.requis(0, 4)).isZero();
        assertThat(PresenceDeLaSemaine.requis(1, 4)).isEqualTo(1);
        assertThat(PresenceDeLaSemaine.requis(4, 4)).isEqualTo(1);
        assertThat(PresenceDeLaSemaine.requis(5, 4)).isEqualTo(2);
        assertThat(PresenceDeLaSemaine.requis(9, 4)).isEqualTo(3);
    }

    @Test
    void should_start_the_week_on_the_sunday() {
        assertThat(PresenceDeLaSemaine.debutDeSemaine(DIMANCHE)).isEqualTo(DIMANCHE);
        assertThat(PresenceDeLaSemaine.debutDeSemaine(DIMANCHE.plusDays(6))).isEqualTo(DIMANCHE);
        assertThat(PresenceDeLaSemaine.debutDeSemaine(DIMANCHE.plusDays(7))).isEqualTo(DIMANCHE.plusDays(7));
    }

    @Test
    void should_report_understaffing_until_enough_nurses_are_planned() {
        JeuDePresence t = new JeuDePresence().patients(5, SALLE, MATIN, JourDeLaSemaine.LUNDI);
        CasePresence seul = lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.LUNDI);
        assertThat(seul.statut()).isEqualTo(StatutDeCase.SOUS_EFFECTIF);
        assertThat(seul.requis()).isEqualTo(2);
        assertThat(seul.manque()).isEqualTo(2);
        t.affecter(t.infirmier("Amrani", false), SALLE, MATIN, JourDeLaSemaine.LUNDI);
        t.affecter(t.infirmier("Benali", false), SALLE, MATIN, JourDeLaSemaine.LUNDI);
        CasePresence deux = lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.LUNDI);
        assertThat(deux.statut()).isEqualTo(StatutDeCase.COUVERT);
        assertThat(deux.manque()).isZero();
    }

    @Test
    void should_count_a_nurse_in_an_empty_room_as_entirely_surplus() {
        JeuDePresence t = new JeuDePresence();
        t.affecter(t.infirmier("Amrani", false), SALLE, MATIN, JourDeLaSemaine.MARDI);
        CasePresence c = lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.MARDI);
        assertThat(c.statut()).isEqualTo(StatutDeCase.SANS_PATIENT);
        assertThat(c.surplus()).isEqualTo(1);
    }

    @Test
    void should_count_only_qualified_nurses_in_an_isolation_room_and_flag_the_others() {
        JeuDePresence t = new JeuDePresence().patients(2, ISO, MATIN, JourDeLaSemaine.LUNDI);
        t.affecter(t.infirmier("Habilitee", true), ISO, MATIN, JourDeLaSemaine.LUNDI);
        t.affecter(t.infirmier("Standard", false), ISO, MATIN, JourDeLaSemaine.LUNDI);
        SemainePresence s = PresenceDeLaSemaine.construire(t.build(), DIMANCHE);
        assertThat(lacase(s, ISO, MATIN, JourDeLaSemaine.LUNDI).statut()).isEqualTo(StatutDeCase.COUVERT);
        assertThat(lacase(s, ISO, MATIN, JourDeLaSemaine.LUNDI).surplus()).isZero();
        assertThat(s.conflits()).extracting(Presence.ConflitPresence::type).containsExactly(TypeDeConflit.NON_HABILITE);
    }

    @Test
    void should_remove_an_absent_nurse_and_let_a_replacement_cover_the_case() {
        JeuDePresence t = new JeuDePresence().patients(3, SALLE, MATIN, JourDeLaSemaine.LUNDI);
        InfirmierDeLaPresence amrani = t.infirmier("Amrani", false);
        InfirmierDeLaPresence cherif = t.infirmier("Cherif", false);
        t.affecter(amrani, SALLE, MATIN, JourDeLaSemaine.LUNDI).absence(amrani, LUNDI, LUNDI);
        CasePresence absente = lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.LUNDI);
        assertThat(absente.statut()).isEqualTo(StatutDeCase.SOUS_EFFECTIF);
        assertThat(absente.absents()).extracting(Presence.Absent::nom).containsExactly("Amrani");
        t.remplacement(cherif, LUNDI, SALLE, MATIN);
        CasePresence couverte = lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.LUNDI);
        assertThat(couverte.statut()).isEqualTo(StatutDeCase.COUVERT);
        assertThat(couverte.presents().getFirst().remplacant()).isTrue();
    }

    @Test
    void should_not_count_a_replacement_who_is_absent_that_day() {
        JeuDePresence t = new JeuDePresence().patients(1, SALLE, MATIN, JourDeLaSemaine.LUNDI);
        InfirmierDeLaPresence cherif = t.infirmier("Cherif", false);
        t.remplacement(cherif, LUNDI, SALLE, MATIN).absence(cherif, LUNDI, LUNDI);
        assertThat(lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.LUNDI).statut())
                .isEqualTo(StatutDeCase.SOUS_EFFECTIF);
    }

    @Test
    void should_need_no_staff_on_closed_days_weekly_or_dated() {
        JeuDePresence t = new JeuDePresence().ouverts(JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI).fermeture(MARDI)
                .patients(5, SALLE, MATIN, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI, JourDeLaSemaine.MERCREDI);
        SemainePresence s = PresenceDeLaSemaine.construire(t.build(), DIMANCHE);
        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.LUNDI).statut()).isEqualTo(StatutDeCase.SOUS_EFFECTIF);
        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.MARDI).statut()).isEqualTo(StatutDeCase.FERME);
        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.MERCREDI).statut()).isEqualTo(StatutDeCase.FERME);
        assertThat(s.jours().get(JourDeLaSemaine.MARDI.ordinal()).fermetureMotif()).isEqualTo("Férié");
        assertThat(s.jours().get(JourDeLaSemaine.MERCREDI.ordinal()).ouvertHebdomadaire()).isFalse();
    }

    @Test
    void should_ignore_a_patient_after_the_last_day_of_his_place() {
        JeuDePresence t = new JeuDePresence().patientSortiLe(SALLE, MATIN, LUNDI, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        SemainePresence s = PresenceDeLaSemaine.construire(t.build(), DIMANCHE);
        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.LUNDI).patients()).isEqualTo(1);
        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.MARDI).patients()).isZero();
    }

    @Test
    void should_let_the_ratio_drive_the_requirement() {
        JeuDePresence t = new JeuDePresence().ratio(2).patients(5, SALLE, MATIN, JourDeLaSemaine.LUNDI);
        assertThat(lacase(PresenceDeLaSemaine.construire(t.build(), DIMANCHE), SALLE, MATIN, JourDeLaSemaine.LUNDI).requis())
                .isEqualTo(3);
    }

    @Test
    void should_list_understaffed_cases_in_the_window_in_chronological_order() {
        JeuDePresence t = new JeuDePresence().patients(2, SALLE, SOIR, JourDeLaSemaine.LUNDI)
                .patients(2, SALLE, MATIN, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        InfirmierDeLaPresence a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourDeLaSemaine.MARDI).affecter(a, SALLE, SOIR, JourDeLaSemaine.LUNDI).absence(a, MARDI, MARDI);

        List<Presence.AlertePresence> alertes = PresenceDeLaSemaine.alertes(t.build(), LUNDI, MARDI);

        assertThat(alertes).hasSize(2);
        assertThat(alertes.get(0).date()).isEqualTo(LUNDI);
        assertThat(alertes.get(0).creneauId()).isEqualTo(MATIN.id());
        assertThat(alertes.get(1).absents()).containsExactly("Amrani");
        assertThat(alertes).noneMatch(x -> x.creneauId().equals(SOIR.id()));
        assertThat(PresenceDeLaSemaine.alertes(t.build(), LUNDI.plusDays(2), LUNDI.plusDays(3))).isEmpty();
    }

    @Test
    void should_list_surplus_cases_in_the_window_and_ignore_closed_days() {
        JeuDePresence t = new JeuDePresence().fermeture(MARDI).patients(4, SALLE, MATIN, JourDeLaSemaine.MARDI);
        t.affecter(t.infirmier("Amrani", false), SALLE, MATIN, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        t.affecter(t.infirmier("Benali", false), SALLE, MATIN, JourDeLaSemaine.MARDI);

        var toute = PresenceDeLaSemaine.sureffectifs(t.build(), DIMANCHE, DIMANCHE.plusDays(6));
        var apresLundi = PresenceDeLaSemaine.sureffectifs(t.build(), MARDI, DIMANCHE.plusDays(6));

        assertThat(toute).extracting(Presence.AlerteSureffectif::jour).containsExactly(JourDeLaSemaine.LUNDI);
        assertThat(toute.getFirst().patients()).isZero();
        assertThat(apresLundi).isEmpty();
    }

    @Test
    void should_flag_a_nurse_planned_in_two_rooms_on_the_same_slot_as_a_double_assignment_alert() {
        JeuDePresence t = new JeuDePresence();
        InfirmierDeLaPresence a = t.infirmier("Amrani", false);
        t.affecter(a, SALLE, MATIN, JourDeLaSemaine.LUNDI).affecter(a, SALLE_B, MATIN, JourDeLaSemaine.LUNDI);
        t.affecter(a, SALLE, SOIR, JourDeLaSemaine.MARDI);

        SemainePresence s = PresenceDeLaSemaine.construire(t.build(), DIMANCHE);
        var doubles = PresenceDeLaSemaine.doublesAffectations(t.build(), DIMANCHE, DIMANCHE.plusDays(6));

        assertThat(s.conflits()).extracting(Presence.ConflitPresence::type).containsExactly(TypeDeConflit.DOUBLE_AFFECTATION);
        assertThat(doubles).hasSize(1);
        assertThat(doubles.getFirst().infirmier()).isEqualTo("Amrani");
        assertThat(doubles.getFirst().salles()).containsExactly(SALLE.id(), SALLE_B.id());
        assertThat(doubles.getFirst().date()).isEqualTo(LUNDI);
    }

    @Test
    void should_use_the_display_name_first_name_then_name() {
        InfirmierDeLaPresence i = new InfirmierDeLaPresence(UUID.randomUUID(), "Benali", "Karim", Qualification.MAJOR, false);
        assertThat(i.nomAffiche()).isEqualTo("Karim Benali");
        assertThat(new InfirmierDeLaPresence(UUID.randomUUID(), "Benali", null, Qualification.MAJOR, false).nomAffiche())
                .isEqualTo("Benali");
    }

    @Test
    void should_read_a_ratio_from_the_database_or_fall_back_to_the_default() {
        assertThat(RatioDeLaPresence.depuisLaBase(null)).isEqualTo(new RatioDeLaPresence(4, false));
        assertThat(RatioDeLaPresence.depuisLaBase(1)).isEqualTo(new RatioDeLaPresence(1, false));
        assertThat(RatioDeLaPresence.depuisLaBase(20)).isEqualTo(new RatioDeLaPresence(20, false));
        for (int illisible : new int[]{0, -3, 21, 100, 1000}) {
            assertThat(RatioDeLaPresence.depuisLaBase(illisible)).isEqualTo(new RatioDeLaPresence(4, true));
        }
    }

    @Test
    void should_not_count_a_patient_before_his_first_day_and_order_same_day_alerts_by_slot() {
        JeuDePresence t = new JeuDePresence().patients(1, SALLE, SOIR, JourDeLaSemaine.LUNDI)
                .patients(1, SALLE, MATIN, JourDeLaSemaine.LUNDI);
        t.occupationAPartirDu(SALLE, MATIN, MARDI, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);

        SemainePresence s = PresenceDeLaSemaine.construire(t.build(), DIMANCHE);

        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.LUNDI).patients()).isEqualTo(1);
        assertThat(lacase(s, SALLE, MATIN, JourDeLaSemaine.MARDI).patients()).isEqualTo(1);
        assertThat(PresenceDeLaSemaine.alertes(t.build(), LUNDI, LUNDI)).extracting(Presence.AlertePresence::creneauId)
                .containsExactly(MATIN.id(), SOIR.id());
        assertThat(PresenceDeLaSemaine.sureffectifs(t.build(), LUNDI, LUNDI)).isEmpty();
    }

    @Test
    void should_describe_a_date_beyond_a_week_boundary_in_the_next_week() {
        LocalDate dimancheSuivant = DIMANCHE.plusDays(7);
        JeuDePresence t = new JeuDePresence().patients(5, SALLE, MATIN, JourDeLaSemaine.DIMANCHE);
        assertThat(PresenceDeLaSemaine.alertes(t.build(), dimancheSuivant, dimancheSuivant)).hasSize(1);
    }
}
