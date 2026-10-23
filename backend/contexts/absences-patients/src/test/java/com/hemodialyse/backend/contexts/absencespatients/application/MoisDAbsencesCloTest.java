package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.application.CloturerLeMoisDAbsences.ResultatDeCloture;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.EntreeDuJournalDuMois;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesClos;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesDejaClos;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesNonClos;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisDAbsencesNonTermine;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifDeReouvertureObligatoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesCloture;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.MoisDAbsencesRouvert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AMRANI;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.AVEC_DROIT;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.BELKACEM;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.CENTRE;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.MEDECIN;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.SANS_DROIT;
import static com.hemodialyse.backend.contexts.absencespatients.application.MondeDesEcritures.SECRETAIRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Clôture mensuelle des absences (A18, A6, ADR 0042), cas d'usage sur les doubles en mémoire. « Aujourd'hui » du monde : 22 octobre
 * 2026 ; le mois clos des scénarios est septembre 2026, l'autre mois est octobre.
 */
class MoisDAbsencesCloTest {

    private static final YearMonth SEPTEMBRE = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBRE = YearMonth.of(2026, 10);
    private static final LocalDate FIN_SEPTEMBRE = LocalDate.of(2026, 9, 28);
    private static final LocalDate DEBUT_OCTOBRE = LocalDate.of(2026, 10, 2);

    private final MondeDesEcritures monde = new MondeDesEcritures();

    private void clore(YearMonth mois) {
        monde.cloturer.executer(CENTRE, mois, MondeDesEcritures.MEDECIN);
    }

    private void assertMoisClos(Runnable ecriture) {
        assertThatThrownBy(ecriture::run).isInstanceOfSatisfying(MoisDAbsencesClos.class, e -> {
            assertThat(e.code()).isEqualTo("MOIS_D_ABSENCES_CLOS");
            assertThat(e.mois()).isEqualTo(SEPTEMBRE);
        });
    }

    // --- clôturer

    @Test
    void should_close_a_past_month_and_warn_with_the_absences_left_to_qualify_and_those_late() {
        monde.declaree(AMRANI, LocalDate.of(2026, 9, 10), null);
        monde.declaree(BELKACEM, LocalDate.of(2026, 9, 11), null);
        monde.declaree(AMRANI, LocalDate.of(2026, 9, 12), MotifAbsence.MALADIE);
        monde.declaree(AMRANI, LocalDate.of(2026, 10, 5), null);

        ResultatDeCloture resultat = monde.cloturer.executer(CENTRE, SEPTEMBRE, MEDECIN);

        assertThat(resultat).isEqualTo(new ResultatDeCloture(SEPTEMBRE, MondeDesEcritures.MAINTENANT, 2, 2));
        assertThat(monde.mois.duMois(CENTRE, SEPTEMBRE).estClos()).isTrue();
        assertThat(monde.mois.duMois(CENTRE, OCTOBRE).estClos()).isFalse();
    }

    @Test
    void should_count_as_late_only_the_absences_older_than_the_qualification_delay_on_the_day_of_the_closure() {
        // le 1er octobre, le délai (3 jours) rend en retard les absences antérieures au 28 septembre
        monde.instant = Instant.parse("2026-10-01T08:00:00Z");
        monde.declaree(AMRANI, LocalDate.of(2026, 9, 20), null);
        monde.declaree(BELKACEM, LocalDate.of(2026, 9, 29), null);

        ResultatDeCloture resultat = monde.cloturer.executer(CENTRE, SEPTEMBRE, MEDECIN);

        assertThat(resultat.aQualifierRestantes()).isEqualTo(2);
        assertThat(resultat.enRetard()).isEqualTo(1);
    }

    @Test
    void should_close_without_any_precondition_even_with_nothing_declared() {
        ResultatDeCloture resultat = monde.cloturer.executer(CENTRE, SEPTEMBRE, MEDECIN);

        assertThat(resultat.aQualifierRestantes()).isZero();
        assertThat(resultat.enRetard()).isZero();
        assertThat(monde.mois.duMois(CENTRE, SEPTEMBRE).estClos()).isTrue();
    }

