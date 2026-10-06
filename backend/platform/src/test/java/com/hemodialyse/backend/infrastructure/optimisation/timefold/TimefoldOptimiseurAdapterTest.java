package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.CauseNonPlace;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.DeplacementPatient;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimiseurPlanningPort.Ecouteur;
import com.hemodialyse.backend.domain.planning.optimisation.service.VerificationDeplacementsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.VENDREDI;
import static com.hemodialyse.backend.infrastructure.optimisation.timefold.OptimisationFixture.DIMANCHE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Résolution réelle avec Timefold sur de petits centres : le solveur doit trouver les économies évidentes, ne jamais
 * enfreindre les règles de la planification et respecter les absences et l'isolement.
 */
class TimefoldOptimiseurAdapterTest {

    private final TimefoldOptimiseurAdapter adapter = new TimefoldOptimiseurAdapter(1);

    @AfterEach
    void arreter() {
        adapter.destroy();
    }

    private static ParametresOptimisation params(PerimetreOptimisation perimetre, int stabilite) {
        return new ParametresOptimisation(perimetre, DIMANCHE, 1, 2, stabilite, ObjectifInfirmiers.EQUITE, 2, 6);
    }

    @Test
    void should_round_unimproved_termination_limit_down_to_whole_seconds() {
        var terminaison = TimefoldOptimiseurAdapter.terminaison(Duration.ofSeconds(20));

        assertThat(terminaison.getUnimprovedSpentLimit()).isEqualTo(Duration.ofSeconds(6));
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

    /**
     * Applique les déplacements et vérifie qu'aucun générateur n'est utilisé deux fois le même jour au même créneau.
     */
    private static void assertSansConflit(DonneesOptimisation donnees, ResultatOptimisation resultat) {
        assertThat(VerificationDeplacementsService.verifier(donnees, resultat.deplacements())).isEmpty();
        Map<UUID, Poste> finaux = new HashMap<>(donnees.placementsActuels());
        resultat.deplacements().forEach(d -> finaux.put(d.patientId(), d.vers()));
        Set<String> occupes = new HashSet<>();
        for (PatientAPlacer p : donnees.patients()) {
            Poste poste = finaux.get(p.patientId());
            if (poste == null) continue;
            p.jours().forEach(jour -> assertThat(occupes.add(poste.generateurId() + "|" + poste.creneauId() + "|" + jour))
                    .as("générateur %s déjà pris %s", poste.generateurCode(), jour).isTrue());
        }
    }

    private OptimisationFixture deuxSallesDeuxPatientsChacune(int jours) {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        SalleRef b = f.salle("Salle B", 4);
        CreneauRef matin = f.creneau("Matin");
        var j = jours == 1 ? new com.hemodialyse.backend.domain.planning.model.JourSemaine[]{LUNDI}
                : new com.hemodialyse.backend.domain.planning.model.JourSemaine[]{LUNDI, MERCREDI, VENDREDI};
        f.patient("P1", false, a, matin, f.generateur(a, 0), j);
        f.patient("P2", false, a, matin, f.generateur(a, 1), j);
        f.patient("P3", false, b, matin, f.generateur(b, 0), j);
        f.patient("P4", false, b, matin, f.generateur(b, 1), j);
        return f;
    }

    @Test
    void should_pack_the_patients_in_one_room_to_save_nurse_vacations() throws Exception {
        DonneesOptimisation donnees = deuxSallesDeuxPatientsChacune(3).build();

        ResultatOptimisation resultat = resoudre(donnees, params(PerimetreOptimisation.PATIENTS, 1));

        assertThat(resultat.avant().vacationsRequises()).isEqualTo(6);
        assertThat(resultat.apres().vacationsRequises()).isEqualTo(3);
        assertThat(resultat.apres().sallesOuvertes()).isEqualTo(3);
        assertThat(resultat.apres().patientsNonPlaces()).isZero();
        assertThat(resultat.deplacements()).hasSize(2);
        assertSansConflit(donnees, resultat);
    }

    @Test
    void should_leave_everybody_in_place_when_stability_outweighs_the_savings() throws Exception {
        DonneesOptimisation donnees = deuxSallesDeuxPatientsChacune(1).build();

        ResultatOptimisation resultat = resoudre(donnees, params(PerimetreOptimisation.PATIENTS, 10));

        assertThat(resultat.deplacements()).isEmpty();
        assertThat(resultat.apres()).isEqualTo(resultat.avant());
    }

    @Test
    void should_place_a_waiting_patient_on_a_free_generator() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Placé", false, a, matin, f.generateur(a, 0), LUNDI, MERCREDI);
        PatientAPlacer attente = f.patientNonPlace("En attente", false, LUNDI, MERCREDI);
        DonneesOptimisation donnees = f.build();

        ResultatOptimisation resultat = resoudre(donnees, params(PerimetreOptimisation.PATIENTS, 5));

        assertThat(resultat.nonPlaces()).isEmpty();
        DeplacementPatient placement = resultat.deplacements().stream()
                .filter(d -> d.patientId().equals(attente.patientId())).findFirst().orElseThrow();
        assertThat(placement.de()).isNull();
        assertThat(placement.vers().generateurId()).isNotNull();
        assertSansConflit(donnees, resultat);
    }

