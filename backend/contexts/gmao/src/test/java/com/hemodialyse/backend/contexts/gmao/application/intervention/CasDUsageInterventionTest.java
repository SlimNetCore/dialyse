package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.equipement.ConstaterEtat;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementIntrouvable;
import com.hemodialyse.backend.contexts.gmao.application.equipement.Reformer;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.CorrigerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.CorrigerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.doubles.CapaciteDesSallesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.IntervenantsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.ParcEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementReformeImmuable;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.UniciteDuCodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionAnnulee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionDemarree;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionPlanifiee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRectifiee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionTerminee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.MainOeuvreRefuseePourExterne;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIncompatible;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des interventions (G4) sur adapters in-memory : R10 à R20, R28 et comportements reproduits du legacy. */
class CasDUsageInterventionTest {

    private static final Instant T0 = Instant.parse("2026-10-12T09:00:00Z");
    private static final Instant DEBUT = Instant.parse("2026-10-12T10:00:00Z");
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

    private ParcEnMemoire parc;
    private IntervenantsEnMemoire intervenants;
    private InterventionsEnMemoire interventions;
    private PublicateurEnMemoire publicateur;
    private UniteDeTravailDirecte unite;
    private HorlogeFixe horloge;
    private EnregistrerEquipement enregistrerEquipement;
    private Reformer reformerEquipement;
    private EnregistrerIntervenant enregistrerIntervenant;
    private CorrigerIntervenant corrigerIntervenant;
    private PlanifierIntervention planifier;
    private DemarrerIntervention demarrer;
    private TerminerIntervention terminer;
    private AjouterLigneDeCout ajouterLigne;
    private RetirerLigneDeCout retirerLigne;
    private Rectifier rectifier;
    private Annuler annuler;
    private ConsulterIntervention consulter;
    private ListerInterventions lister;

    @BeforeEach
    void setUp() {
        parc = new ParcEnMemoire();
        intervenants = new IntervenantsEnMemoire();
        interventions = new InterventionsEnMemoire();
        publicateur = new PublicateurEnMemoire();
        unite = new UniteDeTravailDirecte();
        horloge = new HorlogeFixe(T0);
        IdentifiantsSequentiels identifiants = new IdentifiantsSequentiels();
        enregistrerEquipement = new EnregistrerEquipement(parc, new UniciteDuCodeEquipement(parc),
                new CapaciteDesSallesEnMemoire(), publicateur, horloge, identifiants, unite);
        reformerEquipement = new Reformer(parc, publicateur, horloge, unite);
        enregistrerIntervenant = new EnregistrerIntervenant(intervenants, publicateur, horloge, identifiants, unite);
        corrigerIntervenant = new CorrigerIntervenant(intervenants, publicateur, horloge, unite);
        ConstaterEtat etatEquipement = new ConstaterEtat(parc, publicateur, horloge, unite);
        planifier = new PlanifierIntervention(interventions, parc, publicateur, horloge, identifiants, unite);
        demarrer = new DemarrerIntervention(interventions, etatEquipement, publicateur, horloge, unite);
        terminer = new TerminerIntervention(interventions, intervenants, etatEquipement, publicateur, horloge, identifiants,
                unite);
        ajouterLigne = new AjouterLigneDeCout(interventions, intervenants, horloge, identifiants, unite);
        retirerLigne = new RetirerLigneDeCout(interventions, horloge, unite);
        rectifier = new Rectifier(interventions, publicateur, horloge, identifiants, unite);
        annuler = new Annuler(interventions, publicateur, horloge, unite);
        consulter = new ConsulterIntervention(interventions, horloge);
        lister = new ListerInterventions(interventions, parc, horloge);
    }

    // ------------------------------------------------------------------ fixtures

