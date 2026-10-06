package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.RunsEnMemoire;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.infirmier.port.RemplacementInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.service.EmpreinteOptimisation;
import com.hemodialyse.backend.domain.planning.port.DeplacementTemporairePort;
import com.hemodialyse.backend.domain.planning.port.PlacementPatientPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.infrastructure.optimisation.timefold.OptimisationFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.DIMANCHE;
import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.T0;
import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.horloge;
import static com.hemodialyse.backend.application.planning.optimisation.OptimisationTestSupport.parametres;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimisationApplicationServiceTest {

    private final OptimisationFixture f = new OptimisationFixture();
    private final UUID centre = OptimisationFixture.CENTRE;
    private final RunsEnMemoire runs = new RunsEnMemoire();
    private final OptimisationDonneesPort donnees = mock(OptimisationDonneesPort.class);
    private final PlacementPatientPort placements = mock(PlacementPatientPort.class);
    private final AffectationInfirmierRepositoryPort affectations = mock(AffectationInfirmierRepositoryPort.class);
    private final RemplacementInfirmierRepositoryPort remplacements = mock(RemplacementInfirmierRepositoryPort.class);
    private final DeplacementTemporairePort temporaires = mock(DeplacementTemporairePort.class);
    private final OptimisationApplicationService service = new OptimisationApplicationService(runs, donnees, placements,
            affectations, remplacements, temporaires, horloge(T0.plusSeconds(60)));

    private SalleRef a;
    private CreneauRef matin;
    private PatientAPlacer p1;

    @BeforeEach
    void setUp() {
        a = f.salle("Salle A", 3);
        matin = f.creneau("Matin");
        p1 = f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
    }

    private RunOptimisation run(PerimetreOptimisation perimetre, ResultatOptimisation resultat) {
        DonneesOptimisation lues = f.build();
        ParametresOptimisation parametres = parametres(perimetre);
        RunOptimisation termine = RunOptimisation.demarrer(centre, parametres, "admin",
                        EmpreinteOptimisation.calculer(lues, parametres), T0)
                .terminer(resultat, "0hard/0medium/0soft", T0.plusSeconds(10));
        runs.save(termine);
        when(donnees.charger(eq(centre), eq(parametres.debutSemaine()), eq(parametres.finHorizon()))).thenReturn(lues);
        return termine;
    }

    private ResultatOptimisation resultat(List<DeplacementPatient> deplacements, List<VacationPlanifiee> vacations) {
        return new ResultatOptimisation(List.of(), List.of(), deplacements, List.of(), vacations, List.of(),
                OptimisationTestSupport.resultatVide().avant(), OptimisationTestSupport.resultatVide().apres());
    }

    private static BusinessException refus(org.assertj.core.api.ThrowableAssert.ThrowingCallable appel) {
        return (BusinessException) org.assertj.core.api.Assertions.catchThrowable(appel);
    }

    @Test
    void should_move_the_patients_and_mark_the_run_as_applied() {
        Poste vers = new Poste(a.id(), matin.id(), f.generateur(a, 1).id(), "A-G2");
        RunOptimisation run = run(PerimetreOptimisation.PATIENTS,
                resultat(List.of(new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), vers)), List.of()));

        RunOptimisation appliquee = service.appliquer(centre, run.id());

        verify(placements).deplacer(centre, p1.patientId(), a.id(), matin.id(), vers.generateurId());
        assertThat(appliquee.appliqueLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(runs.findById(centre, run.id()).orElseThrow().appliqueLe()).isNotNull();
    }

    @Test
    void should_refuse_a_proposal_when_the_center_changed_since_the_calculation() {
        Poste vers = new Poste(a.id(), matin.id(), f.generateur(a, 1).id(), "A-G2");
        RunOptimisation run = run(PerimetreOptimisation.PATIENTS,
                resultat(List.of(new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), vers)), List.of()));
        f.patientNonPlace("Nouveau", false, MERCREDI); // le centre change après le calcul
        when(donnees.charger(eq(centre), any(), any())).thenReturn(f.build());

        assertThat(refus(() -> service.appliquer(centre, run.id())).getCode()).isEqualTo("OPTIMISATION_PERIMEE");
        verify(placements, never()).deplacer(any(), any(), any(), any(), any());
        assertThat(runs.findById(centre, run.id()).orElseThrow().appliqueLe()).isNull();
    }

    @Test
    void should_refuse_a_new_place_that_breaks_the_planning_rules_and_move_nobody() {
        PatientAPlacer p2 = f.patient("P2", false, a, matin, f.generateur(a, 1), LUNDI);
        Poste surP2 = p2.actuelle();
        Poste libre = new Poste(a.id(), matin.id(), f.generateur(a, 2).id(), "A-G3");
        RunOptimisation run = run(PerimetreOptimisation.PATIENTS, resultat(List.of(
                new DeplacementPatient(p1.patientId(), "P1", p1.actuelle(), libre),
                new DeplacementPatient(p2.patientId(), "P2", p2.actuelle(), libre)), List.of()));
        assertThat(surP2).isNotNull();

        BusinessException e = refus(() -> service.appliquer(centre, run.id()));

        assertThat(e.getCode()).isEqualTo("OPTIMISATION_PLACEMENT_GENERATEUR_OCCUPE");
        verify(placements, never()).deplacer(any(), any(), any(), any(), any());
    }

    @Test
    void should_not_apply_twice_nor_an_unfinished_run_nor_the_run_of_another_center() {
        RunOptimisation run = run(PerimetreOptimisation.PATIENTS, resultat(List.of(), List.of()));
        service.appliquer(centre, run.id());

        assertThat(refus(() -> service.appliquer(centre, run.id())).getCode()).isEqualTo("OPTIMISATION_DEJA_APPLIQUEE");
        assertThat(refus(() -> service.appliquer(UUID.randomUUID(), run.id())).getCode())
                .isEqualTo("OPTIMISATION_INTROUVABLE");

        RunOptimisation enCours = runs.save(RunOptimisation.demarrer(centre, parametres(PerimetreOptimisation.PATIENTS),
                "admin", "x", T0));
        assertThat(refus(() -> service.appliquer(centre, enCours.id())).getCode()).isEqualTo("OPTIMISATION_NON_APPLICABLE");
    }

    @Test
    void should_replace_the_rotation_keeping_what_does_not_change() {
        InfirmierRef marie = f.infirmier("Marie");
        InfirmierRef paul = f.infirmier("Paul");
        InfirmierRef lea = f.infirmier("Léa");
        f.affecter(marie, a, matin, LUNDI, MERCREDI);
        f.affecter(paul, a, matin, LUNDI);
        AffectationInfirmier deMarie = f.affectations.get(0);
        AffectationInfirmier dePaul = f.affectations.get(1);
        when(affectations.findByInfirmierIds(eq(centre), any())).thenReturn(List.copyOf(f.affectations));
        // proposition : Marie ne garde que le lundi, Paul est retiré, Léa prend le mercredi
        RunOptimisation run = run(PerimetreOptimisation.ROULEMENT, resultat(List.of(), List.of(
                vacation(LUNDI, marie, true), vacation(MERCREDI, lea, false))));

        service.appliquer(centre, run.id());

        ArgumentCaptor<AffectationInfirmier> sauvees = ArgumentCaptor.forClass(AffectationInfirmier.class);
        verify(affectations, org.mockito.Mockito.times(2)).save(sauvees.capture());
        assertThat(sauvees.getAllValues()).anySatisfy(x -> {
            assertThat(x.id()).isEqualTo(deMarie.id());
            assertThat(x.jours()).containsExactly(LUNDI);
        }).anySatisfy(x -> {
            assertThat(x.infirmierId()).isEqualTo(lea.id());
            assertThat(x.jours()).containsExactly(MERCREDI);
            assertThat(x.centerId()).isEqualTo(centre);
        });
        verify(affectations).delete(centre, dePaul.id());
        verify(affectations, never()).delete(centre, deMarie.id());
    }

    @Test
    void should_leave_an_unchanged_rotation_untouched() {
        InfirmierRef marie = f.infirmier("Marie");
        f.affecter(marie, a, matin, LUNDI);
        when(affectations.findByInfirmierIds(eq(centre), any())).thenReturn(List.copyOf(f.affectations));
        RunOptimisation run = run(PerimetreOptimisation.ROULEMENT,
                resultat(List.of(), List.of(vacation(LUNDI, marie, true))));

        service.appliquer(centre, run.id());

        verify(affectations, never()).save(any());
        verify(affectations, never()).delete(any(), any());
    }

    @Test
    void should_create_replacements_only_for_the_vacations_not_already_held() {
        InfirmierRef marie = f.infirmier("Marie");
        InfirmierRef paul = f.infirmier("Paul");
        RunOptimisation run = run(PerimetreOptimisation.COUVERTURE, resultat(List.of(),
                List.of(vacation(LUNDI, marie, true), vacation(LUNDI, paul, false))));

        service.appliquer(centre, run.id());

        ArgumentCaptor<RemplacementInfirmier> sauve = ArgumentCaptor.forClass(RemplacementInfirmier.class);
        verify(remplacements).save(sauve.capture());
        assertThat(sauve.getValue().infirmierId()).isEqualTo(paul.id());
        assertThat(sauve.getValue().centerId()).isEqualTo(centre);
        assertThat(sauve.getValue().date()).isEqualTo(DIMANCHE.plusDays(1));
        verify(affectations, never()).save(any());
    }

    private VacationPlanifiee vacation(JourSemaine jour, InfirmierRef infirmier, boolean existante) {
        LocalDate date = DIMANCHE.plusDays(jour.ordinal());
        return new VacationPlanifiee(date, jour, a.id(), matin.id(), infirmier.id(), infirmier.nom(), existante);
    }

    @Test
    void should_record_the_days_chosen_by_the_optimisation_before_moving_the_patient() {
        PatientAPlacer nouveau = f.patientAChoisir("Nouveau", 2);
        Poste vers = new Poste(a.id(), matin.id(), f.generateur(a, 1).id(), "A-G2");
        RunOptimisation run = run(PerimetreOptimisation.PATIENTS, resultat(List.of(new DeplacementPatient(
                nouveau.patientId(), "Nouveau", null, vers, List.of(JourSemaine.MARDI, JourSemaine.JEUDI))), List.of()));

        service.appliquer(centre, run.id());

        verify(placements).definirJours(centre, nouveau.patientId(), EnumSet.of(JourSemaine.MARDI, JourSemaine.JEUDI));
        verify(placements).deplacer(centre, nouveau.patientId(), a.id(), matin.id(), vers.generateurId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_record_the_temporary_moves_of_a_maintenance_proposal_without_touching_the_usual_place() {
        LocalDate lundi = DIMANCHE.plusDays(1);
        Poste vers = new Poste(a.id(), matin.id(), f.generateur(a, 1).id(), "A-G2");
        ResultatOptimisation proposition = new ResultatOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), OptimisationTestSupport.resultatVide().avant(), OptimisationTestSupport.resultatVide().apres(),
                List.of(new DeplacementTemporairePropose(p1.patientId(), "P1", lundi, LUNDI, p1.actuelle(), vers,
                        "Révision")), List.of());
        RunOptimisation run = run(PerimetreOptimisation.MAINTENANCE, proposition);

        service.appliquer(centre, run.id());

        ArgumentCaptor<List<DeplacementTemporaire>> enregistres = ArgumentCaptor.forClass(List.class);
        verify(temporaires).enregistrer(enregistres.capture());
        assertThat(enregistres.getValue()).singleElement().satisfies(t -> {
            assertThat(t.centerId()).isEqualTo(centre);
            assertThat(t.patientId()).isEqualTo(p1.patientId());
            assertThat(t.date()).isEqualTo(lundi);
            assertThat(t.generateurId()).isEqualTo(vers.generateurId());
            assertThat(t.motif()).isEqualTo("Révision");
        });
        verify(placements, never()).deplacer(any(), any(), any(), any(), any());
    }
}
