package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants P15 (RG-INF-030 à 032) : période, type, motif, retrait par l'infirmier.
 */
class AbsenceInfirmierTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final InfirmierId INFIRMIER = new InfirmierId(UUID.randomUUID());
    private static final CompteId SON_COMPTE = new CompteId(UUID.randomUUID());
    private static final CompteId UN_ADMIN = new CompteId(UUID.randomUUID());
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 2);

    private static PeriodeDAbsence periode(int decalageDebut, int decalageFin) {
        return new PeriodeDAbsence(AUJOURDHUI.plusDays(decalageDebut), AUJOURDHUI.plusDays(decalageFin));
    }

    private static AbsenceInfirmier parAdmin(PeriodeDAbsence p, String motif) {
        return AbsenceInfirmier.declarer(new AbsenceId(UUID.randomUUID()), CENTRE, INFIRMIER, p, TypeAbsence.CONGE, motif, UN_ADMIN);
    }

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    @Test
    void should_accept_a_one_day_and_a_366_day_period_but_not_367_days_or_an_inverted_period() {
        assertThat(new PeriodeDAbsence(AUJOURDHUI, AUJOURDHUI).couvre(AUJOURDHUI)).isTrue();
        assertThat(periode(0, 365)).isNotNull();
        assertThatThrownBy(() -> periode(0, 366)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> periode(1, 0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new PeriodeDAbsence(null, AUJOURDHUI)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_share_a_day_only_when_periods_overlap_and_accept_contiguous_periods() {
        assertThat(periode(0, 4).chevauche(periode(4, 8))).isTrue();
        assertThat(periode(0, 8).chevauche(periode(2, 3))).isTrue();
        assertThat(periode(0, 4).chevauche(periode(0, 4))).isTrue();
        assertThat(periode(0, 4).chevauche(periode(5, 8))).isFalse();
    }

    @Test
    void should_trim_the_reason_and_treat_a_blank_one_as_absent() {
        assertThat(parAdmin(periode(0, 1), "  Grippe  ").motif()).contains("Grippe");
        assertThat(parAdmin(periode(0, 1), "   ").motif()).isEmpty();
        assertThat(parAdmin(periode(0, 1), null).motif()).isEmpty();
    }

    @Test
    void should_refuse_a_reason_longer_than_255_characters() {
        assertThat(parAdmin(periode(0, 1), "x".repeat(255)).motif()).isPresent();
        assertThatThrownBy(() -> parAdmin(periode(0, 1), "x".repeat(256))).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> AbsenceInfirmier.exigerUnMotifValide("x".repeat(256))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_refuse_an_unknown_or_lowercase_type() {
        assertThat(TypeAbsence.depuisLaSaisie("MALADIE")).isEqualTo(TypeAbsence.MALADIE);
        assertThatThrownBy(() -> TypeAbsence.depuisLaSaisie("conge")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> TypeAbsence.depuisLaSaisie(" ")).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> TypeAbsence.depuisLaSaisie(null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_let_a_nurse_declare_an_absence_ending_today_but_not_one_ended_yesterday() {
        AbsenceInfirmier.declarerParLInfirmier(new AbsenceId(UUID.randomUUID()), CENTRE, INFIRMIER, periode(-3, 0), TypeAbsence.AUTRE,
                null, SON_COMPTE, AUJOURDHUI);

        refuse(() -> AbsenceInfirmier.declarerParLInfirmier(new AbsenceId(UUID.randomUUID()), CENTRE, INFIRMIER, periode(-3, -1),
                TypeAbsence.AUTRE, null, SON_COMPTE, AUJOURDHUI), "ABSENCE_PASSEE");
    }

    @Test
    void should_let_a_nurse_withdraw_her_own_absence_only_when_it_has_not_started() {
        AbsenceInfirmier avenir = AbsenceInfirmier.declarerParLInfirmier(new AbsenceId(UUID.randomUUID()), CENTRE, INFIRMIER,
                periode(1, 3), TypeAbsence.CONGE, null, SON_COMPTE, AUJOURDHUI);
        AbsenceInfirmier aujourdhui = AbsenceInfirmier.declarerParLInfirmier(new AbsenceId(UUID.randomUUID()), CENTRE, INFIRMIER,
                periode(0, 3), TypeAbsence.CONGE, null, SON_COMPTE, AUJOURDHUI);

        avenir.exigerRetirablePar(SON_COMPTE, AUJOURDHUI);
        refuse(() -> aujourdhui.exigerRetirablePar(SON_COMPTE, AUJOURDHUI), "ABSENCE_NON_ANNULABLE");
    }

    @Test
    void should_reserve_the_withdrawal_of_an_absence_entered_by_the_administration_to_the_administration() {
        AbsenceInfirmier parAdministration = parAdmin(periode(5, 6), null);

        refuse(() -> parAdministration.exigerRetirablePar(SON_COMPTE, AUJOURDHUI), "ABSENCE_NON_ANNULABLE");
    }

    @Test
    void should_keep_an_absence_without_known_author_withdrawable_by_the_nurse() {
        AbsenceInfirmier ancienne = AbsenceInfirmier.reconstituer(new AbsenceId(UUID.randomUUID()), CENTRE, INFIRMIER, periode(5, 6),
                TypeAbsence.CONGE, null, null);

        ancienne.exigerRetirablePar(SON_COMPTE, AUJOURDHUI);
        assertThat(ancienne.auteur()).isEmpty();
    }
}