    @Test
    void should_report_patients_that_cannot_be_placed_because_the_center_is_full() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 1);
        CreneauRef matin = f.creneau("Matin");
        f.patient("Placé", false, a, matin, f.generateur(a, 0), LUNDI);
        PatientAPlacer trop = f.patientNonPlace("De trop", false, LUNDI);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.PATIENTS, 5));

        assertThat(resultat.nonPlaces()).singleElement().satisfies(n -> {
            assertThat(n.patientId()).isEqualTo(trop.patientId());
            assertThat(n.cause()).isEqualTo(CauseNonPlace.AUCUNE_PLACE);
        });
        assertThat(resultat.apres().patientsNonPlaces()).isEqualTo(1);
    }

    @Test
    void should_only_place_risk_patients_in_the_isolation_room() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        SalleRef iso = f.salleIsolement("Salle I", 2);
        CreneauRef matin = f.creneau("Matin");
        PatientAPlacer risque = f.patient("Risque", true, a, matin, f.generateur(a, 0), MARDI);
        PatientAPlacer sain = f.patient("Sain", false, iso, matin, f.generateur(iso, 0), MARDI);
        DonneesOptimisation donnees = f.build();

        ResultatOptimisation resultat = resoudre(donnees, params(PerimetreOptimisation.PATIENTS, 0));

        Map<UUID, Poste> vers = new HashMap<>();
        resultat.deplacements().forEach(d -> vers.put(d.patientId(), d.vers()));
        assertThat(vers.get(risque.patientId()).salleId()).isEqualTo(iso.id());
        assertThat(vers.get(sain.patientId()).salleId()).isEqualTo(a.id());
        assertSansConflit(donnees, resultat);
    }

    @Test
    void should_flag_a_risk_patient_when_no_isolation_room_exists() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patientNonPlace("Risque", true, LUNDI);
        f.patient("Autre", false, a, matin, f.generateur(a, 0), LUNDI);

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.PATIENTS, 5));

        assertThat(resultat.nonPlaces()).singleElement()
                .satisfies(n -> assertThat(n.cause()).isEqualTo(CauseNonPlace.ISOLEMENT_IMPOSSIBLE));
    }

    @Test
    void should_design_a_rotation_that_fills_every_required_vacation() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 8);
        SalleRef iso = f.salleIsolement("Salle I", 1);
        CreneauRef matin = f.creneau("Matin");
        // 8 patients le lundi en salle A = 2 infirmiers (ratio 4) ; 1 patient à risque en isolement = 1 infirmier habilité
        for (int i = 0; i < 8; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i), LUNDI);
        f.patient("Risque", true, iso, matin, f.generateur(iso, 0), LUNDI);
        InfirmierRef habilite = f.infirmier("Habilitée", QualificationInfirmier.INFIRMIER, true);
        f.infirmier("Marie");
        f.infirmier("Paul");
        DonneesOptimisation donnees = f.build();

        ResultatOptimisation resultat = resoudre(donnees, params(PerimetreOptimisation.ROULEMENT, 5));

        assertThat(resultat.manques()).isEmpty();
        assertThat(resultat.vacations()).hasSize(3);
        assertThat(resultat.vacations().stream().filter(v -> v.salleId().equals(iso.id())).toList())
                .singleElement().satisfies(v -> assertThat(v.infirmierId()).isEqualTo(habilite.id()));
        assertThat(resultat.vacations().stream().map(VacationPlanifiee::infirmierId).distinct()).hasSize(3);
        assertThat(resultat.avant().vacationsNonPourvues()).isEqualTo(3);
        assertThat(resultat.apres().vacationsNonPourvues()).isZero();
    }

    @Test
    void should_report_the_vacations_it_cannot_fill_when_the_staff_is_too_small() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 8);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 8; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i), LUNDI);
        f.infirmier("Seule");

        ResultatOptimisation resultat = resoudre(f.build(), params(PerimetreOptimisation.ROULEMENT, 5));

        assertThat(resultat.vacations()).hasSize(1);
        assertThat(resultat.manques()).singleElement().satisfies(m -> {
            assertThat(m.manque()).isEqualTo(1);
            assertThat(m.jour()).isEqualTo(LUNDI);
        });
    }

    @Test
    void should_cover_the_gap_left_by_an_absent_nurse_without_moving_the_others() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 4);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 4; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i), LUNDI);
        InfirmierRef marie = f.infirmier("Marie");
        InfirmierRef paul = f.infirmier("Paul");
        InfirmierRef lea = f.infirmier("Léa");
        f.affecter(marie, a, matin, LUNDI);
        f.absence(marie, DIMANCHE, DIMANCHE.plusDays(6));
        DonneesOptimisation donnees = f.build();

        ParametresOptimisation parametres = new ParametresOptimisation(PerimetreOptimisation.COUVERTURE, DIMANCHE, 1, 2,
                5, ObjectifInfirmiers.EQUITE, 2, 6);
        ResultatOptimisation resultat = resoudre(donnees, parametres);

        assertThat(resultat.manques()).isEmpty();
        assertThat(resultat.vacations()).singleElement().satisfies(v -> {
            assertThat(v.infirmierId()).isIn(paul.id(), lea.id()).isNotEqualTo(marie.id());
            assertThat(v.existante()).isFalse();
        });
        assertThat(resultat.avant().vacationsNonPourvues()).isEqualTo(1);
        assertThat(resultat.apres().vacationsNonPourvues()).isZero();
    }

    @Test
    void should_keep_the_nurses_already_planned_and_only_fill_the_missing_ones_on_several_weeks() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 8);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 8; i++) f.patient("P" + i, false, a, matin, f.generateur(a, i), LUNDI);
        InfirmierRef marie = f.infirmier("Marie");
        InfirmierRef paul = f.infirmier("Paul");
        f.infirmier("Léa");
        f.affecter(marie, a, matin, LUNDI);
        f.affecter(paul, a, matin, LUNDI);
        f.absence(paul, DIMANCHE.plusWeeks(1), DIMANCHE.plusWeeks(1).plusDays(6));
        DonneesOptimisation donnees = f.build();

        ParametresOptimisation parametres = new ParametresOptimisation(PerimetreOptimisation.COUVERTURE, DIMANCHE, 2, 2,
                5, ObjectifInfirmiers.EQUITE, 2, 6);
        ResultatOptimisation resultat = resoudre(donnees, parametres);

        LocalDate lundi2 = DIMANCHE.plusWeeks(1).plusDays(1);
        List<VacationPlanifiee> semaine2 = resultat.vacations().stream().filter(v -> v.date().equals(lundi2)).toList();
        assertThat(semaine2).extracting(VacationPlanifiee::existante).containsExactlyInAnyOrder(true, false);
        assertThat(resultat.vacations().stream().filter(v -> v.date().equals(DIMANCHE.plusDays(1))))
                .hasSize(2).allMatch(VacationPlanifiee::existante);
        assertThat(resultat.manques()).isEmpty();
    }

    @Test
    void should_use_fewer_nurses_with_the_economy_objective() throws Exception {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 8);
        CreneauRef matin = f.creneau("Matin");
        for (int i = 0; i < 4; i++) f.patient("L" + i, false, a, matin, f.generateur(a, i), LUNDI);
        for (int i = 4; i < 8; i++) f.patient("M" + i, false, a, matin, f.generateur(a, i), MARDI);
        for (int i = 0; i < 4; i++) f.infirmier("I" + i);
        DonneesOptimisation donnees = f.build();

        ResultatOptimisation equite = resoudre(donnees, new ParametresOptimisation(PerimetreOptimisation.ROULEMENT,
                DIMANCHE, 1, 2, 0, ObjectifInfirmiers.EQUITE, 2, 6));
        ResultatOptimisation economie = resoudre(donnees, new ParametresOptimisation(PerimetreOptimisation.ROULEMENT,
                DIMANCHE, 1, 2, 0, ObjectifInfirmiers.ECONOMIE, 2, 6));

        assertThat(equite.apres().infirmiersMobilises()).isEqualTo(2);
        assertThat(economie.apres().infirmiersMobilises()).isEqualTo(1);
        assertThat(economie.manques()).isEmpty();
    }

    @Test
    void should_chain_patients_and_rotation_for_the_complete_scope() throws Exception {
        OptimisationFixture f = deuxSallesDeuxPatientsChacune(3);
        f.infirmier("Marie");
        f.infirmier("Paul");
        DonneesOptimisation donnees = f.build();

        ResultatOptimisation resultat = resoudre(donnees, params(PerimetreOptimisation.COMPLET, 1));

        assertThat(resultat.deplacements()).hasSize(2);
        // 1 salle, 3 jours : 3 vacations, tenues par 2 infirmiers au plus
        assertThat(resultat.vacations()).hasSize(3);
        assertThat(resultat.manques()).isEmpty();
        assertThat(resultat.apres().vacationsRequises()).isLessThan(resultat.avant().vacationsRequises());
    }

    @Test
    void should_stop_early_and_still_deliver_a_complete_proposal() throws Exception {
        DonneesOptimisation donnees = deuxSallesDeuxPatientsChacune(3).build();
        ParametresOptimisation longue = new ParametresOptimisation(PerimetreOptimisation.PATIENTS, DIMANCHE, 1, 120, 0,
                ObjectifInfirmiers.EQUITE, 2, 6);
        CompletableFuture<ResultatOptimisation> futur = new CompletableFuture<>();
        UUID run = UUID.randomUUID();
        List<String> phases = new ArrayList<>();
        adapter.demarrer(run, donnees, longue, new Ecouteur() {
            @Override
            public void progression(String phase, String score) {
                phases.add(phase);
                adapter.arreter(run);
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

        ResultatOptimisation resultat = futur.get(30, TimeUnit.SECONDS);

        assertThat(phases).isNotEmpty();
        assertThat(resultat.apres().patientsNonPlaces()).isZero();
        assertThat(adapter.arreter(run)).as("plus rien à arrêter une fois terminé").isFalse();
    }

    @Test
    void should_report_a_failure_instead_of_throwing_when_the_data_is_inconsistent() {
        DonneesOptimisation donnees = null;
        CompletableFuture<String> echec = new CompletableFuture<>();
        adapter.demarrer(UUID.randomUUID(), donnees, params(PerimetreOptimisation.PATIENTS, 5), new Ecouteur() {
            @Override
            public void progression(String phase, String score) {
            }

            @Override
            public void termine(ResultatOptimisation resultat, String score) {
                echec.complete("terminé");
            }

            @Override
            public void echec(String message) {
                echec.complete("échec");
            }
        });

        assertThat(echec.orTimeout(10, TimeUnit.SECONDS).join()).isEqualTo("échec");
    }
}
