package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.JourDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.SituationInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.PatientNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.SeanceSansSolution;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P23 (motifs de signalement d'une proposition de nuit) et P24 (calendrier figé de la proposition).
 */
class MotifEtCalendrierDeLaPropositionTest {

    private static Indicateurs indicateurs(int requises, int nonPlaces) {
        return new Indicateurs(0, 0, requises, 0, nonPlaces, 0, 0, 0, 0);
    }

    // ── motifs ──

    @Test
    void should_link_each_nightly_perimeter_to_one_motive() {
        assertThat(MotifDeProposition.pour(Perimetre.COUVERTURE)).contains(MotifDeProposition.SOUS_EFFECTIF);
        assertThat(MotifDeProposition.pour(Perimetre.MAINTENANCE)).contains(MotifDeProposition.MAINTENANCE);
        assertThat(MotifDeProposition.pour(Perimetre.PATIENTS)).contains(MotifDeProposition.GAIN);
        assertThat(MotifDeProposition.pour(Perimetre.ROULEMENT)).isEqualTo(Optional.empty());
        assertThat(MotifDeProposition.pour(Perimetre.COMPLET)).isEmpty();
        assertThat(MotifDeProposition.GAIN.perimetre()).isEqualTo(Perimetre.PATIENTS);
    }

    @Test
    void should_value_a_coverage_by_the_new_vacations_plus_the_missing_ones() {
        ResultatDOptimisation resultat = new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(),
                List.of(new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, infirmier(1), "N", false),
                        new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, B, MATIN, infirmier(2), "N", true)),
                List.of(new VacationNonPourvue(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, 2)), null, null);
        assertThat(MotifDeProposition.SOUS_EFFECTIF.valeur(resultat)).isEqualTo(3);
    }

    @Test
    void should_value_a_maintenance_by_the_moves_and_the_sessions_without_solution() {
        ResultatDOptimisation resultat = new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                null, List.of(new DeplacementTemporairePropose(patient(1), "P", LUNDI, JourDeLaSemaine.LUNDI, null,
                poste(B, MATIN, B1), "PREVENTIVE")),
                List.of(new SeanceSansSolution(patient(2), "P", LUNDI, JourDeLaSemaine.LUNDI, null, "x")));
        assertThat(MotifDeProposition.MAINTENANCE.valeur(resultat)).isEqualTo(2);
    }

    @Test
    void should_value_a_placement_by_the_saved_vacations_and_the_placed_waiting_patients_never_below_zero() {
        ResultatDOptimisation gain = new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                indicateurs(10, 3), indicateurs(8, 1));
        ResultatDOptimisation perte = new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                indicateurs(8, 1), indicateurs(10, 3));
        ResultatDOptimisation sansIndicateurs = OptimisationEnMemoireResultat.vide();
        assertThat(MotifDeProposition.GAIN.valeur(gain)).isEqualTo(4);
        assertThat(MotifDeProposition.GAIN.valeur(perte)).isZero();
        assertThat(MotifDeProposition.GAIN.valeur(sansIndicateurs)).isZero();
    }

    /** Un résultat sans indicateurs. */
    private static final class OptimisationEnMemoireResultat {
        static ResultatDOptimisation vide() {
            return new ResultatDOptimisation(null, null, null, null, null, null, null, null);
        }
    }

    // ── calendrier ──

    private static JeuDOptimisation jeu() {
        return new JeuDOptimisation()
                .patient(1, LMV, false, poste(A, MATIN, A1))
                .patient(2, LMV, true, poste(A, MATIN, A2))
                .infirmierActif(1, false)
                .affecte(1, A, MATIN, LMV);
    }

    private static Optional<JourDuCalendrier> jour(List<CaseDuCalendrier> cases, Identifiants.SalleId salle, LocalDate semaine,
                                                   JourDeLaSemaine jour) {
        return cases.stream().filter(c -> c.salleId().equals(salle.valeur()) && c.semaineDebut().equals(semaine))
                .flatMap(c -> c.jours().stream()).filter(j -> j.jour() == jour).findFirst();
    }

    @Test
    void should_show_the_current_patients_and_the_current_nurses_when_nothing_changes() {
        ParametresDOptimisation parametres = JeuDOptimisation.parametres(Perimetre.PATIENTS);
        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu().donnees(), OptimisationEnMemoireResultat.vide(),
                parametres);

        assertThat(cases).singleElement().satisfies(c -> {
            assertThat(c.salleId()).isEqualTo(A.valeur());
            assertThat(c.salleNom()).isEqualTo("Salle A");
            assertThat(c.creneauLibelle()).isEqualTo("Matin");
        });
        JourDuCalendrier lundi = jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow();
        assertThat(lundi.patients()).extracting(CalendrierDeLaProposition.PatientDuCalendrier::nom).containsExactly("Patient 1", "Patient 2");
        assertThat(lundi.patients()).allMatch(p -> !p.deplace() && !p.temporaire());
        assertThat(lundi.infirmiers()).singleElement().satisfies(i -> assertThat(i.situation()).isEqualTo(SituationInfirmier.PREVU));
        assertThat(lundi.requis()).isEqualTo(1);
        assertThat(lundi.manque()).isZero();
        assertThat(jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.MARDI).orElseThrow().vide()).isTrue();
    }

    @Test
    void should_place_a_moved_patient_in_its_new_room_and_say_where_it_came_from() {
        ResultatDOptimisation resultat = new ResultatDOptimisation(List.of(), List.of(),
                List.of(new DeplacementDePatient(patient(1), "Patient 1", poste(A, MATIN, A1), new Poste(B, MATIN, B1, "G-B1"))),
                List.of(), List.of(), List.of(), null, null);

        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu().donnees(), resultat,
                JeuDOptimisation.parametres(Perimetre.PATIENTS));

        JourDuCalendrier chezB = jour(cases, B, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow();
        assertThat(chezB.patients()).singleElement().satisfies(p -> {
            assertThat(p.deplace()).isTrue();
            assertThat(p.generateurCode()).isEqualTo("G-B1");
            assertThat(p.avant()).isEqualTo("Salle A · Matin · G");
        });
        assertThat(jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow().patients()).hasSize(1);
    }

    @Test
    void should_project_the_typical_week_on_every_week_of_the_horizon_with_closed_days() {
        JeuDOptimisation jeu = jeu();
        jeu.ressources = JeuDePlaces.ressources(JeuDePlaces.OUVERTS, List.of(new RessourcesDuCentre.Fermeture(LUNDI.plusWeeks(1), "Férié")));
        ParametresDOptimisation deuxSemaines = new ParametresDOptimisation(Perimetre.PATIENTS, LUNDI, 2, 20, 5, null, 2, 6);

        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu.donnees(), OptimisationEnMemoireResultat.vide(),
                deuxSemaines);

        assertThat(cases).extracting(CaseDuCalendrier::semaineDebut).containsOnly(JeuDOptimisation.DIMANCHE, JeuDOptimisation.DIMANCHE.plusWeeks(1));
        JourDuCalendrier ferie = jour(cases, A, JeuDOptimisation.DIMANCHE.plusWeeks(1), JourDeLaSemaine.LUNDI).orElseThrow();
        assertThat(ferie.ferme()).isTrue();
        assertThat(ferie.motifFermeture()).isEqualTo("Férié");
        assertThat(ferie.patients()).isEmpty();
    }

    @Test
    void should_take_the_nurses_of_the_proposition_and_flag_the_new_ones_when_it_plans_the_staff() {
        ResultatDOptimisation resultat = new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(),
                List.of(new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, infirmier(1), "Zoe", true),
                        new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, infirmier(2), "Abel", false)),
                List.of(), null, null);

        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu().donnees(), resultat,
                JeuDOptimisation.parametres(Perimetre.ROULEMENT));

        JourDuCalendrier lundi = jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow();
        assertThat(lundi.infirmiers()).extracting(CalendrierDeLaProposition.InfirmierDuCalendrier::nom).containsExactly("Abel", "Zoe");
        assertThat(lundi.infirmiers().getFirst().situation()).isEqualTo(SituationInfirmier.NOUVEAU);
        assertThat(lundi.infirmiers().getLast().situation()).isEqualTo(SituationInfirmier.PREVU);
        // deux patients exigent un infirmier (ratio 4) : le second infirmier de la proposition est en surplus
        assertThat(lundi.requis()).isEqualTo(1);
        assertThat(lundi.surplus()).isEqualTo(1);
        assertThat(lundi.manque()).isZero();
    }

    @Test
    void should_list_an_absent_nurse_as_absent_and_report_the_missing_one() {
        JeuDOptimisation jeu = jeu().absent(1, LUNDI, LUNDI);

        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu.donnees(), OptimisationEnMemoireResultat.vide(),
                new ParametresDOptimisation(Perimetre.COUVERTURE, LUNDI, 1, 20, 5, null, 2, 6));

        JourDuCalendrier lundi = jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow();
        assertThat(lundi.infirmiers()).singleElement().satisfies(i -> assertThat(i.situation()).isEqualTo(SituationInfirmier.ABSENT));
        assertThat(lundi.manque()).isEqualTo(1);
    }

    @Test
    void should_move_a_patient_for_one_date_only_when_the_move_is_temporary() {
        ResultatDOptimisation resultat = new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                null, List.of(new DeploiementTemporaire().vers(patient(1), LUNDI)), List.of());

        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu().donnees(), resultat,
                new ParametresDOptimisation(Perimetre.MAINTENANCE, LUNDI, 1, 20, 5, null, 2, 6));

        JourDuCalendrier ailleurs = jour(cases, B, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow();
        assertThat(ailleurs.patients()).singleElement().satisfies(p -> {
            assertThat(p.temporaire()).isTrue();
            assertThat(p.avant()).isEqualTo("Salle A · Matin · G");
        });
        assertThat(jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow().patients())
                .extracting(CalendrierDeLaProposition.PatientDuCalendrier::nom).containsExactly("Patient 2");
        assertThat(jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.MERCREDI).orElseThrow().patients()).hasSize(2);
    }

    @Test
    void should_remove_the_patients_the_proposition_leaves_without_a_place() {
        ResultatDOptimisation resultat = new ResultatDOptimisation(List.of(), List.of(), List.of(),
                List.of(new PatientNonPlace(patient(1), "Patient 1", ResultatDOptimisation.CauseNonPlace.AUCUNE_PLACE)), List.of(),
                List.of(), null, null);

        List<CaseDuCalendrier> cases = CalendrierDeLaProposition.construire(jeu().donnees(), resultat,
                JeuDOptimisation.parametres(Perimetre.PATIENTS));

        assertThat(jour(cases, A, JeuDOptimisation.DIMANCHE, JourDeLaSemaine.LUNDI).orElseThrow().patients())
                .extracting(CalendrierDeLaProposition.PatientDuCalendrier::nom).containsExactly("Patient 2");
    }

    /** Un déplacement temporaire proposé de la salle A vers la salle B, le matin. */
    private static final class DeploiementTemporaire {
        DeplacementTemporairePropose vers(Identifiants.PatientId patient, LocalDate date) {
            return new DeplacementTemporairePropose(patient, "Patient 1", date, JourDeLaSemaine.LUNDI, poste(A, MATIN, A1),
                    new Poste(B, MATIN, B1, "G-B1"), "PREVENTIVE");
        }
    }
}
