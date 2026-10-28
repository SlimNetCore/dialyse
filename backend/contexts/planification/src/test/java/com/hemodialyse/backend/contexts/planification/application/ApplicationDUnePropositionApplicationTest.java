package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.doubles.MondeDOptimisation;
import com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Affectation;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.HORS_SERVICE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P22 / P25 : application d'une proposition, tout ou rien en UNE transaction, verrous centre puis fiches par identifiant, empreinte
 * relue sous verrou, écritures par les agrégats (places, roulement, remplacements avec remplacé, déplacements temporaires),
 * notifications des infirmiers concernés. L'optimiseur est faux : les résultats sont posés par le test.
 */
class ApplicationDUnePropositionApplicationTest {

    private static final PatientId P1 = patient(1);
    private static final PatientId P2 = patient(2);
    private static final Set<JourDeLaSemaine> MJ = EnumSet.of(JourDeLaSemaine.MARDI, JourDeLaSemaine.JEUDI);

    private final MondeDOptimisation m = new MondeDOptimisation();
    private final OptimisationEnMemoire opt = m.optimisation;
    private JeuDOptimisation jeu;

    @BeforeEach
    void centre() {
        jeu = new JeuDOptimisation()
                .patient(1, LMV, false, poste(A, MATIN, A1))
                .patient(2, LMV, false, poste(A, MATIN, A2))
                .infirmierActif(1, false).infirmierActif(2, true).infirmierActif(3, false);
        m.places.patient(P1, "Patient 1", occupation(P1, A, MATIN, A1));
        m.places.patient(P2, "Patient 2", occupation(P2, A, MATIN, A2));
        poser();
    }

    private void poser() {
        opt.donneesDuCentre = jeu.donnees();
    }

    private static Occupation occupation(PatientId p, com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId salle,
                                         com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId creneau,
                                         GenerateurId generateur) {
        return new Occupation(p, salle, creneau, generateur, LMV, null);
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (RegleDePlanificationViolee e) {
            return e.code();
        }
        return null;
    }

    private ExecutionDOptimisation calculee(Perimetre perimetre, ResultatDOptimisation resultat) {
        ExecutionDOptimisation e = m.lancer.executer(CENTRE,
                new com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation(perimetre, LUNDI, 1, 20, 5, null, 2, 6),
                "admin");
        opt.derniereDemande().ecouteur().termine(resultat, "0hard/0soft");
        opt.notifications.clear();
        m.places.verrous.clear();
        return opt.executions.get(e.id());
    }

    private static ResultatDOptimisation deplacements(DeplacementDePatient... d) {
        return new ResultatDOptimisation(List.of(), List.of(), List.of(d), List.of(), List.of(), List.of(), null, null);
    }

