package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ContraintesDesPatients.Contraintes;
import com.hemodialyse.backend.contexts.planification.doubles.OptimisationEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.IndisponibiliteDatee;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RatioDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.SejourDuPatient;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDOptimisation.LMV;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.HORS_SERVICE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.SOIR;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture de l'état du centre soumis à l'optimisation : patients placés ou en attente, séjour, fermetures de l'horizon seulement,
 * préférences, profils, contraintes, séances réalisées. Aucune seconde règle de présence : celle de la lecture (P4).
 */
class LireLesDonneesDeLOptimisationTest {

    private static final LocalDate DU = LocalDate.of(2026, 11, 1);
    private static final LocalDate AU = LocalDate.of(2026, 11, 14);

    private final List<PatientDeLaSemaine> sejours = new ArrayList<>();
    private final Map<PatientId, PreferenceDePlanification> preferences = new HashMap<>();
    private final Map<PatientId, Contraintes> contraintes = new HashMap<>();
    private final Map<InfirmierId, ProfilDePlanification> profils = new HashMap<>();
    private final List<IndisponibiliteDatee> indisponibilites = new ArrayList<>();
    private final List<SeanceRealisee> seances = new ArrayList<>();
    private final OptimisationEnMemoire opt = new OptimisationEnMemoire(new UniteDeTravail() {
        @Override
        public <T> T executer(java.util.function.Supplier<T> action) {
            return action.get();
        }
    });
    private final RessourcesDuCentre ressources = com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.ressources(
            com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.OUVERTS,
            List.of(new RessourcesDuCentre.Fermeture(DU.plusDays(3), "Férié"), new RessourcesDuCentre.Fermeture(AU.plusDays(10), "Loin")));

    private static Occupation place(int n, Set<JourDeLaSemaine> jours, boolean salleEtCreneau) {
        return new Occupation(patient(n), salleEtCreneau ? A : null, salleEtCreneau ? MATIN : null, salleEtCreneau ? A1 : null, jours,
                CategorieDIsolement.SANS_RISQUE);
    }

    private DonneesDOptimisation lire() {
        PreferencesRepository prefs = new PreferencesRepository() {
            @Override
            public Map<PatientId, PreferenceDePlanification> duCentre(CentreId centre) {
                return preferences;
            }

            @Override
            public void enregistrer(CentreId centre, PreferenceDePlanification preference) {
                throw new UnsupportedOperationException();
            }
        };
        ProfilsRepository profs = new ProfilsRepository() {
            @Override
            public Map<InfirmierId, ProfilDePlanification> duCentre(CentreId centre) {
                return profils;
            }

            @Override
            public void enregistrer(CentreId centre, ProfilDePlanification profil) {
                throw new UnsupportedOperationException();
            }
        };
        return new LireLesDonneesDeLOptimisation((centre, du) -> ressources,
                (centre, du, au) -> new Personnel(List.of(), List.of(), List.of(), List.of(), new RatioDeLaPresence(4, false)),
                c -> sejours, prefs, profs, (centre, jour) -> contraintes, (centre, du, au) -> indisponibilites, opt.deplacements,
                (centre, du, au) -> seances).charger(CENTRE, DU, AU);
    }

    private void ajouterPatient(int n, String nom, Occupation place, SejourDuPatient sejour) {
        sejours.add(new PatientDeLaSemaine(patient(n), nom, place, sejour));
    }

    @Test
    void should_read_a_placed_patient_with_the_code_of_its_generator_and_its_stay() {
        ajouterPatient(1, "Anne Alpha", place(1, LMV, true), new SejourDuPatient(DU.minusDays(30), null, false, false));

        PatientAPlacer p = lire().patients().getFirst();

        assertThat(p.patient()).isEqualTo(patient(1));
        assertThat(p.nom()).isEqualTo("Anne Alpha");
        assertThat(p.jours()).isEqualTo(LMV);
        assertThat(p.actuelle().salle()).isEqualTo(A);
        assertThat(p.actuelle().generateurCode()).isEqualTo("G-A1");
        assertThat(p.premierJour()).isEqualTo(DU.minusDays(30));
        assertThat(p.dernierJour()).isNull();
        assertThat(p.aRisque()).isFalse();
    }

    @Test
    void should_keep_a_sleeping_patient_who_keeps_its_place_but_drop_one_whose_place_is_already_freed() {
        ajouterPatient(1, "Endormi", place(1, LMV, true), new SejourDuPatient(null, null, false, true));
        ajouterPatient(2, "Parti", place(2, LMV, true), new SejourDuPatient(null, DU.minusDays(1), true, false));
        ajouterPatient(3, "Sorti sans date", place(3, LMV, false), new SejourDuPatient(null, LocalDate.MIN, false, false));

        assertThat(lire().patients()).extracting(PatientAPlacer::nom).containsExactly("Endormi");
    }

    @Test
    void should_keep_a_patient_whose_last_day_is_the_first_day_of_the_horizon() {
        ajouterPatient(1, "Dernier jour", place(1, LMV, true), new SejourDuPatient(null, DU, false, false));
        assertThat(lire().patients()).hasSize(1);
        assertThat(lire().patients().getFirst().dernierJour()).isEqualTo(DU);
    }

    @Test
    void should_drop_a_placed_patient_without_days() {
        ajouterPatient(1, "Sans jours", place(1, Set.of(), true), SejourDuPatient.SANS_LIMITE);
        assertThat(lire().patients()).isEmpty();
    }