    @Test
    void should_refuse_to_close_the_current_month_and_any_future_month_without_grace_days() {
        assertThatThrownBy(() -> monde.cloturer.executer(CENTRE, OCTOBRE, MEDECIN)).isInstanceOfSatisfying(
                MoisDAbsencesNonTermine.class, e -> {
                    assertThat(e.code()).isEqualTo("MOIS_D_ABSENCES_NON_TERMINE");
                    assertThat(e.mois()).isEqualTo(OCTOBRE);
                });
        assertThatThrownBy(() -> monde.cloturer.executer(CENTRE, YearMonth.of(2026, 11), MEDECIN))
                .isInstanceOf(MoisDAbsencesNonTermine.class);
        assertThatThrownBy(() -> monde.cloturer.executer(CENTRE, YearMonth.of(2027, 1), MEDECIN))
                .isInstanceOf(MoisDAbsencesNonTermine.class);
        assertThat(monde.mois.journal(CENTRE, OCTOBRE)).isEmpty();
        assertThat(monde.evenementsDeMois).isEmpty();
    }

    @Test
    void should_close_the_previous_month_from_its_very_first_day_of_the_next_month() {
        monde.instant = Instant.parse("2026-10-01T00:00:00Z");

        monde.cloturer.executer(CENTRE, SEPTEMBRE, MEDECIN);

        assertThat(monde.mois.duMois(CENTRE, SEPTEMBRE).estClos()).isTrue();
    }

    @Test
    void should_refuse_a_second_closure_without_journal_or_event() {
        clore(SEPTEMBRE);

        assertThatThrownBy(() -> clore(SEPTEMBRE)).isInstanceOf(MoisDAbsencesDejaClos.class);

        assertThat(monde.mois.journal(CENTRE, SEPTEMBRE)).hasSize(1);
        assertThat(monde.evenementsDeMois).hasSize(1);
    }

    @Test
    void should_journal_the_closure_and_publish_the_event_with_the_warning() {
        monde.declaree(AMRANI, LocalDate.of(2026, 9, 10), null);

        monde.cloturer.executer(CENTRE, SEPTEMBRE, MEDECIN);

        assertThat(monde.mois.journal(CENTRE, SEPTEMBRE)).containsExactly(new EntreeDuJournalDuMois(
                EntreeDuJournalDuMois.Type.CLOTURE, CENTRE, SEPTEMBRE, MondeDesEcritures.MAINTENANT, MEDECIN, null, 1L, 1L));
        assertThat(monde.evenementsDeMois).containsExactly(new MoisDAbsencesCloture(CENTRE.valeur(), SEPTEMBRE,
                MEDECIN.valeur(), MondeDesEcritures.MAINTENANT, 1, 1));
    }

