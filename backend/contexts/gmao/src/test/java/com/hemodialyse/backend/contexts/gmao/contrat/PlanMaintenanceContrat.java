package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.PlanMaintenanceVue;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé des ports de persistance et de lecture des plans de maintenance : exécuté contre le double in-memory
 * et contre l'adapter JPA (même suite => le double est fidèle). Les comportements legacy reproduits y sont verrouillés
 * (KB-GMAO-15, 17, 20).
 */
public abstract class PlanMaintenanceContrat {

    protected static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    protected static final Instant PREVUE = Instant.parse("2026-04-01T08:00:00Z");
    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996000-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996000-0000-0000-0000-00000000000b"));
    protected static final EquipementId EQUIPEMENT_1 = new EquipementId(UUID.fromString("99996000-0000-0000-0000-0000000000e1"));
    protected static final EquipementId EQUIPEMENT_2 = new EquipementId(UUID.fromString("99996000-0000-0000-0000-0000000000e2"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99996000-0000-0000-0000-0000000000f1"));

    private int sequence = 0;

    protected abstract PlanMaintenanceRepository repository();

    protected abstract LecturePlans lecture();

    /** Pose une suppression logique en dehors du domaine (comme le legacy ou un script SQL). */
    protected abstract void supprimerLogiquement(PlanMaintenanceId id);

    protected PlanMaintenance nouveauPlan(CentreId centre, EquipementId equipement, String designation, Instant prevue) {
        PlanMaintenanceId id = new PlanMaintenanceId(UUID.fromString(String.format("99996000-0000-0000-0000-%012d", ++sequence)));
        PlanMaintenance plan = PlanMaintenance.etablir(id, equipement, centre, designation, "description " + designation,
                FrequenceMaintenance.MENSUELLE, prevue, "tâches", ADMIN, T0);
        repository().enregistrer(plan);
        return plan;
    }

    @Test
    void should_restore_all_fields_of_an_established_plan() {
        PlanMaintenance plan = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Désinfection", PREVUE);

        PlanMaintenance relu = repository().trouverParId(plan.id()).orElseThrow();

        assertThat(relu.id()).isEqualTo(plan.id());
        assertThat(relu.equipementId()).isEqualTo(EQUIPEMENT_1);
        assertThat(relu.centreId()).isEqualTo(CENTRE_A);
        assertThat(relu.designation()).isEqualTo("Désinfection");
        assertThat(relu.description()).isEqualTo("description Désinfection");
        assertThat(relu.frequence()).isEqualTo(FrequenceMaintenance.MENSUELLE);
        assertThat(relu.statut()).isEqualTo(StatutPlan.ACTIF);
        assertThat(relu.prochaineDatePrevue()).isEqualTo(PREVUE);
        assertThat(relu.derniereExecution()).isNull();
        assertThat(relu.nombreExecutions()).isZero();
        assertThat(relu.tachesAEffectuer()).isEqualTo("tâches");
        assertThat(relu.creeLe()).isEqualTo(T0);
        assertThat(relu.creePar()).isEqualTo(ADMIN);
        assertThat(relu.modifieLe()).isNull();
        assertThat(relu.modifiePar()).isNull();
        assertThat(relu.tirerEvenements()).isEmpty();
    }

    @Test
    void should_accept_optional_description_and_taches_to_be_absent() {
        PlanMaintenanceId id = new PlanMaintenanceId(UUID.fromString("99996000-0000-0000-0000-0000000000d1"));
        repository().enregistrer(PlanMaintenance.etablir(id, EQUIPEMENT_1, CENTRE_A, "Minimal", null,
                FrequenceMaintenance.ANNUELLE, PREVUE, null, ADMIN, T0));

        PlanMaintenance relu = repository().trouverParId(id).orElseThrow();

        assertThat(relu.description()).isNull();
        assertThat(relu.tachesAEffectuer()).isNull();
    }

    @Test
    void should_return_empty_when_plan_is_unknown() {
        assertThat(repository().trouverParId(new PlanMaintenanceId(UUID.fromString("99996000-0000-0000-0000-0000000000ff")))).isEmpty();
        assertThat(lecture().parId(new PlanMaintenanceId(UUID.fromString("99996000-0000-0000-0000-0000000000ff")))).isEmpty();
    }

    @Test
    void should_persist_state_changes_of_a_reloaded_plan() {
        PlanMaintenance plan = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Cycle", PREVUE);
        Instant t1 = Instant.parse("2026-03-15T10:30:00Z");

        PlanMaintenance charge = repository().trouverParId(plan.id()).orElseThrow();
        charge.enregistrerExecution(ADMIN, t1);
        repository().enregistrer(charge);
        charge.suspendre(ADMIN, t1);
        repository().enregistrer(charge);

        PlanMaintenance relu = repository().trouverParId(plan.id()).orElseThrow();
        assertThat(relu.statut()).isEqualTo(StatutPlan.INACTIF);
        assertThat(relu.nombreExecutions()).isEqualTo(1);
        assertThat(relu.derniereExecution()).isEqualTo(t1);
        assertThat(relu.prochaineDatePrevue()).isEqualTo(FrequenceMaintenance.MENSUELLE.prochaineDate(t1));
        assertThat(relu.modifieLe()).isEqualTo(t1);
        assertThat(relu.modifiePar()).isEqualTo(ADMIN);
        assertThat(relu.creeLe()).isEqualTo(T0);
    }

    @Test
    void should_read_the_view_of_a_plan_by_id_whatever_the_centre() {
        PlanMaintenance plan = nouveauPlan(CENTRE_B, EQUIPEMENT_2, "Autre centre", PREVUE);

        PlanMaintenanceVue vue = lecture().parId(plan.id()).orElseThrow();

        assertThat(vue.id()).isEqualTo(plan.id().valeur());
        assertThat(vue.centreId()).isEqualTo(CENTRE_B.valeur());
        assertThat(vue.designation()).isEqualTo("Autre centre");
        assertThat(vue.statut()).isEqualTo(StatutPlan.ACTIF);
    }

    @Test
    void should_list_the_plans_of_a_centre_with_every_statut_but_not_other_centres() {
        nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Actif", PREVUE);
        PlanMaintenance asuspendre = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Suspendu", PREVUE);
        asuspendre.suspendre(ADMIN, T0);
        repository().enregistrer(asuspendre);
        nouveauPlan(CENTRE_B, EQUIPEMENT_2, "Autre centre", PREVUE);

        assertThat(lecture().duCentre(CENTRE_A)).extracting(PlanMaintenanceVue::designation)
                .containsExactlyInAnyOrder("Actif", "Suspendu");
        assertThat(lecture().duCentre(new CentreId(UUID.fromString("99996000-0000-0000-0000-00000000000c")))).isEmpty();
    }

    @Test
    void should_list_only_actifs_of_a_centre() {
        nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Actif", PREVUE);
        PlanMaintenance asuspendre = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Suspendu", PREVUE);
        asuspendre.suspendre(ADMIN, T0);
        repository().enregistrer(asuspendre);

        assertThat(lecture().actifsDuCentre(CENTRE_A)).extracting(PlanMaintenanceVue::designation).containsExactly("Actif");
    }

    @Test
    void should_list_en_retard_actifs_with_date_before_or_equal_to_the_given_instant() {
        nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Echu pile", PREVUE);
        nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Echu avant", PREVUE.minusSeconds(3600));
        nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Futur", PREVUE.plusSeconds(1));
        PlanMaintenance suspenduEchu = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Suspendu echu", PREVUE.minusSeconds(60));
        suspenduEchu.suspendre(ADMIN, T0);
        repository().enregistrer(suspenduEchu);
        nouveauPlan(CENTRE_B, EQUIPEMENT_2, "Autre centre echu", PREVUE.minusSeconds(60));

        assertThat(lecture().enRetardDuCentre(CENTRE_A, PREVUE)).extracting(PlanMaintenanceVue::designation)
                .containsExactlyInAnyOrder("Echu pile", "Echu avant");
    }

    @Test
    void should_list_plans_of_an_equipement_whatever_the_centre_and_the_statut() {
        nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Centre A", PREVUE);
        PlanMaintenance suspendu = nouveauPlan(CENTRE_B, EQUIPEMENT_1, "Centre B suspendu", PREVUE);
        suspendu.suspendre(ADMIN, T0);
        repository().enregistrer(suspendu);
        nouveauPlan(CENTRE_A, EQUIPEMENT_2, "Autre equipement", PREVUE);

        assertThat(lecture().deEquipement(EQUIPEMENT_1)).extracting(PlanMaintenanceVue::designation)
                .containsExactlyInAnyOrder("Centre A", "Centre B suspendu");
    }

    @Test
    void should_hide_logically_deleted_plans_from_every_list_but_not_from_the_lookup_by_id() {
        // KB-GMAO-17 (reproduit) : seules les listes appliquent la suppression logique
        PlanMaintenance plan = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Supprimé", PREVUE.minusSeconds(60));
        supprimerLogiquement(plan.id());

        assertThat(lecture().duCentre(CENTRE_A)).isEmpty();
        assertThat(lecture().actifsDuCentre(CENTRE_A)).isEmpty();
        assertThat(lecture().enRetardDuCentre(CENTRE_A, PREVUE)).isEmpty();
        assertThat(lecture().deEquipement(EQUIPEMENT_1)).isEmpty();
        assertThat(lecture().parId(plan.id())).isPresent();
        assertThat(repository().trouverParId(plan.id())).isPresent();
    }

    @Test
    void should_resurrect_a_logically_deleted_plan_when_it_is_saved_again() {
        // KB-GMAO-17 (reproduit, décision utilisateur ADR 0019) : toute commande sur un plan supprimé le fait réapparaître
        PlanMaintenance plan = nouveauPlan(CENTRE_A, EQUIPEMENT_1, "Ressuscité", PREVUE);
        supprimerLogiquement(plan.id());
        PlanMaintenance charge = repository().trouverParId(plan.id()).orElseThrow();
        charge.enregistrerExecution(ADMIN, T0);

        repository().enregistrer(charge);

        assertThat(lecture().duCentre(CENTRE_A)).extracting(PlanMaintenanceVue::designation).containsExactly("Ressuscité");
    }
}