    private static ResultatDOptimisation vacations(VacationPlanifiee... v) {
        return new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(v), List.of(), null, null);
    }

    private static VacationPlanifiee vacation(LocalDate date, JourDeLaSemaine jour, com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId salle,
                                              com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId creneau, int infirmier,
                                              boolean existante) {
        return new VacationPlanifiee(date, jour, salle, creneau, infirmier(infirmier), "N" + infirmier, existante);
    }

    private Occupation fiche(PatientId p) {
        return m.places.fiches.get(p);
    }

    // ───────────────────────────── refus communs ─────────────────────────────

    @Test
    void should_report_an_unknown_execution_as_not_found() {
        assertThat(code(() -> m.appliquer.executer(CENTRE, new ExecutionId(UUID.randomUUID())))).isEqualTo("OPTIMISATION_INTROUVABLE");
    }

    @Test
    void should_refuse_a_calculation_in_progress_or_failed_as_not_applicable() {
        ExecutionDOptimisation enCours = m.lancer.executer(CENTRE, JeuDOptimisation.parametres(Perimetre.PATIENTS), "admin");
        assertThat(code(() -> m.appliquer.executer(CENTRE, enCours.id()))).isEqualTo("OPTIMISATION_NON_APPLICABLE");
        opt.derniereDemande().ecouteur().echec("x");
        assertThat(code(() -> m.appliquer.executer(CENTRE, enCours.id()))).isEqualTo("OPTIMISATION_NON_APPLICABLE");
    }

    @Test
    void should_refuse_a_foreign_centre_as_not_found() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements());
        assertThat(code(() -> m.appliquer.executer(new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(
                UUID.randomUUID()), e.id()))).isEqualTo("OPTIMISATION_INTROUVABLE");
    }

    @Test
    void should_apply_once_then_refuse_a_second_application() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements());
        ExecutionDOptimisation appliquee = m.appliquer.executer(CENTRE, e.id());

        assertThat(appliquee.appliqueeLe()).isEqualTo(MondeDOptimisation.MAINTENANT);
        assertThat(opt.executions.get(e.id()).appliqueeLe()).isNotNull();
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_DEJA_APPLIQUEE");
    }

    @Test
    void should_take_the_centre_lock_first_and_refuse_when_another_write_holds_it() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements());
        m.appliquer.executer(CENTRE, e.id());
        assertThat(m.places.verrous).containsExactly(CENTRE);

        ExecutionDOptimisation autre = calculee(Perimetre.PATIENTS, deplacements());
        m.places.verrouRefuse = true;
        assertThatThrownBy(() -> m.appliquer.executer(CENTRE, autre.id())).isInstanceOf(EcritureConcurrente.class);
        assertThat(opt.executions.get(autre.id()).appliqueeLe()).isNull();
    }

    @Test
    void should_refuse_an_outdated_proposition_without_writing_anything() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements(
                new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(B, MATIN, B1))));
        jeu.patient(3, LMV, false, null);
        poser();

        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_PERIMEE");

        assertThat(fiche(P1).generateur()).isEqualTo(A1);
        assertThat(opt.executions.get(e.id()).appliqueeLe()).isNull();
        assertThat(opt.notifications).isEmpty();
        assertThat(m.caches).isEmpty();
    }

    @Test
    void should_judge_the_fingerprint_on_the_data_read_under_the_lock_and_not_on_the_launch() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements());
        int lecturesAvant = opt.lectures;
        m.appliquer.executer(CENTRE, e.id());
        assertThat(opt.lectures).isGreaterThan(lecturesAvant);
    }

    // ───────────────────────────── places ─────────────────────────────

    @Test
    void should_move_the_patients_write_the_chosen_days_with_the_place_notify_and_evict_the_caches() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements(
                new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(B, SOIR, B1), List.of(JourDeLaSemaine.MARDI,
                        JourDeLaSemaine.JEUDI))));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(fiche(P1).salle()).isEqualTo(B);
        assertThat(fiche(P1).creneau()).isEqualTo(SOIR);
        assertThat(fiche(P1).generateur()).isEqualTo(B1);
        assertThat(fiche(P1).jours()).isEqualTo(MJ);
        assertThat(fiche(P2).generateur()).isEqualTo(A2);
        assertThat(opt.notifications).containsExactly("SEANCES_DEPLACEES|PATIENTS|1|0");
        assertThat(m.caches).containsExactly(CENTRE.valeur() + "|" + P1.valeur());
    }

    @Test
    void should_accept_two_patients_swapping_their_generators() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements(
                new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(A, MATIN, A2)),
                new DeplacementDePatient(P2, "Patient 2", poste(A, MATIN, A2), poste(A, MATIN, A1))));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(fiche(P1).generateur()).isEqualTo(A2);
        assertThat(fiche(P2).generateur()).isEqualTo(A1);
        assertThat(opt.notifications).containsExactly("SEANCES_DEPLACEES|PATIENTS|2|0");
    }

    @Test
    void should_write_nothing_when_one_new_place_is_inconsistent() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements(
                new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(B, MATIN, B1)),
                new DeplacementDePatient(P2, "Patient 2", poste(A, MATIN, A2), new Poste(A, MATIN, new GenerateurId(UUID.randomUUID()),
                        "?"))));

        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_PLACEMENT_GENERATEUR_INCONNU");

        assertThat(fiche(P1).generateur()).isEqualTo(A1);
        assertThat(fiche(P2).generateur()).isEqualTo(A2);
        assertThat(opt.executions.get(e.id()).appliqueeLe()).isNull();
        assertThat(opt.notifications).isEmpty();
        assertThat(m.caches).isEmpty();
    }

    @Test
    void should_name_the_broken_rule_of_a_place_taken_by_a_patient_who_stays() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements(
                new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(A, MATIN, A2))));
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_PLACEMENT_GENERATEUR_OCCUPE");
    }

    @Test
    void should_refuse_a_move_for_a_patient_unknown_to_the_data_or_without_days() {
        ExecutionDOptimisation inconnu = calculee(Perimetre.PATIENTS, deplacements(
                new DeplacementDePatient(patient(77), "?", null, poste(B, MATIN, B1))));
        assertThat(code(() -> m.appliquer.executer(CENTRE, inconnu.id()))).isEqualTo("OPTIMISATION_PLACEMENT_INCOMPLET");
    }

    @Test
    void should_not_touch_the_places_for_a_staff_perimeter() {
        ExecutionDOptimisation e = calculee(Perimetre.ROULEMENT, deplacements(
                new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(B, MATIN, B1))));
        m.appliquer.executer(CENTRE, e.id());
        assertThat(fiche(P1).generateur()).isEqualTo(A1);
    }

    // ───────────────────────────── roulement ─────────────────────────────

    private void roulementActuel(int infirmier, com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId salle,
                                 com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId creneau, Set<JourDeLaSemaine> jours,
                                 long id) {
        m.planning.roulements.ajouter(CENTRE, infirmier(infirmier), new Affectation(new AffectationId(new UUID(1, id)), salle, creneau, jours));
        jeu.affecte(infirmier, salle, creneau, jours);
        poser();
    }

    @Test
    void should_replace_the_roulement_keeping_identical_assignments_and_notifying_only_the_nurses_who_change() {
        roulementActuel(1, A, MATIN, LMV, 1);
        roulementActuel(2, A, MATIN, LMV, 2);
        ExecutionDOptimisation e = calculee(Perimetre.ROULEMENT, vacations(
                vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 1, true), vacation(LUNDI.plusDays(2), JourDeLaSemaine.MERCREDI, A, MATIN, 1, true),
                vacation(LUNDI.plusDays(4), JourDeLaSemaine.VENDREDI, A, MATIN, 1, true),
                vacation(LUNDI.plusDays(1), JourDeLaSemaine.MARDI, B, SOIR, 3, false),
                vacation(LUNDI.plusDays(3), JourDeLaSemaine.JEUDI, B, SOIR, 3, false)));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(m.planning.roulements.de(CENTRE, infirmier(1)).affectations()).singleElement()
                .satisfies(a -> assertThat(a.id()).isEqualTo(new AffectationId(new UUID(1, 1))));
        assertThat(m.planning.roulements.de(CENTRE, infirmier(2)).affectations()).isEmpty();
        assertThat(m.planning.roulements.de(CENTRE, infirmier(3)).affectations()).singleElement().satisfies(a -> {
            assertThat(a.salle()).isEqualTo(B);
            assertThat(a.creneau()).isEqualTo(SOIR);
            assertThat(a.jours()).isEqualTo(MJ);
        });
        assertThat(opt.notifications).containsExactly(
                "ROULEMENT|" + e.id().valeur() + "|" + infirmier(2).valeur(),
                "ROULEMENT|" + e.id().valeur() + "|" + infirmier(3).valeur());
    }

    @Test
    void should_lock_the_nurse_files_in_increasing_identifier_order_after_the_centre() {
        ExecutionDOptimisation e = calculee(Perimetre.ROULEMENT, vacations(vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 3, false)));
        m.planning.monde.fichesVerrouillees.clear();

        m.appliquer.executer(CENTRE, e.id());

        assertThat(m.places.verrous).containsExactly(CENTRE);
        assertThat(m.planning.monde.fichesVerrouillees).containsExactly(infirmier(1), infirmier(2), infirmier(3));
    }

    @Test
    void should_judge_the_wanted_roulement_and_refuse_a_nurse_in_two_rooms_at_the_same_slot() {
        ExecutionDOptimisation e = calculee(Perimetre.ROULEMENT, vacations(
                vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 1, false), vacation(LUNDI, JourDeLaSemaine.LUNDI, B, MATIN, 1, false)));

        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("AFFECTATION_CHEVAUCHEMENT");

        assertThat(opt.executions.get(e.id()).appliqueeLe()).isNull();
        assertThat(m.planning.roulements.de(CENTRE, infirmier(1)).affectations()).isEmpty();
        assertThat(opt.notifications).isEmpty();
    }

    @Test
    void should_restore_the_places_when_the_roulement_of_a_complete_calculation_is_refused() {
        ExecutionDOptimisation e = calculee(Perimetre.COMPLET, new ResultatDOptimisation(List.of(), List.of(),
                List.of(new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(B, SOIR, B1))), List.of(),
                List.of(vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 1, false), vacation(LUNDI, JourDeLaSemaine.LUNDI, B, MATIN, 1, false)),
                List.of(), null, null));

        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("AFFECTATION_CHEVAUCHEMENT");

        assertThat(fiche(P1).generateur()).isEqualTo(A1);
        assertThat(opt.executions.get(e.id()).appliqueeLe()).isNull();
    }

    @Test
    void should_apply_places_and_roulement_together_for_a_complete_calculation() {
        ExecutionDOptimisation e = calculee(Perimetre.COMPLET, new ResultatDOptimisation(List.of(), List.of(),
                List.of(new DeplacementDePatient(P1, "Patient 1", poste(A, MATIN, A1), poste(B, SOIR, B1))), List.of(),
                List.of(vacation(LUNDI, JourDeLaSemaine.LUNDI, B, SOIR, 1, false)), List.of(), null, null));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(fiche(P1).salle()).isEqualTo(B);
        assertThat(m.planning.roulements.de(CENTRE, infirmier(1)).affectations()).singleElement()
                .satisfies(a -> assertThat(a.jours()).containsExactly(JourDeLaSemaine.LUNDI));
        assertThat(opt.notifications).contains("SEANCES_DEPLACEES|COMPLET|1|0", "ROULEMENT|" + e.id().valeur() + "|" + infirmier(1).valeur());
    }

    // ───────────────────────────── couverture ─────────────────────────────

    @Test
    void should_create_the_replacements_link_the_replaced_nurse_and_notify_the_replacement() {
        roulementActuel(1, A, MATIN, LMV, 1);
        jeu.absent(1, LUNDI, LUNDI.plusDays(1));
        poser();
        ExecutionDOptimisation e = calculee(Perimetre.COUVERTURE, vacations(
                vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 2, false),
                vacation(LUNDI.plusDays(2), JourDeLaSemaine.MERCREDI, A, MATIN, 3, true)));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(m.remplacementsPoses).singleElement().satisfies(r -> {
            assertThat(r.remplacant()).isEqualTo(infirmier(2));
            assertThat(r.remplace()).contains(infirmier(1));
            assertThat(r.date()).isEqualTo(LUNDI);
            assertThat(r.salle()).isEqualTo(A);
            assertThat(r.creneau()).isEqualTo(MATIN);
        });
        assertThat(opt.notifications).containsExactly("REMPLACEMENT|" + e.id().valeur() + "|" + infirmier(2).valeur() + "|" + LUNDI);
        assertThat(m.planning.monde.fichesVerrouillees).containsExactly(infirmier(2));
    }

    @Test
    void should_leave_the_replaced_empty_when_nobody_is_absent_on_the_case() {
        ExecutionDOptimisation e = calculee(Perimetre.COUVERTURE, vacations(vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 2, false)));
        m.appliquer.executer(CENTRE, e.id());
        assertThat(m.remplacementsPoses).singleElement().satisfies(r -> assertThat(r.remplace()).isEmpty());
    }

    @Test
    void should_replace_two_absent_nurses_of_the_same_case_one_each_in_identifier_order() {
        roulementActuel(1, A, MATIN, LMV, 1);
        roulementActuel(2, A, MATIN, LMV, 2);
        jeu.absent(1, LUNDI, LUNDI).absent(2, LUNDI, LUNDI);
        poser();
        ExecutionDOptimisation e = calculee(Perimetre.COUVERTURE, vacations(
                vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 3, false)));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(m.remplacementsPoses).singleElement().satisfies(r -> assertThat(r.remplace()).contains(infirmier(1)));
    }

    @Test
    void should_refuse_a_replacement_nurse_who_is_absent_or_already_on_the_slot() {
        jeu.absent(3, LUNDI, LUNDI);
        poser();
        ExecutionDOptimisation absent = calculee(Perimetre.COUVERTURE, vacations(vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 3, false)));
        assertThat(code(() -> m.appliquer.executer(CENTRE, absent.id()))).isEqualTo("REMPLACEMENT_INELIGIBLE");
        assertThat(m.remplacementsPoses).isEmpty();
        assertThat(opt.executions.get(absent.id()).appliqueeLe()).isNull();
    }

    @Test
    void should_refuse_a_non_qualified_nurse_in_an_isolation_room() {
        ExecutionDOptimisation e = calculee(Perimetre.COUVERTURE, vacations(
                vacation(LUNDI, JourDeLaSemaine.LUNDI, JeuDePlaces.ISOLEMENT, MATIN, 1, false)));
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("REMPLACEMENT_INELIGIBLE");
    }

    @Test
    void should_refuse_a_replacement_nurse_unknown_or_inactive_in_the_centre() {
        ExecutionDOptimisation e = calculee(Perimetre.COUVERTURE, vacations(vacation(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 9, false)));
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("REMPLACEMENT_INELIGIBLE");
    }

    // ───────────────────────────── maintenance ─────────────────────────────

    private static DeplacementTemporairePropose temporaire(PatientId p, LocalDate date, Poste vers, String motif) {
        return new DeplacementTemporairePropose(p, "?", date, JourDeLaSemaine.LUNDI, poste(A, MATIN, A1), vers, motif);
    }

    private static ResultatDOptimisation temporaires(DeplacementTemporairePropose... t) {
        return new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of(t), List.of());
    }

    @Test
    void should_record_the_temporary_moves_with_their_reason_without_touching_the_usual_place() {
        ExecutionDOptimisation e = calculee(Perimetre.MAINTENANCE, temporaires(
                temporaire(P1, LUNDI, poste(B, MATIN, B1), "PREVENTIVE")));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(opt.temporaires).singleElement().satisfies(t -> {
            assertThat(t.patient()).isEqualTo(P1);
            assertThat(t.date()).isEqualTo(LUNDI);
            assertThat(t.generateur()).isEqualTo(B1);
            assertThat(t.motif()).contains("PREVENTIVE");
        });
        assertThat(fiche(P1).generateur()).isEqualTo(A1);
        assertThat(opt.notifications).containsExactly("SEANCES_DEPLACEES|MAINTENANCE|0|1");
    }

    @Test
    void should_keep_one_move_per_patient_and_date_the_last_one_replacing_the_others() {
        ExecutionDOptimisation e = calculee(Perimetre.MAINTENANCE, temporaires(
                temporaire(P1, LUNDI, poste(B, MATIN, B1), "A"), temporaire(P1, LUNDI, poste(A, SOIR, A2), "B")));

        m.appliquer.executer(CENTRE, e.id());

        assertThat(opt.temporaires).singleElement().satisfies(t -> assertThat(t.motif()).contains("B"));
    }

    @Test
    void should_make_a_proposition_outdated_once_a_move_was_recorded_by_another_application() {
        ExecutionDOptimisation premiere = calculee(Perimetre.MAINTENANCE, temporaires(temporaire(P1, LUNDI, poste(B, MATIN, B1), "1")));
        ExecutionDOptimisation seconde = calculee(Perimetre.MAINTENANCE, temporaires(temporaire(P1, LUNDI, poste(A, SOIR, A2), "2")));
        m.appliquer.executer(CENTRE, premiere.id());
        // l'état du centre inclut maintenant le déplacement posé : la seconde proposition, calculée avant, est périmée
        jeu.temporaires.addAll(opt.temporaires);
        poser();

        assertThat(code(() -> m.appliquer.executer(CENTRE, seconde.id()))).isEqualTo("OPTIMISATION_PERIMEE");
    }

    @Test
    void should_refuse_an_inconsistent_target_in_422_and_record_nothing() {
        ExecutionDOptimisation e = calculee(Perimetre.MAINTENANCE, temporaires(
                temporaire(P1, LUNDI, poste(B, MATIN, B1), "ok"), temporaire(P2, LUNDI, poste(A, MATIN, B1), "mauvais")));
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE");
        assertThat(opt.temporaires).isEmpty();
        assertThat(opt.executions.get(e.id()).appliqueeLe()).isNull();
        assertThat(opt.notifications).isEmpty();
    }

    @Test
    void should_refuse_a_target_on_a_generator_out_of_service_or_without_place() {
        ExecutionDOptimisation horsService = calculee(Perimetre.MAINTENANCE, temporaires(
                temporaire(P1, LUNDI, poste(A, MATIN, HORS_SERVICE), "x")));
        assertThat(code(() -> m.appliquer.executer(CENTRE, horsService.id()))).isEqualTo("DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE");
        ExecutionDOptimisation sansPlace = calculee(Perimetre.MAINTENANCE, temporaires(temporaire(P1, LUNDI, null, "x")));
        assertThat(code(() -> m.appliquer.executer(CENTRE, sansPlace.id()))).isEqualTo("DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE");
    }

    @Test
    void should_notify_nobody_when_a_proposition_changes_nothing() {
        ExecutionDOptimisation e = calculee(Perimetre.MAINTENANCE, temporaires());
        m.appliquer.executer(CENTRE, e.id());
        assertThat(opt.notifications).isEmpty();
        assertThat(opt.temporaires).isEmpty();
    }

    @Test
    void should_not_apply_a_proposition_calculated_before_a_session_was_recorded_in_the_horizon() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements());
        jeu.seances.add(new com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee(P1, "Patient 1", LUNDI, A,
                MATIN, A1, false));
        poser();
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_PERIMEE");
    }

    @Test
    void should_not_apply_a_proposition_calculated_before_a_preference_changed() {
        ExecutionDOptimisation e = calculee(Perimetre.PATIENTS, deplacements());
        jeu.preferences.add(new com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification(P1, SOIR, 3, true));
        poser();
        assertThat(code(() -> m.appliquer.executer(CENTRE, e.id()))).isEqualTo("OPTIMISATION_PERIMEE");
    }
}