    @Test
    void should_list_twelve_months_of_a_year_open_by_default_with_the_closed_one_in_place() {
        clore(SEPTEMBRE);
        clore(YearMonth.of(2026, 3));
        var consulter = new ConsulterLesMoisDAbsences(monde.mois);

        var mois = consulter.executer(CENTRE, 2026);

        assertThat(mois).hasSize(12);
        assertThat(mois.get(0).mois()).isEqualTo(YearMonth.of(2026, 1));
        assertThat(mois.get(0).estClos()).isFalse();
        assertThat(mois.get(2).estClos()).isTrue();
        assertThat(mois.get(8).mois()).isEqualTo(SEPTEMBRE);
        assertThat(mois.get(8).estClos()).isTrue();
        assertThat(mois.get(11).mois()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(consulter.executer(AUTRE_CENTRE, 2026)).allMatch(m -> !m.estClos());
        assertThat(consulter.executer(CENTRE, 2025)).allMatch(m -> !m.estClos());
    }

    @Test
    void should_keep_other_centres_and_months_open() {
        clore(SEPTEMBRE);

        assertThat(monde.mois.estOuvert(AUTRE_CENTRE, SEPTEMBRE)).isTrue();
        assertThat(monde.mois.estOuvert(CENTRE, YearMonth.of(2026, 8))).isTrue();
        assertThat(monde.mois.moisClos(CENTRE)).containsExactly(SEPTEMBRE);
    }

    // --- rouvrir

    @Test
    void should_reopen_with_a_reason_journal_it_and_publish_the_event() {
        clore(SEPTEMBRE);
        monde.plusTard();

        monde.rouvrir.executer(CENTRE, SEPTEMBRE, SECRETAIRE, "  absence oubliee  ");

        assertThat(monde.mois.duMois(CENTRE, SEPTEMBRE).estClos()).isFalse();
        assertThat(monde.mois.journal(CENTRE, SEPTEMBRE)).extracting(EntreeDuJournalDuMois::type)
                .containsExactly(EntreeDuJournalDuMois.Type.CLOTURE, EntreeDuJournalDuMois.Type.REOUVERTURE);
        assertThat(monde.evenementsDeMois.getLast()).isEqualTo(new MoisDAbsencesRouvert(CENTRE.valeur(), SEPTEMBRE,
                SECRETAIRE.valeur(), monde.instant, "absence oubliee"));
    }

    @Test
    void should_refuse_a_reopening_without_reason_and_leave_the_month_closed() {
        clore(SEPTEMBRE);

        assertThatThrownBy(() -> monde.rouvrir.executer(CENTRE, SEPTEMBRE, MEDECIN, " ")).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThatThrownBy(() -> monde.rouvrir.executer(CENTRE, SEPTEMBRE, MEDECIN, "x".repeat(501)))
                .isInstanceOf(MotifDeReouvertureObligatoire.class);

        assertThat(monde.mois.duMois(CENTRE, SEPTEMBRE).estClos()).isTrue();
        assertThat(monde.evenementsDeMois).hasSize(1);
    }

    @Test
    void should_refuse_to_reopen_a_month_that_is_not_closed() {
        assertThatThrownBy(() -> monde.rouvrir.executer(CENTRE, SEPTEMBRE, MEDECIN, "motif")).isInstanceOf(MoisDAbsencesNonClos.class);
    }

    @Test
    void should_write_again_in_a_reopened_month_and_close_it_a_second_time() {
        clore(SEPTEMBRE);
        monde.rouvrir.executer(CENTRE, SEPTEMBRE, MEDECIN, "motif");

        monde.declaree(AMRANI, FIN_SEPTEMBRE, null);
        monde.cloturer.executer(CENTRE, SEPTEMBRE, MEDECIN);

        assertThat(monde.mois.journal(CENTRE, SEPTEMBRE)).hasSize(3);
        assertThat(monde.mois.duMois(CENTRE, SEPTEMBRE).estClos()).isTrue();
    }

    // --- un mois clos refuse les écritures

    @Test
    void should_refuse_to_declare_an_absence_dated_in_a_closed_month() {
        clore(SEPTEMBRE);

        assertMoisClos(() -> monde.declaree(AMRANI, FIN_SEPTEMBRE, MotifAbsence.MALADIE));

        assertThat(monde.absences.toutes()).isEmpty();
        assertThat(monde.signalements).isEmpty();
    }

    @Test
    void should_refuse_to_qualify_and_to_cancel_an_absence_of_a_closed_month() {
        AbsencePatient aQualifier = monde.declaree(AMRANI, FIN_SEPTEMBRE, null);
        AbsencePatient aAnnuler = monde.declaree(BELKACEM, FIN_SEPTEMBRE.minusDays(1), null);
        clore(SEPTEMBRE);
        int signalements = monde.signalements.size();

        assertMoisClos(() -> monde.qualifier.executer(CENTRE, aQualifier.id(), MotifAbsence.MALADIE, null, SANS_DROIT));
        assertMoisClos(() -> monde.annuler.executer(CENTRE, aAnnuler.id(), "erreur", AVEC_DROIT));

        assertThat(monde.absences.lire(aQualifier.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(monde.absences.lire(aAnnuler.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(monde.signalements).hasSize(signalements);
    }

    @Test
    void should_refuse_to_make_up_an_absence_whose_month_is_closed_even_if_the_make_up_date_is_in_an_open_month() {
        AbsencePatient absence = monde.declaree(AMRANI, FIN_SEPTEMBRE, MotifAbsence.MALADIE);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, DEBUT_OCTOBRE, "VALIDEE");
        clore(SEPTEMBRE);

        assertMoisClos(() -> monde.rattraper.executer(CENTRE, absence.id(), DEBUT_OCTOBRE, AVEC_DROIT));

        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
    }

    @Test
    void should_refuse_to_make_up_an_open_absence_by_a_session_dated_in_a_closed_month() {
        LocalDate absenceDeAout = LocalDate.of(2026, 8, 28);
        LocalDate seanceDeSeptembre = LocalDate.of(2026, 9, 2);
        AbsencePatient absence = monde.declaree(AMRANI, absenceDeAout, MotifAbsence.MALADIE);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, seanceDeSeptembre, "VALIDEE");
        clore(SEPTEMBRE);

        assertMoisClos(() -> monde.rattraper.executer(CENTRE, absence.id(), seanceDeSeptembre, AVEC_DROIT));

        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        // l'ordre de verrouillage est croissant : mois de l'absence puis mois de la date de rattrapage
        assertThat(monde.mois.moisVerrouilles()).containsSubsequence(YearMonth.of(2026, 8), SEPTEMBRE);
    }

    @Test
    void should_make_up_an_absence_when_both_months_are_open_and_lock_them_in_ascending_order() {
        AbsencePatient absence = monde.declaree(AMRANI, FIN_SEPTEMBRE, MotifAbsence.MALADIE);
        monde.suivi.poserLaSeance(CENTRE, AMRANI, DEBUT_OCTOBRE, "VALIDEE");

        monde.rattraper.executer(CENTRE, absence.id(), DEBUT_OCTOBRE, AVEC_DROIT);

        assertThat(monde.absences.lire(absence.id()).orElseThrow().statut()).isEqualTo(StatutAbsence.RATTRAPEE);
        List<YearMonth> verrous = monde.mois.moisVerrouilles();
        assertThat(verrous.subList(verrous.size() - 2, verrous.size())).containsExactly(SEPTEMBRE, OCTOBRE);
    }

    @Test
    void should_refuse_the_reinclusion_of_a_session_whose_absence_month_is_closed_and_keep_the_absence() {
        AbsencePatient absence = monde.declaree(AMRANI, FIN_SEPTEMBRE, MotifAbsence.MALADIE);
        clore(SEPTEMBRE);

        assertMoisClos(() -> monde.retirer.executer(CENTRE, absence.id()));

        assertThat(monde.absences.lire(absence.id())).isPresent();
    }

    @Test
    void should_remove_the_absence_of_an_exclusion_again_once_the_month_is_reopened() {
        AbsencePatient absence = monde.declaree(AMRANI, FIN_SEPTEMBRE, MotifAbsence.MALADIE);
        clore(SEPTEMBRE);
        monde.rouvrir.executer(CENTRE, SEPTEMBRE, MEDECIN, "reinclusion");

        monde.retirer.executer(CENTRE, absence.id());

        assertThat(monde.absences.lire(absence.id())).isEmpty();
    }

    @Test
    void should_still_write_in_the_neighbouring_month_and_in_another_centre() {
        clore(SEPTEMBRE);

        monde.declaree(AMRANI, DEBUT_OCTOBRE, null);

        assertThat(monde.absences.toutes()).hasSize(1);
    }

    @Test
    void should_not_hold_a_closed_month_against_the_reinclusion_of_an_unknown_absence() {
        clore(SEPTEMBRE);

        monde.retirer.executer(CENTRE, new com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId(
                new java.util.UUID(1, 1)));
    }
}