    private EquipementId unEquipement(CentreId centre, String code) {
        return new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(centre, code, "Désignation",
                "GENERATEUR_DIALYSE", null, null, null, Instant.parse("2024-03-01T08:00:00Z"), null, null, null, ADMIN)).id());
    }

    private IntervenantId unIntervenant(CentreId centre, String nature, String tarif) {
        return new IntervenantId(enregistrerIntervenant.executer(new EnregistrerIntervenantCommande(centre, "Bernard", nature,
                null, null, tarif == null ? null : new BigDecimal(tarif), ADMIN)).id());
    }

    private InterventionVue planifiee(EquipementId equipement, IntervenantId intervenant) {
        return planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT, "Fuite",
                intervenant, "EN_MAINTENANCE", null, null, null, ADMIN));
    }

    private static InterventionId id(InterventionVue vue) {
        return new InterventionId(vue.id());
    }

    private InterventionVue enCours(EquipementId equipement, IntervenantId intervenant) {
        InterventionVue vue = planifiee(equipement, intervenant);
        demarrer.executer(id(vue), CENTRE, ADMIN);
        return vue;
    }

    private InterventionVue terminee(EquipementId equipement, IntervenantId intervenant, Duration duree) {
        InterventionVue vue = enCours(equipement, intervenant);
        return terminer.executer(new TerminerInterventionCommande(id(vue), CENTRE, "Joint remplacé", "EN_SERVICE",
                DEBUT.plus(duree), null, ADMIN));
    }

    private StatutEquipement statutDe(EquipementId equipement) {
        return parc.trouverParId(equipement).orElseThrow().statut();
    }

    private InterventionVue relue(InterventionVue vue) {
        return consulter.executer(id(vue), CENTRE).orElseThrow();
    }

    // ------------------------------------------------------------------ PlanifierIntervention

    @Test
    void should_plan_an_intervention_without_touching_the_equipment_and_publish_the_event() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        publicateur.publies.clear();

        InterventionVue vue = planifiee(equipement, null);

        assertThat(vue.statut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(vue.priorite()).isEqualTo(PrioriteIntervention.NORMALE);
        assertThat(vue.creeLe()).isEqualTo(T0);
        assertThat(vue.enRetard()).isFalse();
        assertThat(statutDe(equipement)).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(publicateur.publies).singleElement().isInstanceOf(InterventionPlanifiee.class);
        assertThat(relue(vue)).isEqualTo(vue);
    }

    @Test
    void should_plan_with_an_unchecked_intervenant_and_on_a_reformed_equipment() {
        EquipementId equipement = unEquipement(CENTRE, "G-RF");
        reformerEquipement.executer(equipement, CENTRE, "fin de vie", ADMIN);

        InterventionVue vue = planifiee(equipement, new IntervenantId(UUID.randomUUID()));

        assertThat(vue.statut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(vue.intervenantId()).isNotNull();
    }

    @Test
    void should_refuse_an_unknown_equipment_or_one_of_another_centre_before_looking_at_the_codes() {
        EquipementId autreCentre = unEquipement(AUTRE_CENTRE, "G-B");
        publicateur.publies.clear();

        for (EquipementId equipement : new EquipementId[]{autreCentre, new EquipementId(UUID.randomUUID())}) {
            assertThatThrownBy(() -> planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "INCONNU", DEBUT,
                    "x", null, "EN_SERVICE", null, null, null, ADMIN)))
                    .isInstanceOf(EquipementIntrouvable.class).hasMessage("Équipement non trouvé");
        }
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_refuse_the_unknown_codes_in_the_legacy_order() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");

        assertThatThrownBy(() -> planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "curative", DEBUT, "x",
                null, "CASSE", null, "CRITIQUE", null, ADMIN))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageEndingWith("TypeIntervention.curative");
        assertThatThrownBy(() -> planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT, "x",
                null, "CASSE", null, "CRITIQUE", null, ADMIN))).hasMessageEndingWith("StatutEquipement.CASSE");
        assertThatThrownBy(() -> planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT, "x",
                null, "EN_SERVICE", null, "CRITIQUE", null, ADMIN))).hasMessageEndingWith("PrioriteIntervention.CRITIQUE");
    }

    @Test
    void should_default_a_blank_priority_to_normal_and_keep_a_given_one() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");

        InterventionVue vide = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "PREVENTIVE", DEBUT, "x",
                null, "EN_SERVICE", null, " ", null, ADMIN));
        InterventionVue haute = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "PREVENTIVE", DEBUT, "x",
                null, "EN_SERVICE", null, "HAUTE", null, ADMIN));

        assertThat(vide.priorite()).isEqualTo(PrioriteIntervention.NORMALE);
        assertThat(haute.priorite()).isEqualTo(PrioriteIntervention.HAUTE);
    }

    // ------------------------------------------------------------------ DemarrerIntervention (R28)

    @Test
    void should_start_and_put_the_equipment_in_the_state_found_before_in_the_same_unit_of_work() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue vue = planifiee(equipement, null);
        publicateur.publies.clear();

        InterventionVue demarree = demarrer.executer(id(vue), CENTRE, ADMIN);

        assertThat(demarree.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(statutDe(equipement)).isEqualTo(StatutEquipement.EN_MAINTENANCE);
        assertThat(parc.historiqueDe(equipement)).last().satisfies(changement -> {
            assertThat(changement.nouveau()).isEqualTo(StatutEquipement.EN_MAINTENANCE);
            assertThat(changement.motif()).isEqualTo("Intervention démarrée");
        });
        assertThat(publicateur.publies).extracting(Object::getClass)
                .containsExactlyInAnyOrder(InterventionDemarree.class, EquipementMisHorsService.class);
    }

    @Test
    void should_leave_the_equipment_alone_when_it_is_already_in_the_state() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue vue = planier(equipement, "EN_SERVICE");
        publicateur.publies.clear();

        demarrer.executer(id(vue), CENTRE, ADMIN);

        assertThat(publicateur.publies).singleElement().isInstanceOf(InterventionDemarree.class);
        assertThat(parc.historiqueDe(equipement)).hasSize(1);
    }

    private InterventionVue planier(EquipementId equipement, String etatAvant) {
        return planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT, "x", null, etatAvant,
                null, null, null, ADMIN));
    }

    @Test
    void should_refuse_to_start_on_a_reformed_equipment_and_write_nothing() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue vue = planifiee(equipement, null);
        reformerEquipement.executer(equipement, CENTRE, "fin de vie", ADMIN);
        publicateur.publies.clear();

        assertThatThrownBy(() -> demarrer.executer(id(vue), CENTRE, ADMIN)).isInstanceOf(EquipementReformeImmuable.class);

        assertThat(relue(vue).statut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(publicateur.publies).isEmpty();
    }

    @Test
    void should_refuse_to_start_twice_and_an_unknown_or_foreign_intervention() {
        InterventionVue vue = enCours(unEquipement(CENTRE, "G-01"), null);

        assertThatThrownBy(() -> demarrer.executer(id(vue), CENTRE, ADMIN)).isInstanceOf(StatutIncompatible.class);
        assertThatThrownBy(() -> demarrer.executer(id(vue), AUTRE_CENTRE, ADMIN))
                .isInstanceOf(InterventionIntrouvable.class).hasMessage("Intervention non trouvée");
        assertThatThrownBy(() -> demarrer.executer(new InterventionId(UUID.randomUUID()), CENTRE, ADMIN))
                .isInstanceOf(InterventionIntrouvable.class).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ TerminerIntervention (R19, R28)

    @Test
    void should_finish_value_the_time_put_the_equipment_in_the_state_left_and_announce_the_closing() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        IntervenantId intervenant = unIntervenant(CENTRE, "INTERNE", "35.00");
        InterventionVue vue = enCours(equipement, intervenant);
        publicateur.publies.clear();

        InterventionVue terminee = terminer.executer(new TerminerInterventionCommande(id(vue), CENTRE, "Joint remplacé",
                "EN_SERVICE", DEBUT.plus(Duration.ofMinutes(20)), "  Usure ", ADMIN));

        assertThat(terminee.statut()).isEqualTo(StatutIntervention.TERMINEE);
        assertThat(terminee.cause()).isEqualTo("Usure");
        assertThat(terminee.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.type()).isEqualTo(TypeLigneCout.MAIN_OEUVRE);
            assertThat(ligne.quantite()).isEqualTo(new BigDecimal("0.33"));
            assertThat(ligne.montant()).isEqualTo(new BigDecimal("11.5500"));
        });
        assertThat(terminee.coutTotal()).isEqualTo(new BigDecimal("11.5500"));
        assertThat(statutDe(equipement)).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(parc.historiqueDe(equipement)).last().satisfies(changement ->
                assertThat(changement.motif()).isEqualTo("Intervention terminée"));
        assertThat(publicateur.publies).filteredOn(e -> e instanceof InterventionTerminee).singleElement()
                .isEqualTo(new InterventionTerminee(id(vue), equipement, CENTRE, terminee.type(), DEBUT,
                        DEBUT.plus(Duration.ofMinutes(20)), new BigDecimal("11.5500"), 1, ADMIN, T0));
        assertThat(relue(vue).lignes()).singleElement().satisfies(ligne ->
                assertThat(ligne.montant()).isEqualTo(new BigDecimal("11.55000")));
    }

    @Test
    void should_value_an_external_intervenant_as_fees() {
        InterventionVue terminee = terminee(unEquipement(CENTRE, "G-01"), unIntervenant(CENTRE, "EXTERNE", "120.50"),
                Duration.ofMinutes(90));

        assertThat(terminee.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.type()).isEqualTo(TypeLigneCout.INTERVENANT);
            assertThat(ligne.libelle()).isEqualTo("Honoraires intervenant");
        });
    }

    @Test
    void should_read_the_rate_and_the_nature_when_closing_not_when_planning() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        IntervenantId intervenant = unIntervenant(CENTRE, "INTERNE", "35.00");
        InterventionVue vue = enCours(equipement, intervenant);
        corrigerIntervenant.executer(new CorrigerIntervenantCommande(intervenant, CENTRE, "Bernard", "EXTERNE", null, null,
                new BigDecimal("50.00"), ADMIN));

        InterventionVue terminee = terminer.executer(new TerminerInterventionCommande(id(vue), CENTRE, "a", "EN_SERVICE",
                DEBUT.plus(Duration.ofHours(1)), null, ADMIN));

        assertThat(terminee.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.type()).isEqualTo(TypeLigneCout.INTERVENANT);
            assertThat(ligne.prixUnitaire()).isEqualByComparingTo("50.00");
        });
    }

    @Test
    void should_add_no_line_without_a_usable_intervenant() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");

        assertThat(terminee(equipement, null, Duration.ofHours(1)).lignes()).isEmpty();
        assertThat(terminee(equipement, new IntervenantId(UUID.randomUUID()), Duration.ofHours(1)).lignes()).isEmpty();
        assertThat(terminee(equipement, unIntervenant(AUTRE_CENTRE, "EXTERNE", "99.99"), Duration.ofHours(1)).lignes()).isEmpty();
        assertThat(terminee(equipement, unIntervenant(CENTRE, "INTERNE", null), Duration.ofHours(1)).lignes()).isEmpty();
        assertThat(terminee(equipement, unIntervenant(CENTRE, "INTERNE", "0"), Duration.ofHours(1)).lignes()).isEmpty();
    }

    @Test
    void should_refuse_the_closing_on_a_reformed_equipment_and_record_nothing() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue vue = enCours(equipement, unIntervenant(CENTRE, "INTERNE", "35.00"));
        reformerEquipement.executer(equipement, CENTRE, "fin de vie", ADMIN);
        publicateur.publies.clear();

        assertThatThrownBy(() -> terminer.executer(new TerminerInterventionCommande(id(vue), CENTRE, "a", "EN_SERVICE",
                DEBUT.plus(Duration.ofHours(1)), null, ADMIN))).isInstanceOf(EquipementReformeImmuable.class);

        InterventionVue relue = relue(vue);
        assertThat(relue.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(relue.lignes()).isEmpty();
        assertThat(publicateur.publies).as("l'événement d'une clôture refusée n'est pas publié").isEmpty();
    }

    @Test
    void should_refuse_an_unknown_state_after_only_once_the_intervention_is_found_and_before_the_domain_rules() {
        InterventionVue vue = planifiee(unEquipement(CENTRE, "G-01"), null);

        assertThatThrownBy(() -> terminer.executer(new TerminerInterventionCommande(id(vue), CENTRE, "a", "CASSE", DEBUT, null,
                ADMIN))).hasMessageEndingWith("StatutEquipement.CASSE");
        assertThatThrownBy(() -> terminer.executer(new TerminerInterventionCommande(
                new InterventionId(UUID.randomUUID()), CENTRE, "a", "CASSE", DEBUT, null, ADMIN)))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThatThrownBy(() -> terminer.executer(new TerminerInterventionCommande(id(vue), CENTRE, "a", "EN_SERVICE", DEBUT,
                null, ADMIN))).isInstanceOf(StatutIncompatible.class);
    }

    // ------------------------------------------------------------------ AjouterLigneDeCout / RetirerLigneDeCout (R16, R17, R20)

    private InterventionVue avecUneLigne(InterventionVue vue, String type, String quantite, String prix) {
        return ajouterLigne.executer(new AjouterLigneDeCoutCommande(id(vue), CENTRE, type, "Joint", new BigDecimal(quantite),
                new BigDecimal(prix), UUID.fromString("00000000-0000-0000-0000-00000000a771"), ADMIN));
    }

    @Test
    void should_add_a_cost_line_with_an_opaque_stock_article_and_keep_the_received_scale_in_the_answer() {
        InterventionVue vue = planifiee(unEquipement(CENTRE, "G-01"), null);

        InterventionVue reponse = avecUneLigne(vue, "PIECE", "3.333", "10.105");

        assertThat(reponse.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.montant()).isEqualTo(new BigDecimal("33.679965"));
            assertThat(ligne.articleStockId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-00000000a771"));
            assertThat(ligne.automatique()).isFalse();
        });
        assertThat(reponse.coutTotal()).isEqualTo(new BigDecimal("33.679965"));
        assertThat(relue(vue).lignes()).singleElement().satisfies(ligne ->
                assertThat(ligne.montant()).as("la base arrondit le prix à 2 décimales").isEqualTo(new BigDecimal("33.69663")));
    }

    @Test
    void should_refuse_manual_labour_for_an_external_intervenant_of_the_centre_only() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue avecExterne = planifiee(equipement, unIntervenant(CENTRE, "EXTERNE", "80"));
        InterventionVue avecExterneAilleurs = planifiee(equipement, unIntervenant(AUTRE_CENTRE, "EXTERNE", "80"));
        InterventionVue avecInterne = planifiee(equipement, unIntervenant(CENTRE, "INTERNE", "30"));

        assertThatThrownBy(() -> avecUneLigne(avecExterne, "MAIN_OEUVRE", "1", "10")).isInstanceOf(MainOeuvreRefuseePourExterne.class);
        assertThat(avecUneLigne(avecExterne, "INTERVENANT", "1", "10").lignes()).hasSize(1);
        assertThat(avecUneLigne(avecExterneAilleurs, "MAIN_OEUVRE", "1", "10").lignes()).hasSize(1);
        assertThat(avecUneLigne(avecInterne, "MAIN_OEUVRE", "1", "10").lignes()).hasSize(1);
    }

    @Test
    void should_refuse_a_line_on_a_cancelled_intervention_but_accept_one_on_a_finished_intervention_without_valuing_again() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue annulee = planifiee(equipement, null);
        annuler.executer(id(annulee), CENTRE, "doublon", ADMIN);
        InterventionVue terminee = terminee(equipement, unIntervenant(CENTRE, "INTERNE", "35.00"), Duration.ofHours(1));

        assertThatThrownBy(() -> avecUneLigne(annulee, "PIECE", "1", "1")).isInstanceOf(StatutIncompatible.class);
        assertThat(avecUneLigne(terminee, "PIECE", "1", "1").lignes()).hasSize(2);
    }

    @Test
    void should_refuse_an_unknown_line_type_and_an_unknown_intervention() {
        InterventionVue vue = planifiee(unEquipement(CENTRE, "G-01"), null);

        assertThatThrownBy(() -> avecUneLigne(vue, "DIVERS", "1", "1")).hasMessageEndingWith("TypeLigneCout.DIVERS");
        assertThatThrownBy(() -> ajouterLigne.executer(new AjouterLigneDeCoutCommande(new InterventionId(UUID.randomUUID()),
                CENTRE, "PIECE", "x", BigDecimal.ONE, BigDecimal.ONE, null, ADMIN))).isInstanceOf(InterventionIntrouvable.class);
    }

    @Test
    void should_remove_a_cost_line_from_an_open_intervention_only() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue vue = avecUneLigne(planifiee(equipement, null), "PIECE", "1", "5");
        LigneDeCoutId ligne = new LigneDeCoutId(vue.lignes().get(0).id());

        assertThat(retirerLigne.executer(id(vue), CENTRE, ligne, ADMIN).lignes()).isEmpty();
        assertThatThrownBy(() -> retirerLigne.executer(id(vue), CENTRE, ligne, ADMIN)).hasMessage("Ligne de coût non trouvée");

        InterventionVue terminee = terminee(equipement, unIntervenant(CENTRE, "INTERNE", "35"), Duration.ofHours(1));
        assertThatThrownBy(() -> retirerLigne.executer(id(terminee), CENTRE, new LigneDeCoutId(terminee.lignes().get(0).id()),
                ADMIN)).isInstanceOf(StatutIncompatible.class);
    }

    // ------------------------------------------------------------------ Rectifier (R15)

    @Test
    void should_reopen_a_finished_intervention_without_touching_the_equipment_and_value_again_at_the_new_closing() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        IntervenantId intervenant = unIntervenant(CENTRE, "INTERNE", "35.00");
        InterventionVue terminee = terminee(equipement, intervenant, Duration.ofHours(1));
        publicateur.publies.clear();
        int historique = parc.historiqueDe(equipement).size();

        InterventionVue rouverte = rectifier.executer(id(terminee), CENTRE, " erreur de date ", null, ADMIN);

        assertThat(rouverte.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(rouverte.nombreDeRectifications()).isEqualTo(1);
        assertThat(rouverte.lignes()).isEmpty();
        assertThat(rouverte.actions()).isEqualTo("Joint remplacé");
        assertThat(parc.historiqueDe(equipement)).hasSize(historique);
        assertThat(publicateur.publies).singleElement().isEqualTo(
                new InterventionRectifiee(id(terminee), equipement, CENTRE, "erreur de date", ADMIN, T0));

        corrigerIntervenant.executer(new CorrigerIntervenantCommande(intervenant, CENTRE, "Bernard", "INTERNE", null, null,
                new BigDecimal("40.00"), ADMIN));
        InterventionVue reclose = terminer.executer(new TerminerInterventionCommande(id(terminee), CENTRE, "a", "EN_SERVICE",
                DEBUT.plus(Duration.ofHours(2)), null, ADMIN));
        assertThat(reclose.coutTotal()).isEqualTo(new BigDecimal("80.0000"));
        assertThat(publicateur.publies).filteredOn(e -> e instanceof InterventionTerminee).singleElement()
                .satisfies(e -> assertThat(((InterventionTerminee) e).numeroDeCloture()).isEqualTo(2));
    }

    @Test
    void should_refuse_to_rectify_an_unfinished_intervention() {
        InterventionVue vue = enCours(unEquipement(CENTRE, "G-01"), null);

        assertThatThrownBy(() -> rectifier.executer(id(vue), CENTRE, "m", null, ADMIN)).isInstanceOf(StatutIncompatible.class);
        assertThatThrownBy(() -> rectifier.executer(id(vue), AUTRE_CENTRE, "m", null, ADMIN))
                .isInstanceOf(InterventionIntrouvable.class);
    }

    // ------------------------------------------------------------------ Annuler (R10)

    @Test
    void should_cancel_an_intervention_in_progress_and_leave_the_equipment_in_maintenance() {
        EquipementId equipement = unEquipement(CENTRE, "G-01");
        InterventionVue vue = enCours(equipement, null);
        publicateur.publies.clear();

        InterventionVue annulee = annuler.executer(id(vue), CENTRE, "plus nécessaire", ADMIN);

        assertThat(annulee.statut()).isEqualTo(StatutIntervention.ANNULEE);
        assertThat(annulee.observations()).isEqualTo("Annulée: plus nécessaire");
        assertThat(statutDe(equipement)).as("KB-GMAO-48 : l'équipement n'est pas remis en service")
                .isEqualTo(StatutEquipement.EN_MAINTENANCE);
        assertThat(publicateur.publies).singleElement().isInstanceOf(InterventionAnnulee.class);
    }

    @Test
    void should_accept_cancelling_twice_and_accumulate_the_reasons() {
        InterventionVue vue = planifiee(unEquipement(CENTRE, "G-01"), null);

        annuler.executer(id(vue), CENTRE, "a", ADMIN);
        InterventionVue deuxFois = annuler.executer(id(vue), CENTRE, "b", ADMIN);

        assertThat(deuxFois.observations()).isEqualTo("Annulée: a; Annulée: b");
    }

    @Test
    void should_refuse_to_cancel_a_finished_intervention() {
        InterventionVue terminee = terminee(unEquipement(CENTRE, "G-01"), null, Duration.ofHours(1));

        assertThatThrownBy(() -> annuler.executer(id(terminee), CENTRE, "x", ADMIN)).isInstanceOf(StatutIncompatible.class);
    }

    // ------------------------------------------------------------------ ConsulterIntervention / ListerInterventions

    @Test
    void should_consult_only_an_intervention_of_the_centre() {
        InterventionVue vue = planifiee(unEquipement(CENTRE, "G-01"), null);

        assertThat(consulter.executer(id(vue), CENTRE)).contains(vue);
        assertThat(consulter.executer(id(vue), AUTRE_CENTRE)).isEmpty();
        assertThat(consulter.executer(new InterventionId(UUID.randomUUID()), CENTRE)).isEmpty();
    }

    @Test
    void should_flag_a_planned_intervention_late_when_read_after_its_start() {
        InterventionVue vue = planifiee(unEquipement(CENTRE, "G-01"), null);
        horloge.avancerA(DEBUT.plusSeconds(1));

        assertThat(relue(vue).enRetard()).isTrue();
    }

    @Test
    void should_list_the_centre_by_status_ignoring_the_status_when_an_equipment_is_given() {
        EquipementId g1 = unEquipement(CENTRE, "G-01");
        EquipementId g2 = unEquipement(CENTRE, "G-02");
        planifiee(g1, null);
        horloge.avancerA(T0.plusSeconds(10));
        InterventionVue enCours = enCours(g2, null);
        unEquipement(AUTRE_CENTRE, "G-B");

        assertThat(lister.executer(CENTRE, null, null, 0, 20).items()).hasSize(2);
        assertThat(lister.executer(CENTRE, "EN_COURS", null, 0, 20).items()).extracting(InterventionVue::id)
                .containsExactly(enCours.id());
        assertThat(lister.executer(CENTRE, "en_cours", null, 0, 20).items()).isEmpty();
        assertThat(lister.executer(CENTRE, "", null, 0, 20).total()).isEqualTo(2);
        assertThat(lister.executer(CENTRE, "TERMINEE", g1, 0, 20).items()).hasSize(1);
        assertThat(lister.executer(CENTRE, null, null, 1, 1).items()).hasSize(1);
        assertThat(lister.executer(CENTRE, null, null, 5, 20).items()).isEmpty();
    }

    @Test
    void should_refuse_an_equipment_of_another_centre_and_the_bounds_the_legacy_refused() {
        EquipementId autreCentre = unEquipement(AUTRE_CENTRE, "G-B");

        assertThatThrownBy(() -> lister.executer(CENTRE, null, autreCentre, 0, 20)).isInstanceOf(EquipementIntrouvable.class);
        assertThatThrownBy(() -> lister.executer(CENTRE, null, null, -1, 20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page index must not be less than zero");
        assertThatThrownBy(() -> lister.executer(CENTRE, null, null, 0, 0)).hasMessage("Page size must not be less than one");
        assertThat(lister.executer(CENTRE, null, null, 0, 100000).size()).isEqualTo(100000);
    }
}
