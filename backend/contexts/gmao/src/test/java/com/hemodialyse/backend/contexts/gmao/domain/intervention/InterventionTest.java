package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterventionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private static final EquipementId EQUIPEMENT = new EquipementId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
    private static final InterventionId ID = new InterventionId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UtilisateurId PAR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final UtilisateurId AUTRE = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));
    private static final IntervenantId INTERVENANT = new IntervenantId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

    private static final Instant MAINTENANT = Instant.parse("2026-03-10T07:00:00Z");
    private static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-03-12T09:00:00Z");

    private static final ConditionsDeValorisation INTERNE_35 =
            new ConditionsDeValorisation(NatureIntervenant.INTERNE, new BigDecimal("35.00"));
    private static final ConditionsDeValorisation EXTERNE_120 =
            new ConditionsDeValorisation(NatureIntervenant.EXTERNE, new BigDecimal("120.50"));

    private static int sequence = 1;

    private static LigneDeCoutId nouvelleLigneId() {
        return new LigneDeCoutId(UUID.fromString(String.format("00000000-0000-0000-0000-%012d", 5000 + sequence++)));
    }

    // ------------------------------------------------------------------ fabriques de test

    private static Intervention planifiee() {
        return Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "Fuite sur le circuit",
                INTERVENANT, StatutEquipement.EN_MAINTENANCE, "Bruit anormal", PrioriteIntervention.HAUTE, null, PAR, MAINTENANT);
    }

    private static Intervention enCours() {
        Intervention intervention = planifiee();
        intervention.demarrer(PAR, MAINTENANT);
        intervention.tirerEvenements();
        return intervention;
    }

    private static Intervention terminee(Duration duree, ConditionsDeValorisation conditions) {
        Intervention intervention = enCours();
        intervention.terminer("Joint remplacé", StatutEquipement.EN_SERVICE, DEBUT.plus(duree), "Usure", conditions,
                nouvelleLigneId(), PAR, PLUS_TARD);
        intervention.tirerEvenements();
        return intervention;
    }

    private static Intervention terminee() {
        return terminee(Duration.ofMinutes(60), INTERNE_35);
    }

    private static Intervention enStatut(StatutIntervention statut) {
        return Intervention.reconstituer(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, statut, DEBUT,
                statut == StatutIntervention.TERMINEE ? PLUS_TARD : null, INTERVENANT, "Fuite", null, null, null,
                StatutEquipement.EN_MAINTENANCE, null, PrioriteIntervention.NORMALE, null, null, null,
                new Jalon(PAR, MAINTENANT), null, null, null, null, List.of(), List.of());
    }

    private static LigneDeCout manuelle(TypeLigneCout type, String quantite, String prix) {
        return LigneDeCout.saisir(nouvelleLigneId(), type, "Ligne", new BigDecimal(quantite), new BigDecimal(prix), null);
    }

    // ------------------------------------------------------------------ R11, R13 : planification

    @Test
    void should_plan_an_intervention_with_its_defaults() {
        Intervention intervention = Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.PREVENTIVE, DEBUT,
                "  Contrôle annuel ", null, StatutEquipement.EN_SERVICE, "   ", null, null, PAR, MAINTENANT);

        assertThat(intervention.statut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(intervention.priorite()).isEqualTo(PrioriteIntervention.NORMALE);
        assertThat(intervention.description()).isEqualTo("  Contrôle annuel ");
        assertThat(intervention.symptome()).isNull();
        assertThat(intervention.intervenantId()).isNull();
        assertThat(intervention.creation()).isEqualTo(new Jalon(PAR, MAINTENANT));
        assertThat(intervention.modification()).isNull();
        assertThat(intervention.lignes()).isEmpty();
        assertThat(intervention.tirerEvenements()).containsExactly(
                new InterventionPlanifiee(ID, EQUIPEMENT, CENTRE, TypeIntervention.PREVENTIVE, DEBUT, PAR, MAINTENANT));
    }

    @Test
    void should_trim_the_symptom() {
        Intervention intervention = Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "d", null,
                StatutEquipement.EN_SERVICE, "  Bruit  ", PrioriteIntervention.URGENTE, null, PAR, MAINTENANT);

        assertThat(intervention.symptome()).isEqualTo("Bruit");
        assertThat(intervention.priorite()).isEqualTo(PrioriteIntervention.URGENTE);
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_SERVICE", "EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE"})
    void should_accept_the_states_an_intervention_may_find_before_starting(StatutEquipement etat) {
        Intervention intervention = Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "d", null,
                etat, null, null, null, PAR, MAINTENANT);

        assertThat(intervention.etatAvant()).isEqualTo(etat);
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"A_REFORMER", "REFORME", "DESACTIF"})
    @NullSource
    void should_reject_planification_with_an_invalid_state_before(StatutEquipement etat) {
        assertThatThrownBy(() -> Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "d", null,
                etat, null, null, null, PAR, MAINTENANT))
                .isInstanceOf(ChampInvalide.class)
                .hasMessage("État de l'équipement au moment de l'intervention invalide");
    }

    @Test
    void should_reject_echeance_before_debut() {
        assertThatThrownBy(() -> Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "d", null,
                StatutEquipement.EN_SERVICE, null, null, DEBUT.minusSeconds(1), PAR, MAINTENANT))
                .isInstanceOf(ChampInvalide.class)
                .hasMessage("L'échéance doit être postérieure à la date de début");
    }

    @Test
    void should_accept_echeance_equal_to_debut() {
        Intervention intervention = Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "d", null,
                StatutEquipement.EN_SERVICE, null, null, DEBUT, PAR, MAINTENANT);

        assertThat(intervention.echeance()).isEqualTo(DEBUT);
    }

    @Test
    void should_check_the_echeance_before_the_state() {
        assertThatThrownBy(() -> Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "d", null,
                StatutEquipement.REFORME, null, null, DEBUT.minusSeconds(1), PAR, MAINTENANT))
                .hasMessage("L'échéance doit être postérieure à la date de début");
    }

    @Test
    void should_reject_a_planification_without_type_debut_or_description() {
        assertThatThrownBy(() -> Intervention.planifier(ID, EQUIPEMENT, CENTRE, null, DEBUT, "d", null,
                StatutEquipement.EN_SERVICE, null, null, null, PAR, MAINTENANT))
                .hasMessage("Type d'intervention requis");
        assertThatThrownBy(() -> Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, null, "d", null,
                StatutEquipement.EN_SERVICE, null, null, null, PAR, MAINTENANT))
                .hasMessage("Date de début requise");
        assertThatThrownBy(() -> Intervention.planifier(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, DEBUT, "  ", null,
                StatutEquipement.EN_SERVICE, null, null, null, PAR, MAINTENANT))
                .hasMessage("Description requise");
    }

    // ------------------------------------------------------------------ R10 : cycle de vie (table-driven)

    static Stream<org.junit.jupiter.params.provider.Arguments> commandes() {
        return Stream.of(
                // statut, démarrer, terminer, annuler, rectifier, ajouter ligne, retirer ligne
                row(StatutIntervention.PLANIFIEE, true, false, true, false, true, true),
                row(StatutIntervention.EN_COURS, false, true, true, false, true, true),
                row(StatutIntervention.TERMINEE, false, false, false, true, true, false),
                row(StatutIntervention.ANNULEE, false, false, true, false, false, false),
                row(StatutIntervention.EN_ATTENTE_VALIDATION, false, false, true, false, true, false));
    }

    private static org.junit.jupiter.params.provider.Arguments row(Object... valeurs) {
        return org.junit.jupiter.params.provider.Arguments.of(valeurs);
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("commandes")
    void should_allow_each_command_only_in_its_statuses(StatutIntervention statut, boolean demarrer, boolean terminer,
                                                        boolean annuler, boolean rectifier, boolean ajouter, boolean retirer) {
        assertThat(permis(() -> enStatut(statut).demarrer(PAR, PLUS_TARD))).as("démarrer").isEqualTo(demarrer);
        assertThat(permis(() -> enStatut(statut).terminer("a", StatutEquipement.EN_SERVICE, PLUS_TARD, null, null,
                nouvelleLigneId(), PAR, PLUS_TARD))).as("terminer").isEqualTo(terminer);
        assertThat(permis(() -> enStatut(statut).annuler("raison", PAR, PLUS_TARD))).as("annuler").isEqualTo(annuler);
        assertThat(permis(() -> enStatut(statut).rectifier("motif", null, UUID.randomUUID(), PAR, PLUS_TARD)))
                .as("rectifier").isEqualTo(rectifier);
        assertThat(permis(() -> enStatut(statut).ajouterLigneDeCout(manuelle(TypeLigneCout.PIECE, "1", "2"), null, PAR, PLUS_TARD)))
                .as("ajouter une ligne").isEqualTo(ajouter);
        Intervention avecLigne = enStatut(statut);
        LigneDeCout ligne = LigneDeCout.reconstituer(nouvelleLigneId(), TypeLigneCout.PIECE, "l", BigDecimal.ONE, BigDecimal.ONE,
                null, false);
        Intervention portant = Intervention.reconstituer(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, statut, DEBUT, null,
                null, "d", null, null, null, StatutEquipement.EN_SERVICE, null, PrioriteIntervention.NORMALE, null, null, null,
                new Jalon(PAR, MAINTENANT), null, null, null, null, List.of(ligne), List.of());
        assertThat(permis(() -> portant.retirerLigneDeCout(ligne.id(), PAR, PLUS_TARD))).as("retirer une ligne").isEqualTo(retirer);
        assertThat(avecLigne.statut()).isEqualTo(statut);
    }

    private static boolean permis(Runnable commande) {
        try {
            commande.run();
            return true;
        } catch (StatutIncompatible refus) {
            return false;
        }
    }

    @Test
    void should_use_the_legacy_messages_for_the_refused_transitions() {
        assertThatThrownBy(() -> enStatut(StatutIntervention.EN_COURS).demarrer(PAR, PLUS_TARD))
                .hasMessage("Seule une intervention planifiée peut être démarrée");
        assertThatThrownBy(() -> enStatut(StatutIntervention.PLANIFIEE).terminer("a", StatutEquipement.EN_SERVICE, PLUS_TARD,
                null, null, nouvelleLigneId(), PAR, PLUS_TARD)).hasMessage("Seule une intervention en cours peut être terminée");
        assertThatThrownBy(() -> enStatut(StatutIntervention.TERMINEE).annuler("x", PAR, PLUS_TARD))
                .hasMessage("Une intervention terminée ne peut pas être annulée");
        assertThatThrownBy(() -> enStatut(StatutIntervention.EN_COURS).rectifier("x", null, UUID.randomUUID(), PAR, PLUS_TARD))
                .hasMessage("Seule une intervention terminée peut être rectifiée");
        assertThatThrownBy(() -> enStatut(StatutIntervention.ANNULEE)
                .ajouterLigneDeCout(manuelle(TypeLigneCout.PIECE, "1", "1"), null, PAR, PLUS_TARD))
                .hasMessage("Une intervention annulée n'accepte plus de ligne de coût");
        assertThatThrownBy(() -> enStatut(StatutIntervention.TERMINEE).retirerLigneDeCout(nouvelleLigneId(), PAR, PLUS_TARD))
                .hasMessage("Les lignes de coût ne se corrigent que sur une intervention ouverte : rectifiez-la d'abord");
    }

    // ------------------------------------------------------------------ démarrage

    @Test
    void should_start_and_record_who_and_when() {
        Intervention intervention = planifiee();
        intervention.tirerEvenements();

        intervention.demarrer(AUTRE, PLUS_TARD);

        assertThat(intervention.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(intervention.demarrage()).isEqualTo(new Jalon(AUTRE, PLUS_TARD));
        assertThat(intervention.modification()).isEqualTo(new Jalon(AUTRE, PLUS_TARD));
        assertThat(intervention.debut()).isEqualTo(DEBUT);
        assertThat(intervention.tirerEvenements())
                .containsExactly(new InterventionDemarree(ID, EQUIPEMENT, CENTRE, AUTRE, PLUS_TARD));
    }

    // ------------------------------------------------------------------ R14 : clôture

    @Test
    void should_finish_an_intervention_in_progress() {
        Intervention intervention = enCours();

        intervention.terminer("Joint remplacé", StatutEquipement.A_REFORMER, DEBUT.plus(Duration.ofHours(2)), "  Usure ", null,
                nouvelleLigneId(), AUTRE, PLUS_TARD);

        assertThat(intervention.statut()).isEqualTo(StatutIntervention.TERMINEE);
        assertThat(intervention.actions()).isEqualTo("Joint remplacé");
        assertThat(intervention.cause()).isEqualTo("Usure");
        assertThat(intervention.etatApres()).isEqualTo(StatutEquipement.A_REFORMER);
        assertThat(intervention.fin()).isEqualTo(DEBUT.plus(Duration.ofHours(2)));
        assertThat(intervention.cloture()).isEqualTo(new Jalon(AUTRE, PLUS_TARD));
        assertThat(intervention.modification()).isEqualTo(new Jalon(AUTRE, PLUS_TARD));
        assertThat(intervention.lignes()).isEmpty();
    }

    @Test
    void should_keep_a_blank_cause_as_nothing() {
        Intervention intervention = enCours();

        intervention.terminer("a", StatutEquipement.EN_SERVICE, PLUS_TARD, "   ", null, nouvelleLigneId(), PAR, PLUS_TARD);

        assertThat(intervention.cause()).isNull();
    }

    @Test
    void should_reject_terminer_without_actions() {
        assertThatThrownBy(() -> enCours().terminer(" ", StatutEquipement.EN_SERVICE, PLUS_TARD, null, null,
                nouvelleLigneId(), PAR, PLUS_TARD)).isInstanceOf(ChampInvalide.class).hasMessage("Actions requises");
        assertThatThrownBy(() -> enCours().terminer(null, StatutEquipement.EN_SERVICE, PLUS_TARD, null, null,
                nouvelleLigneId(), PAR, PLUS_TARD)).hasMessage("Actions requises");
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_SERVICE", "EN_ATTENTE_PIECE", "HORS_SERVICE", "A_REFORMER"})
    void should_accept_the_states_an_intervention_may_leave_behind(StatutEquipement etat) {
        Intervention intervention = enCours();

        intervention.terminer("a", etat, PLUS_TARD, null, null, nouvelleLigneId(), PAR, PLUS_TARD);

        assertThat(intervention.etatApres()).isEqualTo(etat);
    }

    @ParameterizedTest
    @EnumSource(value = StatutEquipement.class, names = {"EN_MAINTENANCE", "REFORME", "DESACTIF"})
    @NullSource
    void should_reject_terminer_with_an_invalid_state_after(StatutEquipement etat) {
        assertThatThrownBy(() -> enCours().terminer("a", etat, PLUS_TARD, null, null, nouvelleLigneId(), PAR, PLUS_TARD))
                .isInstanceOf(ChampInvalide.class)
                .hasMessage("État de l'équipement après l'intervention invalide");
    }

    @Test
    void should_reject_a_missing_end() {
        assertThatThrownBy(() -> enCours().terminer("a", StatutEquipement.EN_SERVICE, null, null, null, nouvelleLigneId(), PAR,
                PLUS_TARD)).hasMessage("Date et heure de fin requises");
    }

    @Test
    void should_reject_an_end_before_the_start_and_accept_an_end_equal_to_it() {
        assertThatThrownBy(() -> enCours().terminer("a", StatutEquipement.EN_SERVICE, DEBUT.minusSeconds(1), null, null,
                nouvelleLigneId(), PAR, PLUS_TARD))
                .hasMessage("La date de fin doit être postérieure à la date de début");

        Intervention intervention = enCours();
        intervention.terminer("a", StatutEquipement.EN_SERVICE, DEBUT, null, INTERNE_35, nouvelleLigneId(), PAR, PLUS_TARD);
        assertThat(intervention.statut()).isEqualTo(StatutIntervention.TERMINEE);
        assertThat(intervention.lignes()).isEmpty();
    }

    @Test
    void should_leave_the_intervention_untouched_when_the_closing_is_refused() {
        Intervention intervention = enCours();

        assertThatThrownBy(() -> intervention.terminer("a", StatutEquipement.REFORME, PLUS_TARD, "cause", INTERNE_35,
                nouvelleLigneId(), PAR, PLUS_TARD)).isInstanceOf(ChampInvalide.class);

        assertThat(intervention.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(intervention.cause()).isNull();
        assertThat(intervention.lignes()).isEmpty();
    }

    @Test
    void should_announce_the_closing_with_the_cost_and_the_closing_number() {
        Intervention intervention = enCours();

        intervention.terminer("a", StatutEquipement.EN_SERVICE, DEBUT.plus(Duration.ofMinutes(60)), null, INTERNE_35,
                nouvelleLigneId(), AUTRE, PLUS_TARD);

        assertThat(intervention.tirerEvenements()).singleElement().isEqualTo(new InterventionTerminee(ID, EQUIPEMENT, CENTRE,
                TypeIntervention.CURATIVE, DEBUT, DEBUT.plus(Duration.ofMinutes(60)), new BigDecimal("35.0000"), 1, AUTRE,
                PLUS_TARD));
    }

    // ------------------------------------------------------------------ R19 : valorisation automatique du temps

    @ParameterizedTest(name = "{0} min -> {1} h -> {2}")
    @CsvSource({"1,0.02,0.7000", "20,0.33,11.5500", "55,0.92,32.2000", "60,1.00,35.0000", "90,1.50,52.5000",
            "100,1.67,58.4500", "6000,100.00,3500.0000"})
    void should_round_the_hours_half_up_to_two_decimals(long minutes, String heures, String montant) {
        Intervention intervention = terminee(Duration.ofMinutes(minutes), INTERNE_35);

        assertThat(intervention.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.quantite()).isEqualTo(new BigDecimal(heures));
            assertThat(ligne.montant()).isEqualTo(new BigDecimal(montant));
            assertThat(ligne.estAutomatique()).isTrue();
        });
    }

    @ParameterizedTest(name = "{0} s -> {1}")
    @CsvSource({"0,0", "59,0", "60,1", "119,1", "120,1", "3600,1"})
    void should_truncate_the_seconds_before_valuing_the_time(long secondes, int lignes) {
        Intervention intervention = terminee(Duration.ofSeconds(secondes), INTERNE_35);

        assertThat(intervention.lignes()).hasSize(lignes);
        if (secondes == 60 || secondes == 119) {
            assertThat(intervention.lignes().get(0).quantite()).isEqualTo(new BigDecimal("0.02"));
        }
    }

    @Test
    void should_value_an_internal_intervenant_as_labour() {
        Intervention intervention = terminee(Duration.ofMinutes(60), INTERNE_35);

        assertThat(intervention.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.type()).isEqualTo(TypeLigneCout.MAIN_OEUVRE);
            assertThat(ligne.libelle()).isEqualTo("Main d'œuvre");
            assertThat(ligne.prixUnitaire()).isEqualTo(new BigDecimal("35.00"));
            assertThat(ligne.articleStockId()).isNull();
        });
    }

    @Test
    void should_value_an_external_intervenant_as_fees() {
        Intervention intervention = terminee(Duration.ofMinutes(90), EXTERNE_120);

        assertThat(intervention.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.type()).isEqualTo(TypeLigneCout.INTERVENANT);
            assertThat(ligne.libelle()).isEqualTo("Honoraires intervenant");
            assertThat(ligne.montant()).isEqualTo(new BigDecimal("180.7500"));
        });
    }

    @Test
    void should_not_value_without_a_usable_rate() {
        assertThat(terminee(Duration.ofHours(1), null).lignes()).isEmpty();
        assertThat(terminee(Duration.ofHours(1), new ConditionsDeValorisation(NatureIntervenant.INTERNE, null)).lignes()).isEmpty();
        assertThat(terminee(Duration.ofHours(1), new ConditionsDeValorisation(NatureIntervenant.INTERNE,
                BigDecimal.ZERO)).lignes()).isEmpty();
        assertThat(terminee(Duration.ofHours(1), new ConditionsDeValorisation(NatureIntervenant.INTERNE,
                new BigDecimal("-5.00"))).lignes()).as("tarif négatif ancien en base").isEmpty();
        assertThat(terminee(Duration.ofHours(1), new ConditionsDeValorisation(null,
                BigDecimal.TEN)).lignes()).isEmpty();
    }

    @ParameterizedTest(name = "{0} + ligne manuelle {1} -> automatique ajoutée : {2}")
    @CsvSource({"INTERNE,MAIN_OEUVRE,false", "INTERNE,INTERVENANT,true", "INTERNE,PIECE,true", "INTERNE,AUTRE,true",
            "EXTERNE,INTERVENANT,false", "EXTERNE,MAIN_OEUVRE,true", "EXTERNE,PIECE,true"})
    void should_never_count_the_same_nature_twice(NatureIntervenant nature, TypeLigneCout manuelle, boolean ajoutee) {
        Intervention intervention = enCours();
        intervention.ajouterLigneDeCout(manuelle(manuelle, "1", "10"), null, PAR, MAINTENANT);

        intervention.terminer("a", StatutEquipement.EN_SERVICE, DEBUT.plus(Duration.ofHours(1)), null,
                new ConditionsDeValorisation(nature, new BigDecimal("50.00")), nouvelleLigneId(), PAR,
                PLUS_TARD);

        assertThat(intervention.lignes()).hasSize(ajoutee ? 2 : 1);
    }

    // ------------------------------------------------------------------ annulation

    @Test
    void should_cancel_and_accumulate_the_reasons_even_when_already_cancelled() {
        Intervention intervention = enCours();

        intervention.annuler("a", PAR, PLUS_TARD);
        intervention.annuler("b", AUTRE, PLUS_TARD.plusSeconds(5));

        assertThat(intervention.statut()).isEqualTo(StatutIntervention.ANNULEE);
        assertThat(intervention.observations()).isEqualTo("Annulée: a; Annulée: b");
        assertThat(intervention.annulation()).isEqualTo(new Jalon(AUTRE, PLUS_TARD.plusSeconds(5)));
        assertThat(intervention.modification()).isEqualTo(new Jalon(AUTRE, PLUS_TARD.plusSeconds(5)));
        assertThat(intervention.tirerEvenements()).hasSize(2).allSatisfy(e -> assertThat(e).isInstanceOf(InterventionAnnulee.class));
    }

    @Test
    void should_reject_a_blank_cancellation_reason() {
        assertThatThrownBy(() -> planifiee().annuler(" ", PAR, PLUS_TARD)).isInstanceOf(ChampInvalide.class)
                .hasMessage("Raison requise");
    }

    // ------------------------------------------------------------------ R15 : rectification

    @Test
    void should_reopen_a_finished_intervention_and_keep_the_closing_trace() {
        Intervention intervention = terminee(Duration.ofMinutes(60), INTERNE_35);
        intervention.ajouterLigneDeCout(manuelle(TypeLigneCout.PIECE, "2", "12.50"), null, PAR, PLUS_TARD);
        UUID rectificationId = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        Instant lundi = Instant.parse("2026-03-16T10:00:00Z");

        intervention.rectifier("  Mauvaise date de fin ", null, rectificationId, AUTRE, lundi);

        assertThat(intervention.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(intervention.fin()).isNull();
        assertThat(intervention.etatApres()).isNull();
        assertThat(intervention.cloture()).isNull();
        assertThat(intervention.actions()).isEqualTo("Joint remplacé");
        assertThat(intervention.cause()).isEqualTo("Usure");
        assertThat(intervention.modification()).isEqualTo(new Jalon(AUTRE, lundi));
        assertThat(intervention.lignes()).singleElement().satisfies(ligne -> assertThat(ligne.type()).isEqualTo(TypeLigneCout.PIECE));
        assertThat(intervention.rectifications()).containsExactly(
                new Rectification(rectificationId, "Mauvaise date de fin", AUTRE, lundi, PLUS_TARD, PAR));
        assertThat(intervention.tirerEvenements()).containsExactly(
                new InterventionRectifiee(ID, EQUIPEMENT, CENTRE, "Mauvaise date de fin", AUTRE, lundi));
    }

    @Test
    void should_apply_a_new_start_when_given() {
        Intervention intervention = terminee();
        Instant nouveau = DEBUT.minus(Duration.ofHours(3));

        intervention.rectifier("m", nouveau, UUID.randomUUID(), PAR, PLUS_TARD);

        assertThat(intervention.debut()).isEqualTo(nouveau);
    }

    @Test
    void should_keep_the_start_when_none_is_given() {
        Intervention intervention = terminee();

        intervention.rectifier("m", null, UUID.randomUUID(), PAR, PLUS_TARD);

        assertThat(intervention.debut()).isEqualTo(DEBUT);
    }

    @Test
    void should_reject_a_rectification_without_reason() {
        assertThatThrownBy(() -> terminee().rectifier("  ", null, UUID.randomUUID(), PAR, PLUS_TARD))
                .isInstanceOf(ChampInvalide.class).hasMessage("Motif de rectification requis");
        assertThatThrownBy(() -> terminee().rectifier(null, null, UUID.randomUUID(), PAR, PLUS_TARD))
                .hasMessage("Motif de rectification requis");
    }

    @Test
    void should_value_again_after_a_rectification_and_number_the_second_closing() {
        Intervention intervention = terminee(Duration.ofMinutes(60), INTERNE_35);
        intervention.rectifier("m", null, UUID.randomUUID(), PAR, PLUS_TARD);

        intervention.terminer("a", StatutEquipement.EN_SERVICE, DEBUT.plus(Duration.ofMinutes(120)), null,
                new ConditionsDeValorisation(NatureIntervenant.INTERNE, new BigDecimal("40.00")),
                nouvelleLigneId(), PAR, PLUS_TARD.plusSeconds(60));

        assertThat(intervention.lignes()).singleElement().satisfies(ligne ->
                assertThat(ligne.montant()).isEqualTo(new BigDecimal("80.0000")));
        assertThat(intervention.tirerEvenements()).filteredOn(e -> e instanceof InterventionTerminee)
                .singleElement().satisfies(e -> assertThat(((InterventionTerminee) e).numeroDeCloture()).isEqualTo(2));
    }

    // ------------------------------------------------------------------ R16, R17, R18 : lignes de coût

    @Test
    void should_add_and_remove_a_cost_line_while_the_intervention_is_open() {
        Intervention intervention = enCours();
        LigneDeCout ligne = manuelle(TypeLigneCout.PIECE, "2", "12.50");

        intervention.ajouterLigneDeCout(ligne, null, AUTRE, PLUS_TARD);
        assertThat(intervention.lignes()).containsExactly(ligne);
        assertThat(intervention.modification()).isEqualTo(new Jalon(AUTRE, PLUS_TARD));

        intervention.retirerLigneDeCout(ligne.id(), PAR, PLUS_TARD.plusSeconds(1));
        assertThat(intervention.lignes()).isEmpty();
        assertThat(intervention.modification()).isEqualTo(new Jalon(PAR, PLUS_TARD.plusSeconds(1)));
    }

    @Test
    void should_remove_only_the_requested_cost_line_and_keep_the_others() {
        Intervention intervention = enCours();
        LigneDeCout retiree = manuelle(TypeLigneCout.PIECE, "2", "12.50");
        LigneDeCout gardee = manuelle(TypeLigneCout.AUTRE, "1", "30");
        intervention.ajouterLigneDeCout(retiree, null, PAR, PLUS_TARD);
        intervention.ajouterLigneDeCout(gardee, null, PAR, PLUS_TARD);

        intervention.retirerLigneDeCout(retiree.id(), PAR, PLUS_TARD.plusSeconds(1));

        assertThat(intervention.lignes()).containsExactly(gardee);
    }

    @Test
    void should_reject_the_removal_of_an_unknown_line_without_touching_the_intervention() {
        Intervention intervention = enCours();
        Jalon avant = intervention.modification();

        assertThatThrownBy(() -> intervention.retirerLigneDeCout(nouvelleLigneId(), PAR, PLUS_TARD))
                .isInstanceOf(LigneDeCoutInconnue.class).hasMessage("Ligne de coût non trouvée");

        assertThat(intervention.modification()).isEqualTo(avant);
    }

    @Test
    void should_have_dropped_the_automatic_line_when_reopened() {
        Intervention intervention = terminee();
        LigneDeCout automatique = intervention.lignes().get(0);
        intervention.rectifier("m", null, UUID.randomUUID(), PAR, PLUS_TARD);
        // la rectification a déjà retiré la ligne automatique : elle n'existe plus
        assertThatThrownBy(() -> intervention.retirerLigneDeCout(automatique.id(), PAR, PLUS_TARD))
                .isInstanceOf(LigneDeCoutInconnue.class);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,1", "1,-0.01"})
    void should_reject_an_invalid_quantity_or_price(String quantite, String prix) {
        assertThatThrownBy(() -> manuelle(TypeLigneCout.PIECE, quantite, prix)).isInstanceOf(ChampInvalide.class);
    }

    @Test
    void should_reject_a_line_without_label_type_or_with_invalid_amounts() {
        assertThatThrownBy(() -> LigneDeCout.saisir(nouvelleLigneId(), TypeLigneCout.PIECE, " ", BigDecimal.ONE, BigDecimal.ONE, null))
                .hasMessage("Libellé requis");
        assertThatThrownBy(() -> LigneDeCout.saisir(nouvelleLigneId(), null, "l", BigDecimal.ONE, BigDecimal.ONE, null))
                .hasMessage("Type de ligne de coût requis");
        assertThatThrownBy(() -> manuelle(TypeLigneCout.PIECE, "0", "1")).hasMessage("Quantité invalide");
        assertThatThrownBy(() -> manuelle(TypeLigneCout.PIECE, "1", "-1")).hasMessage("Prix unitaire invalide");
    }

    @ParameterizedTest(name = "intervenant {0} + ligne {1} -> acceptée : {2}")
    @CsvSource({"EXTERNE,MAIN_OEUVRE,false", "EXTERNE,INTERVENANT,true", "EXTERNE,PIECE,true", "INTERNE,MAIN_OEUVRE,true",
            "INTERNE,INTERVENANT,true"})
    void should_refuse_manual_labour_only_for_an_external_intervenant(NatureIntervenant nature, TypeLigneCout type,
                                                                      boolean acceptee) {
        Intervention intervention = enCours();

        if (acceptee) {
            intervention.ajouterLigneDeCout(manuelle(type, "1", "10"), nature, PAR, PLUS_TARD);
            assertThat(intervention.lignes()).hasSize(1);
        } else {
            assertThatThrownBy(() -> intervention.ajouterLigneDeCout(manuelle(type, "1", "10"), nature, PAR, PLUS_TARD))
                    .isInstanceOf(MainOeuvreRefuseePourExterne.class)
                    .hasMessage("Intervenant externe : ses honoraires sont facturés automatiquement, pas de main d'œuvre à saisir");
            assertThat(intervention.lignes()).isEmpty();
        }
    }

    @Test
    void should_accept_manual_labour_when_the_intervenant_is_unknown() {
        Intervention intervention = enCours();

        intervention.ajouterLigneDeCout(manuelle(TypeLigneCout.MAIN_OEUVRE, "1", "10"), null, PAR, PLUS_TARD);

        assertThat(intervention.lignes()).hasSize(1);
    }

    @Test
    void should_accept_a_free_line() {
        assertThat(manuelle(TypeLigneCout.AUTRE, "1", "0").montant()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_compute_an_amount_without_rounding() {
        LigneDeCout ligne = manuelle(TypeLigneCout.PIECE, "3.333", "10.105");

        assertThat(ligne.montant()).isEqualTo(new BigDecimal("33.679965"));
    }

    @Test
    void should_total_the_lines_and_cost_zero_without_lines() {
        Intervention intervention = enCours();
        assertThat(intervention.coutTotal()).isEqualTo(BigDecimal.ZERO);
        assertThat(intervention.coutTotal().scale()).isZero();

        intervention.ajouterLigneDeCout(manuelle(TypeLigneCout.PIECE, "2", "12.50"), null, PAR, PLUS_TARD);
        intervention.ajouterLigneDeCout(manuelle(TypeLigneCout.AUTRE, "1.5", "10.00"), null, PAR, PLUS_TARD);

        assertThat(intervention.coutTotal()).isEqualTo(new BigDecimal("40.000"));
    }

    // ------------------------------------------------------------------ R27 : retards

    @ParameterizedTest(name = "{0} / échéance {1}")
    @CsvSource({
            "PLANIFIEE,passee,true,true", "PLANIFIEE,future,true,false", "PLANIFIEE,aucune,true,false",
            "EN_COURS,passee,false,true", "EN_COURS,future,false,false", "EN_COURS,aucune,false,false",
            "TERMINEE,passee,false,false", "ANNULEE,passee,false,false", "EN_ATTENTE_VALIDATION,passee,false,false"})
    void should_flag_late_and_overdue_interventions(StatutIntervention statut, String echeance, boolean enRetard,
                                                    boolean depassee) {
        Instant maintenant = Instant.parse("2026-04-01T00:00:00Z");
        Instant dateEcheance = switch (echeance) {
            case "passee" -> Instant.parse("2026-03-20T00:00:00Z");
            case "future" -> Instant.parse("2026-05-01T00:00:00Z");
            default -> null;
        };
        Intervention intervention = Intervention.reconstituer(ID, EQUIPEMENT, CENTRE, TypeIntervention.CURATIVE, statut, DEBUT,
                null, null, "d", null, null, null, StatutEquipement.EN_SERVICE, null, PrioriteIntervention.NORMALE,
                dateEcheance, null, null, new Jalon(PAR, MAINTENANT), null, null, null, null, List.of(), List.of());

        assertThat(intervention.enRetard(maintenant)).isEqualTo(enRetard);
        assertThat(intervention.echeanceDepassee(maintenant)).isEqualTo(depassee);
    }

    @Test
    void should_not_flag_a_planned_intervention_whose_start_is_still_ahead() {
        assertThat(planifiee().enRetard(DEBUT.minusSeconds(1))).isFalse();
        assertThat(planifiee().enRetard(DEBUT)).isFalse();
        assertThat(planifiee().enRetard(DEBUT.plusSeconds(1))).isTrue();
    }

    @Test
    void should_hand_over_the_events_only_once() {
        Intervention intervention = planifiee();

        assertThat(intervention.tirerEvenements()).hasSize(1);
        assertThat(intervention.tirerEvenements()).isEmpty();
    }
}
