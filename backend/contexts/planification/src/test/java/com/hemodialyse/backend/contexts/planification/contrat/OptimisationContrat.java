package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.InfirmierDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.JourDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.PatientDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.SituationInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Empreinte;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.CauseNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.PatientNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.SeanceSansSolution;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.id;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la persistance de l'optimisation (P8), exécuté contre le double en mémoire ET contre l'adapter JDBC (H2 et PostgreSQL 16) :
 * exécutions (cycle, relecture fidèle du résultat, historique, purge, suppression, reprise), déplacements temporaires (un par patient
 * et par date, borné au centre) et calendrier figé (lecture par semaine, purge des orphelins).
 */
abstract class OptimisationContrat {

    static final CentreId AUTRE_CENTRE = new CentreId(id(2));
    static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    static final LocalDate LUNDI = LocalDate.of(2026, 11, 2);

    static ExecutionId execution(int n) {
        return new ExecutionId(id(5000 + n));
    }

    static ExecutionDOptimisation lancee(int n, CentreId centre, Instant creeLe) {
        return ExecutionDOptimisation.lancer(execution(n), centre, new ParametresDOptimisation(Perimetre.COMPLET, LUNDI, 2, 30, 4,
                null, 2, 6, 4, 35, 2), "utilisateur-" + n, new Empreinte("e".repeat(64)), creeLe);
    }

