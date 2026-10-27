package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants P16 (RG-INF-050 à 052) : éligibilité du remplaçant, jamais soi-même, date, remplacé concerné.
 */
class RemplacementTest {

    private static final InfirmierId REMPLACANT = new InfirmierId(UUID.randomUUID());
    private static final InfirmierId REMPLACE = new InfirmierId(UUID.randomUUID());
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 2);

    private static void refuse(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RegleDePlanificationViolee.class)
                .extracting(e -> ((RegleDePlanificationViolee) e).code()).isEqualTo(code);
    }

    private static RemplacantEligible.Situation situation(boolean actif, boolean habilite, boolean salleDIsolement, boolean absent,
                                                          boolean dejaPrevu) {
        return new RemplacantEligible.Situation(REMPLACANT, actif, habilite, salleDIsolement, absent, dejaPrevu);
    }

    @Test
    void should_prove_the_eligibility_of_an_active_free_nurse() {
        assertThat(RemplacantEligible.prouver(situation(true, false, false, false, false)).infirmier()).isEqualTo(REMPLACANT);
    }

    @Test
    void should_refuse_a_nurse_who_is_absent_already_planned_on_the_slot_or_inactive() {
        refuse(() -> RemplacantEligible.prouver(situation(true, true, false, true, false)), "REMPLACEMENT_INELIGIBLE");
        refuse(() -> RemplacantEligible.prouver(situation(true, true, false, false, true)), "REMPLACEMENT_INELIGIBLE");
        refuse(() -> RemplacantEligible.prouver(situation(false, true, false, false, false)), "REMPLACEMENT_INELIGIBLE");
    }

    @Test
    void should_require_the_isolation_qualification_in_an_isolation_room_only() {
        refuse(() -> RemplacantEligible.prouver(situation(true, false, true, false, false)), "REMPLACEMENT_INELIGIBLE");
        assertThat(RemplacantEligible.prouver(situation(true, true, true, false, false))).isNotNull();
        assertThat(RemplacantEligible.prouver(situation(true, false, false, false, false))).isNotNull();
    }

    @Test
    void should_refuse_a_nurse_replacing_herself_and_accept_no_replaced_nurse() {
        assertThatThrownBy(() -> Remplacement.personnesDistinctes(REMPLACANT, REMPLACANT)).isInstanceOf(SaisieInvalide.class);
        Remplacement.personnesDistinctes(REMPLACANT, null);
        Remplacement.personnesDistinctes(REMPLACANT, REMPLACE);
    }

    @Test
    void should_refuse_a_past_date_and_a_closed_day_but_accept_today_when_open() {
        Remplacement.exigerUneDateDeTravail(AUJOURDHUI, AUJOURDHUI, true);
        refuse(() -> Remplacement.exigerUneDateDeTravail(AUJOURDHUI.minusDays(1), AUJOURDHUI, true), "REMPLACEMENT_DATE_PASSEE");
        refuse(() -> Remplacement.exigerUneDateDeTravail(AUJOURDHUI, AUJOURDHUI, false), "REMPLACEMENT_JOUR_FERME");
    }

    @Test
    void should_require_the_replaced_nurse_to_be_absent_or_planned_on_the_case() {
        Remplacement.exigerUnRemplaceConcerne(true, false);
        Remplacement.exigerUnRemplaceConcerne(false, true);
        refuse(() -> Remplacement.exigerUnRemplaceConcerne(false, false), "REMPLACEMENT_REMPLACE_NON_CONCERNE");
    }

    @Test
    void should_not_replay_the_rules_on_an_inherited_row_so_that_it_stays_cancellable() {
        Remplacement heritee = Remplacement.reconstituer(new RemplacementId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), AUJOURDHUI,
                new SalleId(UUID.randomUUID()), new CreneauId(UUID.randomUUID()), REMPLACANT, REMPLACANT);

        assertThat(heritee.remplace()).contains(REMPLACANT);
        assertThatThrownBy(() -> Remplacement.affecter(new RemplacementId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), AUJOURDHUI,
                new SalleId(UUID.randomUUID()), new CreneauId(UUID.randomUUID()),
                RemplacantEligible.prouver(situation(true, true, false, false, false)), REMPLACANT)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_assign_the_proven_replacement_to_the_case() {
        CentreId centre = new CentreId(UUID.randomUUID());
        SalleId salle = new SalleId(UUID.randomUUID());
        CreneauId creneau = new CreneauId(UUID.randomUUID());

        Remplacement r = Remplacement.affecter(new RemplacementId(UUID.randomUUID()), centre, AUJOURDHUI, salle, creneau,
                RemplacantEligible.prouver(situation(true, true, false, false, false)), REMPLACE);

        assertThat(r.remplacant()).isEqualTo(REMPLACANT);
        assertThat(r.remplace()).contains(REMPLACE);
        assertThat(r.date()).isEqualTo(AUJOURDHUI);
    }
}
