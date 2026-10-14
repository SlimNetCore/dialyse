package com.hemodialyse.backend.contexts.gmao.application.pilotage;

import com.hemodialyse.backend.contexts.gmao.application.equipement.ConstaterEtat;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsulterLaFiche;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementIntrouvable;
import com.hemodialyse.backend.contexts.gmao.application.equipement.FicheDEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.VerifierLaDisponibilite;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenantCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterLaChronologie;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterLesIndicateurs;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImprimerLeBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionIntrouvable;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerInterventionCommande;
import com.hemodialyse.backend.contexts.gmao.doubles.CapaciteDesSallesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.HistoriqueEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.ImpressionEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.IntervenantsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.NomsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.ParcEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PilotageEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PlanMaintenanceEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.UniciteDuCodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.SyntheseGmaoDuCentre;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage de consultation et de pilotage (G6) sur adapters in-memory : fiche (RG-GMA-061, RG-GMA-062), disponibilité pour un
 * patient (RG-GMA-066), indicateurs et chronologie (RG-GMA-031), bon, statistiques et synthèse du centre. Défauts corrigés :
 * KB-GMAO-69 (intervention supprimée), KB-GMAO-72 (seuil sur le ratio exact), KB-GMAO-73 (équipement désactivé).
 */
class CasDUsagePilotageTest {

