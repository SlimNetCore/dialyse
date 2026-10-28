package com.hemodialyse.backend.contexts.absencespatients.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants du mois d'absences (A18, ADR 0042) : clôture sans précondition d'état, une seule fois par ouverture ; réouverture avec
 * motif obligatoire borné ; un mois sans ligne est ouvert ; le journal garde chaque acte.
 */
class MoisAbsencesTest {

    private static final CentreId CENTRE = new CentreId(new UUID(10, 1));
    private static final YearMonth SEPTEMBRE = YearMonth.of(2026, 9);
    private static final UtilisateurId ADMIN = new UtilisateurId(new UUID(13, 1));
    private static final UtilisateurId AUTRE_ADMIN = new UtilisateurId(new UUID(13, 2));
    private static final Instant LE_1 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Instant LE_2 = Instant.parse("2026-10-02T08:00:00Z");

    @Test
    void should_start_open_and_accept_writes_when_the_month_has_never_been_closed() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);

        assertThat(mois.estClos()).isFalse();
        mois.exigerOuvert();
    }

    @Test
    void should_accept_to_close_only_a_finished_month_without_grace_days() {
        LocalDate aujourdhui = LocalDate.of(2026, 10, 1);

        MoisAbsences.exigerTermine(SEPTEMBRE, aujourdhui);
        MoisAbsences.exigerTermine(YearMonth.of(2025, 12), aujourdhui);
        assertThatThrownBy(() -> MoisAbsences.exigerTermine(YearMonth.of(2026, 10), aujourdhui))
                .isInstanceOfSatisfying(MoisDAbsencesNonTermine.class,
                        e -> assertThat(e.code()).isEqualTo("MOIS_D_ABSENCES_NON_TERMINE"));
        assertThatThrownBy(() -> MoisAbsences.exigerTermine(YearMonth.of(2026, 11), aujourdhui))
                .isInstanceOf(MoisDAbsencesNonTermine.class);
    }

    @Test
    void should_refuse_writes_into_a_closed_month() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);

        assertThatThrownBy(mois::exigerOuvert).isInstanceOf(MoisDAbsencesClos.class);
    }

    @Test
    void should_close_with_author_instant_and_the_warning_in_the_journal_entry() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);

        EntreeDuJournalDuMois entree = mois.cloturer(LE_1, ADMIN, 5, 2);

        assertThat(mois.estClos()).isTrue();
        assertThat(mois.closPar()).isEqualTo(ADMIN);
        assertThat(mois.closLe()).isEqualTo(LE_1);
        assertThat(entree).isEqualTo(new EntreeDuJournalDuMois(EntreeDuJournalDuMois.Type.CLOTURE, CENTRE, SEPTEMBRE, LE_1, ADMIN,
                null, 5L, 2L));
    }

    @Test
    void should_close_without_any_precondition_even_with_absences_left_to_qualify() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);

        mois.cloturer(LE_1, ADMIN, 120, 120);

        assertThat(mois.estClos()).isTrue();
    }

    @Test
    void should_refuse_a_second_closure_of_the_same_month() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);

        assertThatThrownBy(() -> mois.cloturer(LE_2, AUTRE_ADMIN, 0, 0)).isInstanceOf(MoisDAbsencesDejaClos.class);
        assertThat(mois.closPar()).isEqualTo(ADMIN);
    }

    @Test
    void should_refuse_every_write_once_the_month_is_closed() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);

        assertThatThrownBy(mois::exigerOuvert).isInstanceOfSatisfying(MoisDAbsencesClos.class, e -> {
            assertThat(e.code()).isEqualTo("MOIS_D_ABSENCES_CLOS");
            assertThat(e.mois()).isEqualTo(SEPTEMBRE);
        });
    }

    @Test
    void should_reopen_with_a_trimmed_reason_and_keep_the_last_closure() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);

        EntreeDuJournalDuMois entree = mois.rouvrir(LE_2, AUTRE_ADMIN, "  absence oubliee  ");

        assertThat(mois.estClos()).isFalse();
        assertThat(mois.motifDeReouverture()).isEqualTo("absence oubliee");
        assertThat(mois.rouvertPar()).isEqualTo(AUTRE_ADMIN);
        assertThat(mois.rouvertLe()).isEqualTo(LE_2);
        assertThat(mois.closPar()).isEqualTo(ADMIN);
        assertThat(entree.motif()).isEqualTo("absence oubliee");
        assertThat(entree.aQualifierRestantes()).isNull();
    }

    @Test
    void should_require_a_reason_to_reopen_blank_or_absent() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);

        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, null)).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, "   ")).isInstanceOf(MotifDeReouvertureObligatoire.class);
        assertThat(mois.estClos()).isTrue();
    }

    @Test
    void should_accept_a_reason_of_500_characters_and_refuse_501() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);

        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, "x".repeat(501))).isInstanceOf(MotifDeReouvertureObligatoire.class);
        mois.rouvrir(LE_2, ADMIN, "x".repeat(500));
        assertThat(mois.estClos()).isFalse();
    }

    @Test
    void should_refuse_to_reopen_a_month_that_is_not_closed_before_checking_the_reason() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);

        assertThatThrownBy(() -> mois.rouvrir(LE_2, ADMIN, null)).isInstanceOf(MoisDAbsencesNonClos.class);
    }

    @Test
    void should_close_again_after_a_reopening() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);
        mois.cloturer(LE_1, ADMIN, 0, 0);
        mois.rouvrir(LE_2, ADMIN, "motif");

        mois.cloturer(LE_2.plusSeconds(60), AUTRE_ADMIN, 1, 0);

        assertThat(mois.estClos()).isTrue();
        assertThat(mois.closPar()).isEqualTo(AUTRE_ADMIN);
    }

    @Test
    void should_require_an_author_and_an_instant_for_every_act() {
        MoisAbsences mois = MoisAbsences.ouvert(CENTRE, SEPTEMBRE);

        assertThatThrownBy(() -> mois.cloturer(null, ADMIN, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mois.cloturer(LE_1, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mois.estClos()).isFalse();
        mois.cloturer(LE_1, ADMIN, 0, 0);
        assertThatThrownBy(() -> mois.rouvrir(null, ADMIN, "motif")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mois.rouvrir(LE_2, null, "motif")).isInstanceOf(IllegalArgumentException.class);
        assertThat(mois.estClos()).isTrue();
    }
}
