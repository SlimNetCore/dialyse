package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LUNDI;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.infirmier;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.poste;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A2;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.B1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P22 (empreinte : ce qui périme une proposition, KB-PLN-95, élargie aux séances et préférences) et P24 (indicateurs avant / après
 * recalculés par le domaine, avec la règle de présence de la lecture).
 */
class EmpreinteEtIndicateursDOptimisationTest {

    private static final ParametresDOptimisation PARAMETRES = JeuDOptimisation.parametres(Perimetre.PATIENTS);

    private static JeuDOptimisation base() {
        return new JeuDOptimisation()
                .patient(1, LMV, false, poste(A, MATIN, A1))
                .patient(2, LMV, false, poste(A, MATIN, A2))
                .infirmierActif(1, false)
                .affecte(1, A, MATIN, LMV);
    }

    private static Empreinte empreinte(JeuDOptimisation jeu) {
        return Empreinte.calculer(jeu.donnees(), PARAMETRES);
    }

    private static void changePar(Consumer<JeuDOptimisation> changement) {
        JeuDOptimisation jeu = base();
        Empreinte avant = empreinte(jeu);
        changement.accept(jeu);
        assertThat(empreinte(jeu)).isNotEqualTo(avant);
    }

    @Test
    void should_be_stable_for_the_same_state() {
        assertThat(empreinte(base())).isEqualTo(empreinte(base()));
        assertThat(empreinte(base()).valeur()).hasSize(64);
    }

    @Test
    void should_ignore_the_names_of_patients_and_nurses() {
        JeuDOptimisation jeu = base();
        Empreinte avant = empreinte(jeu);
        jeu.patients.replaceAll(p -> new DonneesDOptimisation.PatientAPlacer(p.patient(), "Autre nom", p.jours(), p.aRisque(),
                p.actuelle(), p.premierJour(), p.dernierJour()));
        jeu.infirmiers.replaceAll(i -> new Presence.InfirmierDeLaPresence(i.id(), "Autre", "Autre", i.qualification(),
                i.habiliteIsolement()));
        assertThat(empreinte(jeu)).isEqualTo(avant);
    }

    @Test
    void should_change_when_a_patient_day_or_place_changes_or_a_patient_is_added() {
        changePar(j -> j.patients.replaceAll(p -> p.patient().equals(patient(1)) ? p.avecJours(EnumSet.of(JourDeLaSemaine.MARDI)) : p));
        changePar(j -> j.patients.replaceAll(p -> p.patient().equals(patient(1))
                ? new DonneesDOptimisation.PatientAPlacer(p.patient(), p.nom(), p.jours(), p.aRisque(), poste(A, SOIR, A1), null, null) : p));
        changePar(j -> j.patient(3, LMV, false, null));
    }

    @Test
    void should_change_when_the_resources_the_ratio_or_the_staff_change() {
        changePar(j -> j.ratio = 5);
        changePar(j -> j.ressources = JeuDePlaces.ressources(EnumSet.range(JourDeLaSemaine.LUNDI, JourDeLaSemaine.VENDREDI), List.of()));
        changePar(j -> j.ressources = JeuDePlaces.ressources(JeuDePlaces.OUVERTS,
                List.of(new RessourcesDuCentre.Fermeture(LUNDI.plusDays(1), "Férié"))));
        changePar(j -> j.infirmierActif(2, true));
        changePar(j -> j.absent(1, LUNDI, LUNDI));
        changePar(j -> j.affecte(1, A, SOIR, LMV));
    }

    @Test
    void should_change_when_a_maintenance_a_move_a_session_or_a_preference_is_added() {
        changePar(j -> j.indisponibilites.add(new DonneesDOptimisation.IndisponibiliteDatee(A1, LUNDI, LUNDI, "PREVENTIVE")));
        changePar(j -> j.temporaires.add(DeplacementTemporaire.reconstituer(new Identifiants.DeplacementTemporaireId(
                JeuDePlaces.id(500)), JeuDePlaces.CENTRE, patient(1), LUNDI, A, MATIN, A2, null)));
        changePar(j -> j.seances.add(new SemaineDuPlanning.SeanceRealisee(patient(1), "P", LUNDI, A, MATIN, A1, false)));
        changePar(j -> j.preferences.add(new PreferenceDePlanification(patient(1), SOIR, 3, true)));
    }

    @Test
    void should_change_with_the_perimeter_and_the_horizon() {
        JeuDOptimisation jeu = base();
        assertThat(Empreinte.calculer(jeu.donnees(), JeuDOptimisation.parametres(Perimetre.ROULEMENT)))
                .isNotEqualTo(Empreinte.calculer(jeu.donnees(), PARAMETRES));
        assertThat(Empreinte.calculer(jeu.donnees(), new ParametresDOptimisation(Perimetre.PATIENTS, LUNDI, 2, 20, 5, null, 2, 6)))
                .isNotEqualTo(Empreinte.calculer(jeu.donnees(), PARAMETRES));
    }

