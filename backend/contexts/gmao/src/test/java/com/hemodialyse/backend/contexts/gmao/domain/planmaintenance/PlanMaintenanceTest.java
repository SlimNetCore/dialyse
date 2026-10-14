package com.hemodialyse.backend.contexts.gmao.domain.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanMaintenanceTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-03-15T10:30:00Z");
    private static final Instant PREVUE = Instant.parse("2026-04-01T08:00:00Z");
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UtilisateurId AUTRE_ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final EquipementId EQUIPEMENT = new EquipementId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
    private static final PlanMaintenanceId ID = new PlanMaintenanceId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static PlanMaintenance planActif(FrequenceMaintenance frequence) {
        return PlanMaintenance.etablir(ID, EQUIPEMENT, CENTRE, "Désinfection", "desc", frequence, PREVUE, "tâches", ADMIN, T0);
    }

    private static PlanMaintenance planSuspendu() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.MENSUELLE);
        plan.suspendre(ADMIN, T0);
        return plan;
    }

    // ------------------------------------------------------------------ établir

    @Test
    void should_etablir_plan_actif_with_zero_executions_and_no_modification() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.TRIMESTRIELLE);

        assertThat(plan.id()).isEqualTo(ID);
        assertThat(plan.equipementId()).isEqualTo(EQUIPEMENT);
        assertThat(plan.centreId()).isEqualTo(CENTRE);
        assertThat(plan.designation()).isEqualTo("Désinfection");
        assertThat(plan.description()).isEqualTo("desc");
        assertThat(plan.tachesAEffectuer()).isEqualTo("tâches");
        assertThat(plan.frequence()).isEqualTo(FrequenceMaintenance.TRIMESTRIELLE);
        assertThat(plan.statut()).isEqualTo(StatutPlan.ACTIF);
        assertThat(plan.nombreExecutions()).isZero();
        assertThat(plan.prochaineDatePrevue()).isEqualTo(PREVUE);
        assertThat(plan.derniereExecution()).isNull();
        assertThat(plan.creeLe()).isEqualTo(T0);
        assertThat(plan.creePar()).isEqualTo(ADMIN);
        assertThat(plan.modifieLe()).isNull();
        assertThat(plan.modifiePar()).isNull();
    }

    @Test
    void should_emit_PlanMaintenanceEtabli_when_plan_is_established() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.MENSUELLE);

        assertThat(plan.tirerEvenements()).containsExactly(
                new PlanMaintenanceEtabli(ID, EQUIPEMENT, CENTRE, FrequenceMaintenance.MENSUELLE, PREVUE, ADMIN, T0));
        assertThat(plan.tirerEvenements()).isEmpty();
    }

    @Test
    void should_accept_prochaine_date_in_the_past_when_etablir() {
        // KB-GMAO-14 : le legacy accepte une date passée (le plan est immédiatement en retard)
        PlanMaintenance plan = PlanMaintenance.etablir(ID, EQUIPEMENT, CENTRE, "X", null, FrequenceMaintenance.ANNUELLE,
                Instant.parse("2001-01-01T00:00:00Z"), null, ADMIN, T0);

        assertThat(plan.estEnRetard(T0)).isTrue();
    }

    @Test
    void should_reject_etablir_when_designation_blank() {
        assertThatThrownBy(() -> PlanMaintenance.etablir(ID, EQUIPEMENT, CENTRE, "   ", null,
                FrequenceMaintenance.MENSUELLE, PREVUE, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Désignation requise");
        assertThatThrownBy(() -> PlanMaintenance.etablir(ID, EQUIPEMENT, CENTRE, null, null,
                FrequenceMaintenance.MENSUELLE, PREVUE, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Désignation requise");
    }

    @Test
    void should_reject_etablir_when_a_required_value_is_missing() {
        assertThatThrownBy(() -> PlanMaintenance.etablir(ID, null, CENTRE, "X", null, FrequenceMaintenance.MENSUELLE, PREVUE, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Équipement requis");
        assertThatThrownBy(() -> PlanMaintenance.etablir(ID, EQUIPEMENT, null, "X", null, FrequenceMaintenance.MENSUELLE, PREVUE, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Centre requis");
        assertThatThrownBy(() -> PlanMaintenance.etablir(ID, EQUIPEMENT, CENTRE, "X", null, null, PREVUE, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Fréquence requise");
        assertThatThrownBy(() -> PlanMaintenance.etablir(ID, EQUIPEMENT, CENTRE, "X", null, FrequenceMaintenance.MENSUELLE, null, null, ADMIN, T0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Prochaine date prévue requise");
    }

    // ------------------------------------------------------------------ R25 exécution

    @Test
    void should_increment_executions_and_set_derniere_when_enregistrer_execution() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.HEBDOMADAIRE);
        plan.tirerEvenements();

        plan.enregistrerExecution(AUTRE_ADMIN, T1);

        assertThat(plan.nombreExecutions()).isEqualTo(1);
        assertThat(plan.derniereExecution()).isEqualTo(T1);
        assertThat(plan.modifieLe()).isEqualTo(T1);
        assertThat(plan.modifiePar()).isEqualTo(AUTRE_ADMIN);
        assertThat(plan.statut()).isEqualTo(StatutPlan.ACTIF);
    }

    @Test
    void should_compute_prochaine_date_from_execution_instant_not_from_planned_date() {
        // KB-GMAO-12 : base = instant de l'exécution, jamais la date prévue (ici avril 2026)
        PlanMaintenance plan = planActif(FrequenceMaintenance.HEBDOMADAIRE);

        plan.enregistrerExecution(ADMIN, T1);

        assertThat(plan.prochaineDatePrevue()).isEqualTo(Instant.parse("2026-03-22T10:30:00Z"));
    }

    @Test
    void should_emit_MaintenancePreventiveExecutee_with_new_prochaine_date() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.BIMENSUELLE);
        plan.tirerEvenements();

        plan.enregistrerExecution(ADMIN, T1);

        assertThat(plan.tirerEvenements()).containsExactly(
                new MaintenancePreventiveExecutee(ID, CENTRE, Instant.parse("2026-03-30T10:30:00Z"), 1, ADMIN, T1));
    }

    @Test
    void should_count_each_execution() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.MENSUELLE);

        plan.enregistrerExecution(ADMIN, T0);
        plan.enregistrerExecution(ADMIN, T1);

        assertThat(plan.nombreExecutions()).isEqualTo(2);
    }

    @Test
    void should_reject_execution_when_plan_inactif() {
        PlanMaintenance plan = planSuspendu();

        assertThatThrownBy(() -> plan.enregistrerExecution(ADMIN, T1))
                .isInstanceOf(PlanNonActif.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Seul un plan actif peut être exécuté");
        assertThat(plan.nombreExecutions()).isZero();
    }

    @Test
    void should_reject_execution_when_plan_archive() {
        PlanMaintenance plan = restaure(StatutPlan.ARCHIVE);

        assertThatThrownBy(() -> plan.enregistrerExecution(ADMIN, T1)).isInstanceOf(PlanNonActif.class);
    }

    // ------------------------------------------------------------------ R26 suspension / reprise

    @Test
    void should_suspend_only_actif() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.MENSUELLE);
        plan.tirerEvenements();

        plan.suspendre(AUTRE_ADMIN, T1);

        assertThat(plan.statut()).isEqualTo(StatutPlan.INACTIF);
        assertThat(plan.prochaineDatePrevue()).isEqualTo(PREVUE);
        assertThat(plan.modifieLe()).isEqualTo(T1);
        assertThat(plan.modifiePar()).isEqualTo(AUTRE_ADMIN);
        assertThat(plan.tirerEvenements()).containsExactly(new PlanMaintenanceSuspendu(ID, CENTRE, AUTRE_ADMIN, T1));
    }

    @Test
    void should_reject_suspension_when_plan_inactif_or_archive() {
        assertThatThrownBy(() -> planSuspendu().suspendre(ADMIN, T1))
                .isInstanceOf(PlanNonActif.class).hasMessage("Seul un plan actif peut être désactivé");
        assertThatThrownBy(() -> restaure(StatutPlan.ARCHIVE).suspendre(ADMIN, T1)).isInstanceOf(PlanNonActif.class);
    }

    @Test
    void should_resume_only_inactif_with_new_date() {
        PlanMaintenance plan = planSuspendu();
        plan.tirerEvenements();
        Instant nouvelle = Instant.parse("2031-05-01T07:30:00Z");

        plan.reprendre(nouvelle, AUTRE_ADMIN, T1);

        assertThat(plan.statut()).isEqualTo(StatutPlan.ACTIF);
        assertThat(plan.prochaineDatePrevue()).isEqualTo(nouvelle);
        assertThat(plan.modifieLe()).isEqualTo(T1);
        assertThat(plan.modifiePar()).isEqualTo(AUTRE_ADMIN);
        assertThat(plan.tirerEvenements()).containsExactly(new PlanMaintenanceRepris(ID, CENTRE, nouvelle, AUTRE_ADMIN, T1));
    }

    @Test
    void should_accept_reprise_with_date_in_the_past() {
        PlanMaintenance plan = planSuspendu();

        plan.reprendre(Instant.parse("2010-01-01T00:00:00Z"), ADMIN, T1);

        assertThat(plan.estEnRetard(T1)).isTrue();
    }

    @Test
    void should_reject_reprise_when_plan_actif_or_archive() {
        assertThatThrownBy(() -> planActif(FrequenceMaintenance.MENSUELLE).reprendre(PREVUE, ADMIN, T1))
                .isInstanceOf(PlanNonSuspendu.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Seul un plan inactif peut être réactivé");
        assertThatThrownBy(() -> restaure(StatutPlan.ARCHIVE).reprendre(PREVUE, ADMIN, T1)).isInstanceOf(PlanNonSuspendu.class);
    }

    @Test
    void should_reject_reprise_without_new_date() {
        assertThatThrownBy(() -> planSuspendu().reprendre(null, ADMIN, T1))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Nouvelle date prévue requise");
    }

    @Test
    void should_keep_counter_and_derniere_execution_through_suspension_and_reprise() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.SEMESTRIELLE);
        plan.enregistrerExecution(ADMIN, T0);
        plan.suspendre(ADMIN, T1);

        plan.reprendre(PREVUE, ADMIN, T1);
        plan.enregistrerExecution(ADMIN, T1);

        assertThat(plan.nombreExecutions()).isEqualTo(2);
        assertThat(plan.derniereExecution()).isEqualTo(T1);
    }

    // ------------------------------------------------------------------ R27 retard

    @Test
    void should_flag_plan_actif_due_today() {
        PlanMaintenance plan = planActif(FrequenceMaintenance.MENSUELLE);

        assertThat(plan.estEnRetard(PREVUE)).as("échéance == maintenant (KB-GMAO-20, borne <=)").isTrue();
        assertThat(plan.estEnRetard(PREVUE.plusSeconds(1))).isTrue();
        assertThat(plan.estEnRetard(PREVUE.minusSeconds(1))).isFalse();
    }

    @Test
    void should_not_flag_plan_inactif_or_archive_as_late() {
        assertThat(planSuspendu().estEnRetard(PREVUE.plusSeconds(1))).isFalse();
        assertThat(restaure(StatutPlan.ARCHIVE).estEnRetard(PREVUE.plusSeconds(1))).isFalse();
    }

    // ------------------------------------------------------------------ reconstitution

    @Test
    void should_not_emit_events_when_reconstituted() {
        assertThat(restaure(StatutPlan.ACTIF).tirerEvenements()).isEmpty();
    }

    private static PlanMaintenance restaure(StatutPlan statut) {
        return PlanMaintenance.reconstituer(ID, EQUIPEMENT, CENTRE, "Plan", null, FrequenceMaintenance.MENSUELLE, statut,
                PREVUE, null, 0, null, T0, null, ADMIN, null);
    }
}
