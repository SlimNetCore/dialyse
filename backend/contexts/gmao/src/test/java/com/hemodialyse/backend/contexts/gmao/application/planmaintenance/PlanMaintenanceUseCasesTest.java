package com.hemodialyse.backend.contexts.gmao.application.planmaintenance;

import com.hemodialyse.backend.contexts.gmao.doubles.EquipementsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.PlanMaintenanceEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.FrequenceMaintenance;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.MaintenancePreventiveExecutee;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceEtabli;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepris;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceSuspendu;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanNonActif;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanNonSuspendu;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.StatutPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanMaintenanceUseCasesTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-03-15T10:30:00Z");
    private static final Instant PREVUE = Instant.parse("2026-04-01T08:00:00Z");
    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final EquipementId EQUIPEMENT = new EquipementId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

    private PlanMaintenanceEnMemoire plans;
    private PublicateurEnMemoire publicateur;
    private HorlogeFixe horloge;
    private UniteDeTravailDirecte uniteDeTravail;
    private EquipementsEnMemoire equipements;

    private EtablirPlan etablirPlan;
    private EnregistrerExecution enregistrerExecution;
    private SuspendrePlan suspendrePlan;
    private ReprendrePlan reprendrePlan;
    private ConsulterPlan consulterPlan;
    private ListerPlansDuCentre listerPlansDuCentre;
    private ListerPlansActifs listerPlansActifs;
    private ListerPlansEnRetard listerPlansEnRetard;
    private ListerPlansDEquipement listerPlansDEquipement;

    @BeforeEach
    void setUp() {
        plans = new PlanMaintenanceEnMemoire();
        publicateur = new PublicateurEnMemoire();
        horloge = new HorlogeFixe(T0);
        uniteDeTravail = new UniteDeTravailDirecte();
        equipements = new EquipementsEnMemoire().avec(EQUIPEMENT);
        etablirPlan = new EtablirPlan(plans, publicateur, horloge, new IdentifiantsSequentiels(), uniteDeTravail, equipements);
        enregistrerExecution = new EnregistrerExecution(plans, publicateur, horloge, uniteDeTravail);
        suspendrePlan = new SuspendrePlan(plans, publicateur, horloge, uniteDeTravail);
        reprendrePlan = new ReprendrePlan(plans, publicateur, horloge, uniteDeTravail);
        consulterPlan = new ConsulterPlan(plans);
        listerPlansDuCentre = new ListerPlansDuCentre(plans);
        listerPlansActifs = new ListerPlansActifs(plans);
        listerPlansEnRetard = new ListerPlansEnRetard(plans, horloge);
        listerPlansDEquipement = new ListerPlansDEquipement(plans);
    }

    private PlanMaintenanceVue etablir(CentreId centre, String designation, FrequenceMaintenance frequence, Instant prevue) {
        return etablirPlan.executer(new EtablirPlanCommande(EQUIPEMENT, centre, designation, "description", frequence,
                prevue, "tâches", ADMIN));
    }

    // ------------------------------------------------------------------ établir

    @Test
    void should_etablir_plan_actif_persist_it_and_publish_PlanMaintenanceEtabli() {
        PlanMaintenanceVue vue = etablir(CENTRE_A, "Désinfection", FrequenceMaintenance.MENSUELLE, PREVUE);

        assertThat(vue.statut()).isEqualTo(StatutPlan.ACTIF);
        assertThat(vue.centreId()).isEqualTo(CENTRE_A.valeur());
        assertThat(vue.creePar()).isEqualTo(ADMIN.valeur());
        assertThat(vue.dateCreation()).isEqualTo(T0);
        assertThat(plans.parId(new PlanMaintenanceId(vue.id()))).isPresent();
        assertThat(publicateur.publies).hasSize(1).first().isInstanceOf(PlanMaintenanceEtabli.class);
        assertThat(uniteDeTravail.executions).isEqualTo(1);
    }

    @Test
    void should_generate_the_plan_identity_through_the_identifier_port() {
        PlanMaintenanceVue premier = etablir(CENTRE_A, "A", FrequenceMaintenance.MENSUELLE, PREVUE);
        PlanMaintenanceVue second = etablir(CENTRE_A, "B", FrequenceMaintenance.MENSUELLE, PREVUE);

        assertThat(premier.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(second.id()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    void should_neither_persist_nor_publish_when_etablir_is_rejected() {
        assertThatThrownBy(() -> etablir(CENTRE_A, "  ", FrequenceMaintenance.MENSUELLE, PREVUE))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(listerPlansDuCentre.executer(CENTRE_A)).isEmpty();
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_refuse_etablir_for_an_unknown_equipement_without_persisting_nor_publishing() {
        // KB-GMAO-14 / E-PG-1 (écart de parité accepté 2026-10-10) : refus explicite, plus de plan orphelin
        EquipementId inconnu = new EquipementId(UUID.fromString("00000000-0000-0000-0000-0000000000e9"));

        assertThatThrownBy(() -> etablirPlan.executer(new EtablirPlanCommande(inconnu, CENTRE_A, "Plan orphelin", null,
                FrequenceMaintenance.MENSUELLE, PREVUE, null, ADMIN)))
                .isInstanceOf(EquipementInconnu.class)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Équipement non trouvé");

        assertThat(listerPlansDuCentre.executer(CENTRE_A)).isEmpty();
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_refuse_etablir_when_the_equipement_has_been_removed_from_the_referential() {
        equipements.supprimer(EQUIPEMENT);

        assertThatThrownBy(() -> etablir(CENTRE_A, "Plan", FrequenceMaintenance.MENSUELLE, PREVUE))
                .isInstanceOf(EquipementInconnu.class);
    }

    @Test
    void should_not_check_the_equipement_for_commands_on_an_existing_plan() {
        PlanMaintenanceVue plan = etablir(CENTRE_A, "Plan", FrequenceMaintenance.MENSUELLE, PREVUE);
        equipements.supprimer(EQUIPEMENT);

        assertThat(enregistrerExecution.executer(CENTRE_A, new PlanMaintenanceId(plan.id()), ADMIN).nombreExecutions()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ exécuter

    @Test
    void should_enregistrer_execution_persist_and_publish_MaintenancePreventiveExecutee() {
        PlanMaintenanceVue cree = etablir(CENTRE_A, "Hebdo", FrequenceMaintenance.HEBDOMADAIRE, PREVUE);
        publicateur.publies.clear();
        horloge.avancerA(T1);

        PlanMaintenanceVue vue = enregistrerExecution.executer(CENTRE_A, new PlanMaintenanceId(cree.id()), ADMIN);

        assertThat(vue.nombreExecutions()).isEqualTo(1);
        assertThat(vue.derniereDateExecution()).isEqualTo(T1);
        assertThat(vue.prochaineDatePrevue()).isEqualTo(Instant.parse("2026-03-22T10:30:00Z"));
        assertThat(consulterPlan.executer(CENTRE_A, new PlanMaintenanceId(cree.id()))).contains(vue);
        assertThat(publicateur.publies).hasSize(1).first().isInstanceOf(MaintenancePreventiveExecutee.class);
    }

    @Test
    void should_reject_execution_without_publishing_when_plan_suspendu() {
        PlanMaintenanceVue cree = etablir(CENTRE_A, "X", FrequenceMaintenance.MENSUELLE, PREVUE);
        suspendrePlan.executer(CENTRE_A, new PlanMaintenanceId(cree.id()), ADMIN);
        publicateur.publies.clear();

        assertThatThrownBy(() -> enregistrerExecution.executer(CENTRE_A, new PlanMaintenanceId(cree.id()), ADMIN))
                .isInstanceOf(PlanNonActif.class);
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_report_plan_non_trouve_as_illegal_argument_when_command_targets_unknown_plan() {
        // KB-GMAO-16 : plan inconnu sur une commande => IllegalArgumentException "Plan non trouvé" (HTTP 400, pas 404)
        PlanMaintenanceId inconnu = new PlanMaintenanceId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));

        assertThatThrownBy(() -> enregistrerExecution.executer(CENTRE_A, inconnu, ADMIN))
                .isInstanceOf(PlanIntrouvable.class).isInstanceOf(IllegalArgumentException.class).hasMessage("Plan non trouvé");
        assertThatThrownBy(() -> suspendrePlan.executer(CENTRE_A, inconnu, ADMIN)).isInstanceOf(PlanIntrouvable.class);
        assertThatThrownBy(() -> reprendrePlan.executer(CENTRE_A, inconnu, PREVUE, ADMIN)).isInstanceOf(PlanIntrouvable.class);
    }

    @Test
    void should_treat_a_plan_of_another_centre_as_unknown_on_commands_without_changing_it() {
        // KB-GMAO-15 corrigé (décision utilisateur 2026-10-12) : même erreur que « plan inconnu » (KB-GMAO-16)
        PlanMaintenanceVue planB = etablir(CENTRE_B, "Plan B", FrequenceMaintenance.MENSUELLE, PREVUE);
        PlanMaintenanceId id = new PlanMaintenanceId(planB.id());
        publicateur.publies.clear();

        assertThatThrownBy(() -> enregistrerExecution.executer(CENTRE_A, id, ADMIN)).isInstanceOf(PlanIntrouvable.class);
        assertThatThrownBy(() -> suspendrePlan.executer(CENTRE_A, id, ADMIN)).isInstanceOf(PlanIntrouvable.class);
        assertThatThrownBy(() -> reprendrePlan.executer(CENTRE_A, id, PREVUE, ADMIN)).isInstanceOf(PlanIntrouvable.class);
        assertThat(consulterPlan.executer(CENTRE_B, id)).hasValueSatisfying(vue -> {
            assertThat(vue.nombreExecutions()).isZero();
            assertThat(vue.statut()).isEqualTo(StatutPlan.ACTIF);
        });
        assertThat(publicateur.publies).isEmpty();
    }

    // ------------------------------------------------------------------ suspendre / reprendre

    @Test
    void should_suspendre_then_reprendre_with_new_date_and_publish_both_events() {
        PlanMaintenanceVue cree = etablir(CENTRE_A, "Cycle", FrequenceMaintenance.SEMESTRIELLE, PREVUE);
        PlanMaintenanceId id = new PlanMaintenanceId(cree.id());
        publicateur.publies.clear();
        horloge.avancerA(T1);
        Instant nouvelle = Instant.parse("2027-01-01T00:00:00Z");

        PlanMaintenanceVue suspendu = suspendrePlan.executer(CENTRE_A, id, ADMIN);
        PlanMaintenanceVue repris = reprendrePlan.executer(CENTRE_A, id, nouvelle, ADMIN);

        assertThat(suspendu.statut()).isEqualTo(StatutPlan.INACTIF);
        assertThat(repris.statut()).isEqualTo(StatutPlan.ACTIF);
        assertThat(repris.prochaineDatePrevue()).isEqualTo(nouvelle);
        assertThat(publicateur.publies).hasSize(2);
        assertThat(publicateur.publies.get(0)).isInstanceOf(PlanMaintenanceSuspendu.class);
        assertThat(publicateur.publies.get(1)).isInstanceOf(PlanMaintenanceRepris.class);
    }

    @Test
    void should_reject_reprise_when_plan_actif() {
        PlanMaintenanceVue cree = etablir(CENTRE_A, "X", FrequenceMaintenance.MENSUELLE, PREVUE);

        assertThatThrownBy(() -> reprendrePlan.executer(CENTRE_A, new PlanMaintenanceId(cree.id()), PREVUE, ADMIN))
                .isInstanceOf(PlanNonSuspendu.class);
    }

    // ------------------------------------------------------------------ requêtes

    @Test
    void should_list_plans_of_the_centre_only() {
        etablir(CENTRE_A, "Plan A", FrequenceMaintenance.MENSUELLE, PREVUE);
        etablir(CENTRE_B, "Plan B", FrequenceMaintenance.MENSUELLE, PREVUE);

        assertThat(listerPlansDuCentre.executer(CENTRE_A)).extracting(PlanMaintenanceVue::designation).containsExactly("Plan A");
    }

    @Test
    void should_list_only_actifs_of_the_centre() {
        etablir(CENTRE_A, "Actif", FrequenceMaintenance.MENSUELLE, PREVUE);
        PlanMaintenanceVue aSuspendre = etablir(CENTRE_A, "Suspendu", FrequenceMaintenance.MENSUELLE, PREVUE);
        suspendrePlan.executer(CENTRE_A, new PlanMaintenanceId(aSuspendre.id()), ADMIN);

        assertThat(listerPlansActifs.executer(CENTRE_A)).extracting(PlanMaintenanceVue::designation).containsExactly("Actif");
        assertThat(listerPlansDuCentre.executer(CENTRE_A)).hasSize(2);
    }

    @Test
    void should_list_en_retard_actifs_whose_date_is_reached_at_the_clock_instant() {
        etablir(CENTRE_A, "Echu", FrequenceMaintenance.MENSUELLE, T0);
        etablir(CENTRE_A, "Futur", FrequenceMaintenance.MENSUELLE, T0.plusSeconds(1));
        PlanMaintenanceVue suspenduEchu = etablir(CENTRE_A, "Suspendu echu", FrequenceMaintenance.MENSUELLE, T0.minusSeconds(60));
        suspendrePlan.executer(CENTRE_A, new PlanMaintenanceId(suspenduEchu.id()), ADMIN);

        assertThat(listerPlansEnRetard.executer(CENTRE_A)).extracting(PlanMaintenanceVue::designation).containsExactly("Echu");
    }

    @Test
    void should_list_plans_of_an_equipement_for_the_centre_whatever_the_statut() {
        // KB-GMAO-15 corrigé : les plans d'un autre centre pour le même équipement sont ignorés
        etablir(CENTRE_A, "Plan A", FrequenceMaintenance.MENSUELLE, PREVUE);
        etablir(CENTRE_B, "Plan B", FrequenceMaintenance.MENSUELLE, PREVUE);

        assertThat(listerPlansDEquipement.executer(CENTRE_A, EQUIPEMENT)).extracting(PlanMaintenanceVue::designation)
                .containsExactly("Plan A");
        assertThat(listerPlansDEquipement.executer(CENTRE_B, EQUIPEMENT)).extracting(PlanMaintenanceVue::designation)
                .containsExactly("Plan B");
    }

    @Test
    void should_not_consult_plan_of_another_centre_by_id() {
        // KB-GMAO-15 corrigé : un plan d'un autre centre est traité comme inexistant
        PlanMaintenanceVue cree = etablir(CENTRE_B, "Plan B", FrequenceMaintenance.MENSUELLE, PREVUE);

        assertThat(consulterPlan.executer(CENTRE_B, new PlanMaintenanceId(cree.id()))).isPresent();
        assertThat(consulterPlan.executer(CENTRE_A, new PlanMaintenanceId(cree.id()))).isEmpty();
        assertThat(consulterPlan.executer(CENTRE_A, new PlanMaintenanceId(UUID.fromString("00000000-0000-0000-0000-0000000000ff")))).isEmpty();
    }
}