    @Test
    void should_refuse_an_empty_fingerprint() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Empreinte(" ")).isInstanceOf(SaisieInvalide.class);
    }

    // ── indicateurs ──

    @Test
    void should_measure_the_current_situation_from_the_patients_and_the_presence_rule() {
        Indicateurs avant = IndicateursDOptimisation.avant(base().donnees(), PARAMETRES);

        assertThat(avant.generateursUtilises()).isEqualTo(2);
        assertThat(avant.sallesOuvertes()).isEqualTo(3);
        assertThat(avant.vacationsRequises()).isEqualTo(3);
        assertThat(avant.placesInfirmierInutilisees()).isEqualTo(6);
        assertThat(avant.patientsNonPlaces()).isZero();
        assertThat(avant.infirmiersMobilises()).isEqualTo(1);
        assertThat(avant.vacationsNonPourvues()).isZero();
    }

    @Test
    void should_count_the_patients_without_a_complete_place_and_ignore_closed_days() {
        JeuDOptimisation jeu = base().patient(3, LMV, false, null).patient(4, EnumSet.of(JourDeLaSemaine.DIMANCHE), false, poste(A, SOIR, A1));
        Indicateurs avant = IndicateursDOptimisation.avant(jeu.donnees(), PARAMETRES);
        assertThat(avant.patientsNonPlaces()).isEqualTo(1);
        assertThat(avant.sallesOuvertes()).isEqualTo(3);
    }

    @Test
    void should_not_count_a_generator_that_is_not_in_service() {
        JeuDOptimisation jeu = new JeuDOptimisation().patient(1, LMV, false, poste(A, MATIN, JeuDePlaces.HORS_SERVICE));
        assertThat(IndicateursDOptimisation.avant(jeu.donnees(), PARAMETRES).generateursUtilises()).isZero();
    }

    @Test
    void should_measure_a_proposition_with_the_new_placements_and_the_proposed_vacations() {
        JeuDOptimisation jeu = base();
        Map<PatientId, Poste> placements = Map.of(patient(1), poste(A, MATIN, A1), patient(2), poste(A, MATIN, A1));
        List<VacationPlanifiee> vacations = List.of(
                new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, infirmier(1), "N1", true),
                new VacationPlanifiee(LUNDI.plusDays(2), JourDeLaSemaine.MERCREDI, A, MATIN, infirmier(1), "N1", true),
                new VacationPlanifiee(LUNDI.plusDays(4), JourDeLaSemaine.VENDREDI, A, MATIN, infirmier(2), "N2", false));

        Indicateurs apres = IndicateursDOptimisation.apres(jeu.donnees(), placements, PARAMETRES, vacations,
                List.of(new VacationNonPourvue(LUNDI.plusDays(1), JourDeLaSemaine.MARDI, A, MATIN, 2)));

        assertThat(apres.generateursUtilises()).isEqualTo(1);
        assertThat(apres.infirmiersMobilises()).isEqualTo(2);
        assertThat(apres.ecartCharge()).isEqualTo(1);
        assertThat(apres.vacationsNonPourvues()).isEqualTo(2);
        assertThat(apres.depassementsHebdo()).isZero();
    }

    @Test
    void should_count_the_weekly_overruns_beyond_the_maximum() {
        ParametresDOptimisation unSeulJour = new ParametresDOptimisation(Perimetre.ROULEMENT, LUNDI, 1, 20, 5, null, 2, 1);
        List<VacationPlanifiee> vacations = List.of(
                new VacationPlanifiee(LUNDI, JourDeLaSemaine.LUNDI, A, MATIN, infirmier(1), "N1", false),
                new VacationPlanifiee(LUNDI.plusDays(2), JourDeLaSemaine.MERCREDI, A, MATIN, infirmier(1), "N1", false),
                new VacationPlanifiee(LUNDI.plusDays(4), JourDeLaSemaine.VENDREDI, A, MATIN, infirmier(1), "N1", false));
        Indicateurs apres = IndicateursDOptimisation.apres(base().donnees(), Map.of(), unSeulJour, vacations, null);
        assertThat(apres.depassementsHebdo()).isEqualTo(2);
        assertThat(apres.vacationsNonPourvues()).isZero();
    }

    @Test
    void should_use_the_dated_data_for_dated_perimeters_and_the_typical_week_for_the_others() {
        JeuDOptimisation jeu = base().absent(1, LUNDI, LUNDI.plusDays(6));
        ParametresDOptimisation couverture = new ParametresDOptimisation(Perimetre.COUVERTURE, LUNDI, 1, 20, 5, null, 2, 6);
        assertThat(IndicateursDOptimisation.reference(jeu.donnees(), couverture).personnel().absences()).hasSize(1);
        assertThat(IndicateursDOptimisation.reference(jeu.donnees(), PARAMETRES).personnel().absences()).isEmpty();
        // la nurse absente tient moins de cases en couverture que dans la semaine type
        assertThat(IndicateursDOptimisation.avant(jeu.donnees(), couverture).vacationsNonPourvues())
                .isGreaterThan(IndicateursDOptimisation.avant(jeu.donnees(), PARAMETRES).vacationsNonPourvues());
    }

    @Test
    void should_report_a_unique_generator_when_both_patients_share_it_in_the_proposition_even_for_another_room() {
        JeuDOptimisation jeu = new JeuDOptimisation().patient(1, LMV, false, poste(A, MATIN, A1));
        Indicateurs apres = IndicateursDOptimisation.apres(jeu.donnees(), Map.of(patient(1), poste(JeuDePlaces.B, MATIN, B1)), PARAMETRES,
                null, null);
        assertThat(apres.generateursUtilises()).isEqualTo(1);
        assertThat(apres.sallesOuvertes()).isEqualTo(3);
    }
}