    private static final Instant T0 = Instant.parse("2026-10-12T09:00:00Z");
    private static final Instant DEBUT = Instant.parse("2026-10-12T10:00:00Z");
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final UtilisateurId TECHNICIEN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));

    private ParcEnMemoire parc;
    private InterventionsEnMemoire interventions;
    private IntervenantsEnMemoire intervenants;
    private PilotageEnMemoire pilotage;
    private NomsEnMemoire noms;
    private ImpressionEnMemoire impression;
    private HorlogeFixe horloge;
    private EnregistrerEquipement enregistrerEquipement;
    private EnregistrerIntervenant enregistrerIntervenant;
    private ConstaterEtat constaterEtat;
    private PlanifierIntervention planifier;
    private DemarrerIntervention demarrer;
    private TerminerIntervention terminer;
    private ConsulterLaFiche fiche;
    private VerifierLaDisponibilite disponibilite;
    private ConsulterLesIndicateurs indicateurs;
    private ConsulterLaChronologie chronologie;
    private ImprimerLeBon bon;
    private ConsulterLesStatistiques statistiques;
    private SynthetiserLeCentre synthese;
    private final AnalyseDeDecision analyse = new AnalyseDeDecision(new BigDecimal("0.60"));

    @BeforeEach
    void setUp() {
        parc = new ParcEnMemoire();
        interventions = new InterventionsEnMemoire();
        intervenants = new IntervenantsEnMemoire();
        pilotage = new PilotageEnMemoire(parc, interventions);
        noms = new NomsEnMemoire().avec(ADMIN, "Samia Admin");
        impression = new ImpressionEnMemoire();
        horloge = new HorlogeFixe(T0);
        PublicateurEnMemoire publicateur = new PublicateurEnMemoire();
        UniteDeTravailDirecte unite = new UniteDeTravailDirecte();
        IdentifiantsSequentiels identifiants = new IdentifiantsSequentiels();
        HistoriqueEnMemoire historique = new HistoriqueEnMemoire(parc);
        PlanMaintenanceEnMemoire plans = new PlanMaintenanceEnMemoire();

        enregistrerEquipement = new EnregistrerEquipement(parc, new UniciteDuCodeEquipement(parc),
                new CapaciteDesSallesEnMemoire(), publicateur, horloge, identifiants, unite);
        enregistrerIntervenant = new EnregistrerIntervenant(intervenants, publicateur, horloge, identifiants, unite);
        constaterEtat = new ConstaterEtat(parc, publicateur, horloge, unite);
        planifier = new PlanifierIntervention(interventions, parc, publicateur, horloge, identifiants, unite);
        demarrer = new DemarrerIntervention(interventions, constaterEtat, publicateur, horloge, unite);
        terminer = new TerminerIntervention(interventions, intervenants, constaterEtat, publicateur, horloge, identifiants, unite);

        fiche = new ConsulterLaFiche(parc, historique, pilotage, pilotage, interventions, plans, analyse, horloge);
        disponibilite = new VerifierLaDisponibilite(parc);
        indicateurs = new ConsulterLesIndicateurs(interventions, parc, historique, horloge);
        chronologie = new ConsulterLaChronologie(interventions, noms);
        bon = new ImprimerLeBon(interventions, parc, intervenants, chronologie, impression, horloge);
        statistiques = new ConsulterLesStatistiques(pilotage, horloge);
        synthese = new SynthetiserLeCentre(parc, historique, pilotage, pilotage, analyse);
    }

    // ------------------------------------------------------------------ fixtures

    private EquipementId unEquipement(CentreId centre, String code, String prix) {
        return new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(centre, code, "Générateur " + code,
                "GENERATEUR_DIALYSE", "Fresenius", "5008S", null, Instant.parse("2024-03-01T08:00:00Z"), null, null,
                prix == null ? null : new BigDecimal(prix), ADMIN)).id());
    }

    /** Pose le statut tel que la base le porterait (aucune commande ne fixe tous les statuts : KB-GMAO-06). */
    private void etat(EquipementId equipement, CentreId centre, String statut) {
        Equipement e = parc.trouverParId(equipement).orElseThrow();
        parc.enregistrer(Equipement.reconstituer(e.id(), e.centreId(), e.code(), e.designation(), e.type(), e.fabricant(),
                e.modele(), e.numeroSerie(), e.dateInstallation(), StatutEquipement.valueOf(statut), e.localisation(),
                e.observations(), e.salleId(), e.prixAcquisition(), e.creeLe(), e.modifieLe(), e.creePar(), e.modifiePar()));
    }

    private InterventionVue terminee(EquipementId equipement, IntervenantId intervenant) {
        return terminee(equipement, intervenant, DEBUT);
    }

    private InterventionVue terminee(EquipementId equipement, IntervenantId intervenant, Instant debut) {
        horloge.avancerA(debut.minusSeconds(3600));
        InterventionVue vue = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", debut, "Fuite",
                intervenant, "EN_MAINTENANCE", null, null, null, ADMIN));
        horloge.avancerA(debut);
        demarrer.executer(new InterventionId(vue.id()), CENTRE, TECHNICIEN);
        horloge.avancerA(debut.plus(Duration.ofHours(3)));
        return terminer.executer(new TerminerInterventionCommande(new InterventionId(vue.id()), CENTRE, "Joint remplacé",
                "EN_SERVICE", debut.plus(Duration.ofHours(2)), null, ADMIN));
    }

    /** Une ligne de coût d'une quantité de ce montant : le coût de maintenance de l'équipement en est la somme. */
    private InterventionVue avecCout(InterventionVue vue, String montant) {
        Intervention intervention = interventions.trouverParId(id(vue)).orElseThrow();
        intervention.ajouterLigneDeCout(LigneDeCout.saisir(new LigneDeCoutId(UUID.randomUUID()), TypeLigneCout.PIECE, "Pièce",
                BigDecimal.ONE, new BigDecimal(montant), UUID.randomUUID()), null, ADMIN, horloge.maintenant());
        interventions.enregistrer(intervention);
        return vue;
    }

    private static InterventionId id(InterventionVue vue) {
        return new InterventionId(vue.id());
    }

    // ------------------------------------------------------------------ fiche (RG-GMA-061, RG-GMA-062)

    @Test
    void should_refuse_the_fiche_of_an_unknown_equipment_or_one_of_another_centre() {
        EquipementId autre = unEquipement(AUTRE_CENTRE, "G-B", null);

        for (EquipementId equipement : new EquipementId[]{autre, new EquipementId(UUID.randomUUID())}) {
            assertThatThrownBy(() -> fiche.executer(equipement, CENTRE)).isInstanceOf(EquipementIntrouvable.class);
        }
    }

    @Test
    void should_compare_the_threshold_to_the_exact_ratio_not_to_the_rounded_one() {
        EquipementId g = unEquipement(CENTRE, "G-01", "1000.00");

        InterventionVue premiere = avecCout(terminee(g, null), "599.96");
        FicheDEquipement sous = fiche.executer(g, CENTRE);
        avecCout(premiere, "0.04");
        FicheDEquipement au = fiche.executer(g, CENTRE);

        assertThat(sous.analyse().ratioMaintenance()).isEqualByComparingTo("0.6000");
        assertThat(sous.analyse().reformeRecommandee()).as("KB-GMAO-72 corrigé : 0,59996 < 0,60").isFalse();
        assertThat(au.analyse().reformeRecommandee()).isTrue();
        assertThat(au.seuilDeReforme()).isEqualByComparingTo("0.60");
        assertThat(au.analyse().coutPossession()).isEqualByComparingTo("1600.00");
    }

    @Test
    void should_not_recommend_the_reform_of_a_reformed_equipment_and_know_no_ratio_without_price() {
        EquipementId reforme = unEquipement(CENTRE, "G-RF", "1000.00");
        avecCout(terminee(reforme, null), "5000");
        etat(reforme, CENTRE, "REFORME");
        EquipementId sansPrix = unEquipement(CENTRE, "G-NP", null);
        avecCout(terminee(sansPrix, null), "5000");

        assertThat(fiche.executer(reforme, CENTRE).analyse().reformeRecommandee()).isFalse();
        FicheDEquipement sans = fiche.executer(sansPrix, CENTRE);
        assertThat(sans.analyse().ratioMaintenance()).isNull();
        assertThat(sans.analyse().reformeRecommandee()).isFalse();
    }

    @Test
    void should_report_the_unavailability_of_the_last_twelve_months_and_the_last_intervention() {
        EquipementId g = unEquipement(CENTRE, "G-01", "10000.00");
        InterventionVue derniere = avecCout(terminee(g, null), "900");

        FicheDEquipement f = fiche.executer(g, CENTRE);

        assertThat(f.nombreDInterventions()).isEqualTo(1);
        assertThat(f.derniereIntervention().id()).isEqualTo(derniere.id());
        assertThat(f.indisponibiliteHeures()).isEqualTo(3.0);
        assertThat(f.analyse().coutParHeureIndisponibilite()).isEqualByComparingTo("300.00");
        assertThat(f.prochainPlan()).isNull();
    }

    @Test
    void should_serve_the_fiche_of_a_logically_deleted_equipment() {
        EquipementId g = unEquipement(CENTRE, "G-SUP", null);
        parc.supprimerLogiquement(g);

        assertThat(fiche.executer(g, CENTRE).equipement().code()).isEqualTo("G-SUP");
    }

    // ------------------------------------------------------------------ disponibilité pour un patient (RG-GMA-066)

    @Test
    void should_make_an_equipment_available_to_a_patient_only_when_in_service() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        assertThat(disponibilite.executer(g, CENTRE).disponible()).isTrue();

        for (String statut : List.of("EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE", "A_REFORMER", "REFORME")) {
            etat(g, CENTRE, statut);
            assertThat(disponibilite.executer(g, CENTRE).disponible()).as(statut).isFalse();
        }
    }

    @Test
    void should_not_make_a_deactivated_equipment_available() {
        EquipementId g = unEquipement(CENTRE, "G-DES", null);
        etat(g, CENTRE, "DESACTIF");

        assertThat(disponibilite.executer(g, CENTRE).disponible()).as("KB-GMAO-73 corrigé").isFalse();
        assertThat(disponibilite.executer(g, CENTRE).statut()).isEqualTo(StatutEquipement.DESACTIF);
    }

    @Test
    void should_refuse_the_availability_of_an_equipment_of_another_centre() {
        EquipementId autre = unEquipement(AUTRE_CENTRE, "G-B", null);

        assertThatThrownBy(() -> disponibilite.executer(autre, CENTRE)).isInstanceOf(EquipementIntrouvable.class);
    }

    // ------------------------------------------------------------------ indicateurs et chronologie (RG-GMA-031)

    @Test
    void should_compute_the_indicators_of_a_finished_intervention() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        InterventionVue vue = terminee(g, null);

        var resultat = indicateurs.executer(id(vue), CENTRE);

        assertThat(resultat.dureeMinutes()).isEqualTo(120);
        assertThat(resultat.indisponibiliteInterventionMinutes()).isEqualTo(120);
    }

    @Test
    void should_not_read_the_indicators_of_an_unknown_deleted_or_foreign_intervention() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        InterventionVue vue = terminee(g, null);

        assertThatThrownBy(() -> indicateurs.executer(new InterventionId(UUID.randomUUID()), CENTRE))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThatThrownBy(() -> indicateurs.executer(id(vue), AUTRE_CENTRE)).isInstanceOf(InterventionIntrouvable.class);
        interventions.supprimerLogiquement(id(vue));
        assertThatThrownBy(() -> indicateurs.executer(id(vue), CENTRE)).as("KB-GMAO-69 corrigé")
                .isInstanceOf(InterventionIntrouvable.class);
    }

    @Test
    void should_name_the_authors_of_the_chronology_and_leave_unknown_ones_unnamed() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        InterventionVue vue = terminee(g, null);

        List<EvenementVu> evenements = chronologie.executer(id(vue), CENTRE);

        assertThat(evenements).extracting(EvenementVu::type).startsWith(EvenementDeChronologie.Type.CREEE)
                .contains(EvenementDeChronologie.Type.DEMARREE, EvenementDeChronologie.Type.TERMINEE);
        assertThat(evenements.get(0).parNom()).isEqualTo("Samia Admin");
        assertThat(evenements).filteredOn(e -> e.type() == EvenementDeChronologie.Type.DEMARREE)
                .singleElement().satisfies(e -> {
                    assertThat(e.par()).isEqualTo(TECHNICIEN.valeur());
                    assertThat(e.parNom()).isNull();
                });
    }

    @Test
    void should_not_read_the_chronology_of_a_deleted_or_foreign_intervention() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        InterventionVue vue = terminee(g, null);

        assertThatThrownBy(() -> chronologie.executer(id(vue), AUTRE_CENTRE)).isInstanceOf(InterventionIntrouvable.class);
        interventions.supprimerLogiquement(id(vue));
        assertThatThrownBy(() -> chronologie.executer(id(vue), CENTRE)).isInstanceOf(InterventionIntrouvable.class);
    }

    // ------------------------------------------------------------------ bon d'intervention

    @Test
    void should_hand_the_data_of_the_bon_to_the_printing_port() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        IntervenantId technicien = new IntervenantId(enregistrerIntervenant.executer(new EnregistrerIntervenantCommande(CENTRE,
                "Karim", "EXTERNE", null, null, new BigDecimal("2000.00"), ADMIN)).id());
        InterventionVue vue = terminee(g, technicien);

        var document = bon.executer(id(vue), CENTRE, ZoneId.of("Africa/Algiers"), "admin");

        assertThat(document.format()).isEqualTo("PDF");
        assertThat(impression.centre).isEqualTo(CENTRE);
        assertThat(impression.zone).isEqualTo(ZoneId.of("Africa/Algiers"));
        assertThat(impression.editePar).isEqualTo("admin");
        assertThat(impression.donnees.intervention().id()).isEqualTo(vue.id());
        assertThat(impression.donnees.equipement().code()).isEqualTo("G-01");
        assertThat(impression.donnees.intervenant().nom()).isEqualTo("Karim");
        assertThat(impression.donnees.chronologie()).isNotEmpty();
        assertThat(impression.donnees.editeLe()).isEqualTo(horloge.maintenant());
    }

    @Test
    void should_omit_an_intervenant_of_another_centre_from_the_bon() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        IntervenantId etranger = new IntervenantId(enregistrerIntervenant.executer(new EnregistrerIntervenantCommande(AUTRE_CENTRE,
                "Etranger", "INTERNE", null, null, null, ADMIN)).id());
        InterventionVue vue = terminee(g, etranger);

        bon.executer(id(vue), CENTRE, ZoneId.of("UTC"), "admin");

        assertThat(impression.donnees.intervenant()).isNull();
    }

    @Test
    void should_not_print_the_bon_of_a_deleted_or_foreign_intervention_and_print_nothing() {
        EquipementId g = unEquipement(CENTRE, "G-01", null);
        InterventionVue vue = terminee(g, null);

        assertThatThrownBy(() -> bon.executer(id(vue), AUTRE_CENTRE, ZoneId.of("UTC"), "admin"))
                .isInstanceOf(InterventionIntrouvable.class);
        interventions.supprimerLogiquement(id(vue));
        assertThatThrownBy(() -> bon.executer(id(vue), CENTRE, ZoneId.of("UTC"), "admin"))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThat(impression.impressions).isZero();
    }

    @Test
    void should_still_print_the_bon_of_an_intervention_on_a_logically_deleted_equipment() {
        EquipementId g = unEquipement(CENTRE, "G-SUP", null);
        InterventionVue vue = terminee(g, null);
        parc.supprimerLogiquement(g);

        bon.executer(id(vue), CENTRE, ZoneId.of("UTC"), "admin");

        assertThat(impression.impressions).isEqualTo(1);
    }

    // ------------------------------------------------------------------ statistiques (R27, KB-GMAO-77)

    @Test
    void should_count_the_statistics_and_leave_the_other_statuses_only_in_the_total() {
        EquipementId g1 = unEquipement(CENTRE, "G-01", null);
        EquipementId g2 = unEquipement(CENTRE, "G-02", null);
        unEquipement(CENTRE, "G-03", null);
        unEquipement(AUTRE_CENTRE, "G-B", null);
        etat(g1, CENTRE, "HORS_SERVICE");
        etat(g2, CENTRE, "EN_ATTENTE_PIECE");
        terminee(unEquipement(CENTRE, "G-04", null), null);
        pilotage.plansActifs = 4;
        pilotage.plansEnRetard = 1;

        StatistiquesGmao s = statistiques.executer(CENTRE);

        assertThat(s.totalEquipements()).isEqualTo(4);
        assertThat(s.equipementsHorsService()).isEqualTo(1);
        assertThat(s.equipementsEnService()).isEqualTo(2);
        assertThat(s.equipementsEnMaintenance()).isZero();
        assertThat(s.totalInterventions()).isEqualTo(1);
        assertThat(s.interventionsTerminees()).isEqualTo(1);
        assertThat(s.plansMaintenanceActifs()).isEqualTo(4);
        assertThat(s.plansMaintenanceEnRetard()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ synthèse publiée (pilotage de la direction)

    @Test
    void should_publish_the_synthesis_of_a_centre_for_the_direction() {
        EquipementId g1 = unEquipement(CENTRE, "G-01", "1000.00");
        EquipementId g2 = unEquipement(CENTRE, "G-02", null);
        etat(g2, CENTRE, "HORS_SERVICE");
        unEquipement(AUTRE_CENTRE, "G-B", "1000.00");
        avecCout(terminee(g1, null, T0.minus(Duration.ofDays(60))), "350");
        avecCout(terminee(g1, null), "250");

        SyntheseGmaoDuCentre.Synthese s = synthese.duCentre(CENTRE.valeur(), T0.minus(Duration.ofDays(30)),
                T0.plus(Duration.ofDays(1)));

        assertThat(s.nombreDEquipements()).isEqualTo(2);
        assertThat(s.horsService()).isEqualTo(1);
        assertThat(s.coutDeMaintenance()).isEqualByComparingTo("250");
        assertThat(s.equipements()).hasSize(2);
        SyntheseGmaoDuCentre.Equipement e1 = s.equipements().stream().filter(e -> e.code().equals("G-01")).findFirst().orElseThrow();
        assertThat(e1.ratioMaintenance()).isEqualByComparingTo("0.6000");
        assertThat(e1.reformeRecommandee()).isTrue();
        assertThat(e1.coutDeLaPeriode()).isEqualByComparingTo("250");
        SyntheseGmaoDuCentre.Equipement e2 = s.equipements().stream().filter(e -> e.code().equals("G-02")).findFirst().orElseThrow();
        assertThat(e2.ratioMaintenance()).isNull();
        assertThat(e2.reformeRecommandee()).isFalse();
    }

    // ------------------------------------------------------------------ générateurs installés (capacité)

    @Test
    void should_count_the_installed_generators_without_the_reformed_and_deactivated_ones() {
        unEquipement(CENTRE, "G-01", null);
        EquipementId g2 = unEquipement(CENTRE, "G-02", null);
        EquipementId g3 = unEquipement(CENTRE, "G-03", null);
        unEquipement(AUTRE_CENTRE, "G-B", null);
        etat(g2, CENTRE, "REFORME");
        etat(g3, CENTRE, "DESACTIF");
        var comptage = new ComptageDesGenerateurs(pilotage);

        var parCentre = comptage.parCentre(List.of(CENTRE.valeur(), AUTRE_CENTRE.valeur(), UUID.randomUUID()),
                Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(parCentre).containsEntry(CENTRE.valeur(), 1L).containsEntry(AUTRE_CENTRE.valeur(), 1L).hasSize(2);
        assertThat(comptage.parCentre(List.of(CENTRE.valeur()), Instant.parse("2024-03-01T08:00:00Z"))).isEmpty();
        assertThat(comptage.parCentre(List.of(), Instant.now())).isEmpty();
    }
}
