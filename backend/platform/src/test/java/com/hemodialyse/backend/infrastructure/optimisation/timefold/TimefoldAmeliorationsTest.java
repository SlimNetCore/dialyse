package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort.Ecouteur;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.DIMANCHE;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.JEUDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.SAMEDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.VENDREDI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Résolution réelle des améliorations du module : jours choisis, créneau préféré, maintenance des générateurs,
 * contraintes de personnel (repos, temps partiel, compétences) et modèle conjoint.
 */
class TimefoldAmeliorationsTest {

    private static final LocalDate SEMAINE = OptimisationFixture.DIMANCHE;
    private final TimefoldOptimiseurAdapter adapter = new TimefoldOptimiseurAdapter(1);

    @AfterEach
    void arreter() {
        adapter.destroy();
    }

    private static ParametresOptimisation params(PerimetreOptimisation perimetre, int stabilite) {
        return new ParametresOptimisation(perimetre, SEMAINE, 1, 2, stabilite, ObjectifInfirmiers.EQUITE, 2, 6);
    }

    private ResultatOptimisation resoudre(DonneesOptimisation donnees, ParametresOptimisation parametres) throws Exception {
        CompletableFuture<ResultatOptimisation> futur = new CompletableFuture<>();
        adapter.demarrer(UUID.randomUUID(), donnees, parametres, new Ecouteur() {
            @Override
            public void progression(String phase, String score) {
            }

            @Override
            public void termine(ResultatOptimisation resultat, String score) {
                futur.complete(resultat);
            }

            @Override
            public void echec(String message) {
                futur.completeExceptionally(new IllegalStateException(message));
            }
        });
        return futur.get(60, TimeUnit.SECONDS);
    }