    static ResultatDOptimisation resultatComplet() {
        Poste de = new Poste(A, MATIN, A1, "G-A1");
        Poste vers = new Poste(B, SOIR, B1, "G-B1");
        return new ResultatDOptimisation(
                List.of(new RessourcesDuCentre.Salle(A, "Salle A", false), new RessourcesDuCentre.Salle(B, "Salle B", false)),
                List.of(new RessourcesDuCentre.Creneau(MATIN, "Matin", 1), new RessourcesDuCentre.Creneau(SOIR, "Soir", 2)),
                List.of(new DeplacementDePatient(patient(1), "Patient 1", de, vers, List.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI)),
                        new DeplacementDePatient(patient(2), "Patient 2", null, vers)),
                List.of(new PatientNonPlace(patient(3), "Patient 3", CauseNonPlace.JOURS_FERMES)),
                List.of(new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, infirmier(1), "Anne", true)),
                List.of(new VacationNonPourvue(LUNDI.plusDays(1), JourDeLaSemaine.MARDI, B, SOIR, 2)),
                new Indicateurs(3, 4, 5, 6, 7, 8, 9, 10, 11), new Indicateurs(1, 2, 3, 4, 5, 6, 7, 8, 9),
                List.of(new DeploiementTemporaire().propose()),
                List.of(new SeanceSansSolution(patient(4), "Patient 4", LUNDI, JourDeLaSemaine.LUNDI, de, "PREVENTIVE")));
    }

    static ResultatDOptimisation resultatVide() {
        return new ResultatDOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
    }

    private static DeplacementTemporaire deplacement(int n, CentreId centre, int patient, LocalDate date, String motif) {
        return DeplacementTemporaire.reconstituer(new DeplacementTemporaireId(id(6000 + n)), centre, patient(patient), date, B, MATIN, B1,
                motif);
    }

    private static CaseDuCalendrier ligne(LocalDate semaine, UUID salle, int salleOrdre, UUID creneau, int creneauOrdre) {
        JourDuCalendrier jour = new JourDuCalendrier(JourDeLaSemaine.LUNDI, semaine.plusDays(1), false, null, 1, 0, 1,
                List.of(new PatientDuCalendrier(patient(1).valeur(), "Patient 1", "G-A1", true, true, false, "Salle A · Matin · G-A1")),
                List.of(new InfirmierDuCalendrier("Anne", SituationInfirmier.NOUVEAU)));
        JourDuCalendrier ferme = new JourDuCalendrier(JourDeLaSemaine.MARDI, semaine.plusDays(2), true, "Férié", 0, 0, 0, List.of(), List.of());
        return new CaseDuCalendrier(semaine, salle, "Salle " + salleOrdre, salleOrdre, creneau, "Créneau " + creneauOrdre, creneauOrdre,
                List.of(jour, ferme));
    }

    abstract ExecutionsRepository executions();

    abstract DeplacementsTemporairesRepository deplacements();

    abstract CalendriersDesPropositions calendriers();

    // ───────────────────────────── exécutions ─────────────────────────────

    /**
     * Vide les tables des deux centres.
     */
    abstract void nettoyer();

    @BeforeEach
    void partirDeRien() {
        nettoyer();
    }

    @Test
    void should_read_back_a_started_execution_as_it_was_written() {
        ExecutionDOptimisation e = lancee(1, CENTRE, T0);
        executions().enregistrer(e);

        assertThat(executions().parId(CENTRE, e.id())).contains(e);
        assertThat(executions().enCours(CENTRE)).contains(e);
    }

    @Test
    void should_keep_the_parameters_the_summary_and_the_whole_result_through_the_transitions() {
        ExecutionDOptimisation e = lancee(1, CENTRE, T0);
        executions().enregistrer(e);
        executions().enregistrer(e.progresser("COMPLET", "0hard/-3soft"));
        ExecutionDOptimisation terminee = e.progresser("COMPLET", "0hard/-3soft").terminer(resultatComplet(), "0hard/-1soft", T0.plusSeconds(30));
        executions().enregistrer(terminee);

        assertThat(executions().parId(CENTRE, e.id())).contains(terminee);
        assertThat(executions().enCours(CENTRE)).isEmpty();

        ExecutionDOptimisation appliquee = terminee.appliquer(new Empreinte("e".repeat(64)), T0.plusSeconds(90));
        executions().enregistrer(appliquee);
        assertThat(executions().parId(CENTRE, e.id())).contains(appliquee);
        assertThat(executions().parId(CENTRE, e.id()).orElseThrow().appliqueeLe()).isEqualTo(T0.plusSeconds(90));
    }

    @Test
    void should_record_a_failure_with_its_reason() {
        ExecutionDOptimisation e = lancee(1, CENTRE, T0);
        executions().enregistrer(e);
        ExecutionDOptimisation echec = e.echouer("pas de solution", T0.plusSeconds(5));
        executions().enregistrer(echec);

        assertThat(executions().parId(CENTRE, e.id())).contains(echec);
    }

    @Test
    void should_never_show_an_execution_to_another_centre() {
        ExecutionDOptimisation e = lancee(1, CENTRE, T0);
        executions().enregistrer(e);

        assertThat(executions().parId(AUTRE_CENTRE, e.id())).isEmpty();
        assertThat(executions().enCours(AUTRE_CENTRE)).isEmpty();
        assertThat(executions().page(AUTRE_CENTRE, new Pagination(0, 20)).elements()).isEmpty();
        assertThat(executions().supprimer(AUTRE_CENTRE, e.id())).isFalse();
        assertThat(executions().parId(CENTRE, e.id())).isPresent();
    }

    @Test
    void should_page_the_history_from_the_most_recent_with_the_total() {
        for (int i = 1; i <= 5; i++) {
            ExecutionDOptimisation e = lancee(i, CENTRE, T0.plusSeconds(i));
            executions().enregistrer(e.terminer(resultatVide(), "s", T0.plusSeconds(100 + i)));
        }

        var premiere = executions().page(CENTRE, new Pagination(0, 2));
        var troisieme = executions().page(CENTRE, new Pagination(2, 2));

        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.elements()).extracting(ExecutionDOptimisation::id).containsExactly(execution(5), execution(4));
        assertThat(troisieme.elements()).extracting(ExecutionDOptimisation::id).containsExactly(execution(1));
        assertThat(troisieme.page()).isEqualTo(2);
        assertThat(troisieme.taille()).isEqualTo(2);
        assertThat(executions().page(CENTRE, new Pagination(9, 2)).elements()).isEmpty();
    }

    @Test
    void should_keep_only_the_most_recent_executions_of_the_centre_when_purging() {
        for (int i = 1; i <= 4; i++) {
            executions().enregistrer(lancee(i, CENTRE, T0.plusSeconds(i)).echouer("x", T0.plusSeconds(i)));
        }
        executions().enregistrer(lancee(9, AUTRE_CENTRE, T0).echouer("x", T0));

        executions().purger(CENTRE, 2);

        assertThat(executions().page(CENTRE, new Pagination(0, 20)).elements()).extracting(ExecutionDOptimisation::id)
                .containsExactly(execution(4), execution(3));
        assertThat(executions().parId(AUTRE_CENTRE, execution(9))).isPresent();
    }

    @Test
    void should_delete_an_execution_of_the_centre_only() {
        executions().enregistrer(lancee(1, CENTRE, T0).echouer("x", T0));

        assertThat(executions().supprimer(CENTRE, execution(1))).isTrue();
        assertThat(executions().supprimer(CENTRE, execution(1))).isFalse();
        assertThat(executions().parId(CENTRE, execution(1))).isEmpty();
    }

    // ───────────────────────────── déplacements temporaires ─────────────────────────────

    @Test
    void should_fail_every_execution_left_in_progress_by_a_restart_in_every_centre() {
        executions().enregistrer(lancee(1, CENTRE, T0));
        executions().enregistrer(lancee(2, AUTRE_CENTRE, T0));
        executions().enregistrer(lancee(3, CENTRE, T0.plusSeconds(1)).terminer(resultatVide(), "s", T0.plusSeconds(2)));

        int n = executions().interrompreLesEnCours("Calcul interrompu par l'arrêt du serveur");

        assertThat(n).isEqualTo(2);
        ExecutionDOptimisation interrompue = executions().parId(CENTRE, execution(1)).orElseThrow();
        assertThat(interrompue.statut()).isEqualTo(ExecutionDOptimisation.Statut.ECHEC);
        assertThat(interrompue.erreur()).isEqualTo("Calcul interrompu par l'arrêt du serveur");
        assertThat(executions().parId(AUTRE_CENTRE, execution(2)).orElseThrow().enCours()).isFalse();
        assertThat(executions().parId(CENTRE, execution(3)).orElseThrow().statut()).isEqualTo(ExecutionDOptimisation.Statut.TERMINEE);
    }

    @Test
    void should_record_and_read_the_temporary_moves_by_date_then_patient() {
        deplacements().enregistrer(List.of(deplacement(1, CENTRE, 2, LUNDI.plusDays(1), "PREVENTIVE"),
                deplacement(2, CENTRE, 1, LUNDI.plusDays(1), null), deplacement(3, CENTRE, 1, LUNDI, "CORRECTIVE")));

        List<DeplacementTemporaire> lus = deplacements().entre(CENTRE, LUNDI, LUNDI.plusDays(1));

        assertThat(lus).extracting(d -> d.patient().valeur().toString() + "|" + d.date())
                .containsExactly(patient(1).valeur() + "|" + LUNDI, patient(1).valeur() + "|" + LUNDI.plusDays(1),
                        patient(2).valeur() + "|" + LUNDI.plusDays(1));
        assertThat(lus.getFirst().motif()).contains("CORRECTIVE");
        assertThat(lus.get(1).motif()).isEmpty();
        assertThat(lus.getFirst().salle()).isEqualTo(B);
        assertThat(lus.getFirst().creneau()).isEqualTo(MATIN);
        assertThat(lus.getFirst().generateur()).isEqualTo(B1);
    }

    @Test
    void should_keep_one_move_per_patient_and_date_the_last_written_replacing_the_others() {
        deplacements().enregistrer(List.of(deplacement(1, CENTRE, 1, LUNDI, "premier"), deplacement(2, CENTRE, 1, LUNDI, "second")));
        assertThat(deplacements().entre(CENTRE, LUNDI, LUNDI)).singleElement().satisfies(d -> assertThat(d.motif()).contains("second"));

        deplacements().enregistrer(List.of(deplacement(3, CENTRE, 1, LUNDI, "troisieme")));
        assertThat(deplacements().entre(CENTRE, LUNDI, LUNDI)).singleElement().satisfies(d -> {
            assertThat(d.motif()).contains("troisieme");
            assertThat(d.id()).isEqualTo(new DeplacementTemporaireId(id(6003)));
        });
    }

    @Test
    void should_include_the_bounds_of_the_period_and_exclude_what_is_outside() {
        deplacements().enregistrer(List.of(deplacement(1, CENTRE, 1, LUNDI.minusDays(1), null), deplacement(2, CENTRE, 2, LUNDI, null),
                deplacement(3, CENTRE, 3, LUNDI.plusDays(6), null), deplacement(4, CENTRE, 4, LUNDI.plusDays(7), null)));

        assertThat(deplacements().entre(CENTRE, LUNDI, LUNDI.plusDays(6))).extracting(d -> d.patient()).containsExactly(patient(2), patient(3));
    }

    @Test
    void should_scope_the_temporary_moves_to_the_centre() {
        deplacements().enregistrer(List.of(deplacement(1, CENTRE, 1, LUNDI, null)));
        deplacements().enregistrer(List.of(deplacement(2, AUTRE_CENTRE, 1, LUNDI, "autre")));

        assertThat(deplacements().entre(CENTRE, LUNDI, LUNDI)).singleElement().satisfies(d -> assertThat(d.motif()).isEmpty());
        assertThat(deplacements().entre(AUTRE_CENTRE, LUNDI, LUNDI)).singleElement().satisfies(d -> assertThat(d.motif()).contains("autre"));
    }

    // ───────────────────────────── calendrier figé ─────────────────────────────

    @Test
    void should_freeze_and_read_the_calendar_by_week_ordered_by_slot_then_room() {
        executions().enregistrer(lancee(1, CENTRE, T0));
        LocalDate dimanche = LocalDate.of(2026, 11, 1);
        calendriers().enregistrer(CENTRE, execution(1), List.of(ligne(dimanche, A.valeur(), 2, SOIR.valeur(), 2),
                ligne(dimanche, B.valeur(), 3, MATIN.valeur(), 1), ligne(dimanche, A.valeur(), 1, MATIN.valeur(), 1),
                ligne(dimanche.plusWeeks(1), A.valeur(), 1, MATIN.valeur(), 1)));

        List<CaseDuCalendrier> semaine = calendriers().lire(CENTRE, execution(1), dimanche);

        assertThat(semaine).extracting(CaseDuCalendrier::salleOrdre).containsExactly(1, 3, 2);
        assertThat(semaine.getFirst().jours()).hasSize(2);
        assertThat(semaine.getFirst().jours().getFirst().patients()).singleElement().satisfies(p -> {
            assertThat(p.patientId()).isEqualTo(patient(1).valeur());
            assertThat(p.deplace()).isTrue();
            assertThat(p.avant()).isEqualTo("Salle A · Matin · G-A1");
        });
        assertThat(semaine.getFirst().jours().getFirst().infirmiers().getFirst().situation()).isEqualTo(SituationInfirmier.NOUVEAU);
        assertThat(semaine.getFirst().jours().get(1).ferme()).isTrue();
        assertThat(semaine.getFirst().jours().get(1).motifFermeture()).isEqualTo("Férié");
        assertThat(calendriers().semaines(CENTRE, execution(1))).containsExactly(dimanche, dimanche.plusWeeks(1));
        assertThat(calendriers().lire(CENTRE, execution(1), dimanche.plusWeeks(5))).isEmpty();
    }

    @Test
    void should_replace_the_calendar_of_an_execution_when_frozen_again() {
        executions().enregistrer(lancee(1, CENTRE, T0));
        LocalDate dimanche = LocalDate.of(2026, 11, 1);
        calendriers().enregistrer(CENTRE, execution(1), List.of(ligne(dimanche, A.valeur(), 1, MATIN.valeur(), 1)));
        calendriers().enregistrer(CENTRE, execution(1), List.of(ligne(dimanche.plusWeeks(1), A.valeur(), 1, MATIN.valeur(), 1)));

        assertThat(calendriers().semaines(CENTRE, execution(1))).containsExactly(dimanche.plusWeeks(1));
    }

    @Test
    void should_scope_the_calendar_to_the_centre_and_purge_the_orphans_of_deleted_executions() {
        executions().enregistrer(lancee(1, CENTRE, T0));
        executions().enregistrer(lancee(2, CENTRE, T0.plusSeconds(1)));
        LocalDate dimanche = LocalDate.of(2026, 11, 1);
        calendriers().enregistrer(CENTRE, execution(1), List.of(ligne(dimanche, A.valeur(), 1, MATIN.valeur(), 1)));
        calendriers().enregistrer(CENTRE, execution(2), List.of(ligne(dimanche, A.valeur(), 1, MATIN.valeur(), 1)));
        assertThat(calendriers().lire(AUTRE_CENTRE, execution(1), dimanche)).isEmpty();
        assertThat(calendriers().semaines(AUTRE_CENTRE, execution(1))).isEmpty();

        executions().supprimer(CENTRE, execution(1));
        calendriers().purgerOrphelins(AUTRE_CENTRE);
        assertThat(calendriers().semaines(CENTRE, execution(1))).hasSize(1);
        calendriers().purgerOrphelins(CENTRE);

        assertThat(calendriers().semaines(CENTRE, execution(1))).isEmpty();
        assertThat(calendriers().semaines(CENTRE, execution(2))).hasSize(1);
    }

    static final class DeploiementTemporaire {
        DeplacementTemporairePropose propose() {
            return new DeplacementTemporairePropose(patient(1), "Patient 1", LUNDI, JourDeLaSemaine.LUNDI,
                    new Poste(A, MATIN, A1, "G-A1"), new Poste(B, MATIN, B1, "G-B1"), "PREVENTIVE");
        }
    }
}