    @Test
    void should_read_a_waiting_patient_only_with_prescribed_days_or_days_to_choose() {
        ajouterPatient(1, "Attend avec jours", place(1, LMV, false), SejourDuPatient.SANS_LIMITE);
        ajouterPatient(2, "Attend sans rien", place(2, Set.of(), false), SejourDuPatient.SANS_LIMITE);
        ajouterPatient(3, "Jours a choisir", place(3, Set.of(), false), SejourDuPatient.SANS_LIMITE);
        preferences.put(patient(3), new PreferenceDePlanification(patient(3), SOIR, 3, true));

        List<PatientAPlacer> lus = lire().patients();

        assertThat(lus).extracting(PatientAPlacer::nom).containsExactly("Attend avec jours", "Jours a choisir");
        assertThat(lus.getFirst().actuelle()).isNull();
        assertThat(lus.getFirst().joursAChoisir()).isFalse();
        assertThat(lus.get(1).joursAChoisir()).isTrue();
        assertThat(lus.get(1).seancesAChoisir()).isEqualTo(3);
        assertThat(lus.get(1).creneauPrefere()).isEqualTo(SOIR);
    }

    @Test
    void should_keep_a_partial_current_place_of_a_waiting_patient_without_a_generator_code() {
        sejours.add(new PatientDeLaSemaine(patient(1), "Partiel", new Occupation(patient(1), A, null, A1, LMV,
                CategorieDIsolement.A_RISQUE), SejourDuPatient.SANS_LIMITE));

        PatientAPlacer p = lire().patients().getFirst();

        assertThat(p.actuelle().salle()).isEqualTo(A);
        assertThat(p.actuelle().creneau()).isNull();
        assertThat(p.actuelle().generateurCode()).isNull();
        assertThat(p.aRisque()).isTrue();
    }

    @Test
    void should_ignore_the_choice_of_days_when_the_preference_does_not_ask_for_it() {
        ajouterPatient(1, "Avec creneau prefere", place(1, LMV, true), SejourDuPatient.SANS_LIMITE);
        preferences.put(patient(1), new PreferenceDePlanification(patient(1), SOIR, 3, false));

        PatientAPlacer p = lire().patients().getFirst();

        assertThat(p.creneauPrefere()).isEqualTo(SOIR);
        assertThat(p.seancesAChoisir()).isNull();
    }

    @Test
    void should_sort_the_patients_by_name_then_by_identifier() {
        ajouterPatient(2, "Bob", place(2, LMV, true), SejourDuPatient.SANS_LIMITE);
        ajouterPatient(1, "Anne", place(1, LMV, true), SejourDuPatient.SANS_LIMITE);
        ajouterPatient(4, "Bob", place(4, LMV, true), SejourDuPatient.SANS_LIMITE);

        assertThat(lire().patients()).extracting(PatientAPlacer::patient).containsExactly(patient(1), patient(2), patient(4));
    }

    @Test
    void should_carry_the_transporters_and_the_required_skills() {
        ajouterPatient(1, "Anne", place(1, LMV, true), SejourDuPatient.SANS_LIMITE);
        UUID transporteur = UUID.randomUUID();
        contraintes.put(patient(1), new Contraintes(Set.of(transporteur), EnumSet.of(Competence.PEDIATRIE, Competence.CATHETER)));

        PatientAPlacer p = lire().patients().getFirst();

        assertThat(p.transporteurs()).containsExactly(transporteur);
        assertThat(p.competencesRequises()).containsExactlyInAnyOrder(Competence.PEDIATRIE, Competence.CATHETER);
    }

    @Test
    void should_keep_only_the_closures_of_the_horizon() {
        assertThat(lire().ressources().fermetures()).singleElement().satisfies(f -> {
            assertThat(f.date()).isEqualTo(DU.plusDays(3));
            assertThat(f.motif()).isEqualTo("Férié");
        });
    }

    @Test
    void should_read_the_in_service_generators_the_profiles_the_unavailabilities_the_moves_and_the_sessions() {
        InfirmierId infirmier = new InfirmierId(UUID.randomUUID());
        profils.put(infirmier, new ProfilDePlanification(infirmier, 50, Set.of(Competence.CATHETER)));
        indisponibilites.add(new IndisponibiliteDatee(A1, DU, DU.plusDays(1), "PREVENTIVE"));
        seances.add(new SeanceRealisee(patient(1), "Anne", DU, A, MATIN, A1, false));
        opt.deplacements.enregistrer(List.of(com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire.reconstituer(
                new com.hemodialyse.backend.contexts.planification.domain.Identifiants.DeplacementTemporaireId(UUID.randomUUID()), CENTRE,
                patient(1), DU.plusDays(2), A, MATIN, A1, null)));
        preferences.put(patient(1), PreferenceDePlanification.aucune(patient(1)));

        DonneesDOptimisation donnees = lire();

        assertThat(donnees.ressources().generateurs()).extracting(g -> g.id()).doesNotContain(HORS_SERVICE);
        assertThat(donnees.profils()).containsKey(infirmier.valeur());
        assertThat(donnees.profil(infirmier.valeur()).tauxActivite()).isEqualTo(50);
        assertThat(donnees.indisponibilites()).hasSize(1);
        assertThat(donnees.temporaires()).hasSize(1);
        assertThat(donnees.seances()).hasSize(1);
        assertThat(donnees.preferences()).hasSize(1);
        assertThat(donnees.personnel().ratio().patientsParInfirmier()).isEqualTo(4);
    }
}