    @Test
    void should_choose_well_spaced_days_for_a_new_patient() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        f.salle("Salle A", 2);
        f.creneau("Matin");
        PatientAPlacer nouveau = f.patientAChoisir("Nouveau", 3);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.PATIENTS, 5));

        DeplacementPatient d = resultat.deplacements().getFirst();
        assertThat(d.patientId()).isEqualTo(nouveau.patientId());
        assertThat(d.de()).isNull();
        assertThat(d.jours()).hasSize(3);
        List<JourSemaine> jours = d.jours().stream().sorted().toList();
        for (int i = 0; i < jours.size(); i++) {
            assertThat(jours.get(i).ecartAvec(jours.get((i + 1) % jours.size())))
                    .as("deux séances ne se suivent jamais").isGreaterThanOrEqualTo(2);
        }
        assertThat(resultat.nonPlaces()).isEmpty();
    }

    @Test
    void should_fit_the_chosen_days_into_the_free_generator_days() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        f.ouverts = Set.of(DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI, SAMEDI);
        SalleRef a = f.salle("Salle A", 1);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Habitue", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI, VENDREDI);
        f.patientAChoisir("Nouveau", 3);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.PATIENTS, 5));

        DeplacementPatient d = resultat.deplacements().stream().filter(x -> x.nom().equals("Nouveau")).findFirst()
                .orElseThrow();
        assertThat(d.jours()).doesNotContain(LUNDI, MERCREDI, VENDREDI).hasSize(3);
        assertThat(resultat.deplacements()).noneMatch(x -> x.nom().equals("Habitue"));
    }

    @Test
    void should_move_a_patient_to_the_preferred_slot_when_it_costs_nothing() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        CreneauRef soir = f.creneau("Soir");
        PatientAPlacer p = f.patient("P", false, a, matin, f.generateur(a, 0), LUNDI, JEUDI);
        f.remplacer(p, new PatientAPlacer(p.patientId(), p.nom(), p.jours(), false, p.actuelle(), null, null, soir.id(),
                null, Set.of(), Set.of()));

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.PATIENTS, 1));

        assertThat(resultat.deplacements()).singleElement()
                .satisfies(d -> assertThat(d.vers().creneauId()).isEqualTo(soir.id()));
    }

    @Test
    void should_propose_a_temporary_move_when_a_generator_is_under_maintenance() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        GenerateurRef g1 = f.generateur(a, 0);
        f.patient("P", false, a, matin, g1, LUNDI, JEUDI);
        LocalDate lundi = SEMAINE.plusDays(1);
        f.maintenance(g1, lundi, lundi);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.MAINTENANCE, 5));

        DeplacementTemporairePropose t = resultat.temporaires().getFirst();
        assertThat(resultat.temporaires()).hasSize(1);
        assertThat(t.date()).isEqualTo(lundi);
        assertThat(t.vers().generateurId()).isEqualTo(f.generateur(a, 1).id());
        assertThat(t.vers().creneauId()).as("même créneau").isEqualTo(matin.id());
        assertThat(t.motif()).isEqualTo("Révision");
        assertThat(resultat.deplacements()).as("la place habituelle ne change pas").isEmpty();
        assertThat(resultat.seancesSansSolution()).isEmpty();
    }

    @Test
    void should_report_the_sessions_that_cannot_be_moved() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        GenerateurRef g1 = f.generateur(a, 0);
        f.patient("P1", false, a, matin, g1, LUNDI);
        f.patient("P2", false, a, matin, f.generateur(a, 1), LUNDI);
        LocalDate lundi = SEMAINE.plusDays(1);
        f.maintenance(g1, lundi, lundi);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.MAINTENANCE, 5));

        assertThat(resultat.temporaires()).isEmpty();
        assertThat(resultat.seancesSansSolution()).singleElement()
                .satisfies(s -> assertThat(s.nom()).isEqualTo("P1"));
    }

    @Test
    void should_give_the_pediatric_patient_a_nurse_with_the_skill() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        PatientAPlacer enfant = f.patient("Enfant", false, a, matin, f.generateur(a, 0), LUNDI);
        f.remplacer(enfant, new PatientAPlacer(enfant.patientId(), enfant.nom(), enfant.jours(), false,
                enfant.actuelle(), null, null, null, null, Set.of(), Set.of(CompetenceInfirmier.PEDIATRIE)));
        f.infirmier("Marie");
        InfirmierRef paul = f.infirmier("Paul");
        f.profil(paul, 100, CompetenceInfirmier.PEDIATRIE);

        for (PerimetreOptimisation perimetre : List.of(PerimetreOptimisation.ROULEMENT, PerimetreOptimisation.COMPLET)) {
            ResultatOptimisation resultat = resoudre(f.build(), params(perimetre, 0));

            assertThat(resultat.vacations()).as(perimetre.name()).singleElement()
                    .satisfies(v -> assertThat(v.infirmierId()).isEqualTo(paul.id()));
        }
    }

    @Test
    void should_keep_a_part_time_nurse_within_her_hours() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("P", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI, VENDREDI);
        InfirmierRef partiel = f.infirmier("Partiel");
        f.infirmier("Plein");
        f.profil(partiel, 20);
        // 40 h × 20 % = 8 h : une seule vacation de 5 h
        ParametresOptimisation p = new ParametresOptimisation(PerimetreOptimisation.ROULEMENT, SEMAINE, 1, 2, 0,
                ObjectifInfirmiers.EQUITE, 2, 6, 5, 40, 1);

        ResultatOptimisation resultat = resoudre(f.build(), p);

        assertThat(resultat.vacations()).hasSize(3);
        assertThat(resultat.vacations()).filteredOn(v -> v.infirmierId().equals(partiel.id())).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void should_grant_the_weekly_rest_even_when_saving_staff() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("P", false, a, matin, f.generateur(a, 0), DIMANCHE, LUNDI, MARDI, MERCREDI, JEUDI, VENDREDI);
        f.infirmier("Marie");
        f.infirmier("Paul");
        f.infirmier("Lina");
        // 5 jours de repos exigés : 2 jours travaillés au plus
        ParametresOptimisation p = new ParametresOptimisation(PerimetreOptimisation.ROULEMENT, SEMAINE, 1, 3, 0,
                ObjectifInfirmiers.ECONOMIE, 2, 6, 5, 60, 5);

        ResultatOptimisation resultat = resoudre(f.build(), p);

        Map<UUID, Set<LocalDate>> jours = new HashMap<>();
        for (VacationPlanifiee v : resultat.vacations()) {
            jours.computeIfAbsent(v.infirmierId(), k -> new HashSet<>()).add(v.date());
        }
        assertThat(resultat.vacations()).hasSize(6);
        assertThat(jours.values()).allSatisfy(d -> assertThat(d).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    void should_plan_patients_and_nurses_together_for_the_complete_scope() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        SalleRef b = f.salle("Salle B", 4);
        CreneauRef matin = f.creneau("Matin");
        f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        f.patient("P2", false, b, matin, f.generateur(b, 0), LUNDI);
        f.patientAChoisir("Nouveau", 1);
        InfirmierRef marie = f.infirmier("Marie");
        f.affecter(marie, a, matin, LUNDI);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.COMPLET, 1));

        // tous les patients dans une seule case, tenue par la seule infirmière : rien de non pourvu
        assertThat(resultat.nonPlaces()).isEmpty();
        assertThat(resultat.manques()).isEmpty();
        assertThat(resultat.vacations()).singleElement()
                .satisfies(v -> assertThat(v.infirmierId()).isEqualTo(marie.id()));
        assertThat(resultat.apres().vacationsRequises()).isEqualTo(1);
    }
}
