package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des dépôts du paramétrage : exécuté contre les doubles en mémoire, l'adaptateur JDBC sur H2 et le même adaptateur sur
 * PostgreSQL 16 (ParametrageJdbcContrat). Le double passe le même jeu de tests que l'adaptateur réel.
 */
public abstract class ParametrageContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997801-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("99997801-0000-0000-0000-00000000000b"));
    protected static final SalleId SALLE_1 = new SalleId(UUID.fromString("99997801-0000-0000-0000-000000000011"));
    protected static final SalleId SALLE_2 = new SalleId(UUID.fromString("99997801-0000-0000-0000-000000000012"));
    protected static final SalleId SALLE_AUTRE = new SalleId(UUID.fromString("99997801-0000-0000-0000-000000000013"));
    private static final PatientId PATIENT_1 = new PatientId(UUID.fromString("99997801-0000-0000-0000-000000000031"));
    private static final PatientId PATIENT_2 = new PatientId(UUID.fromString("99997801-0000-0000-0000-000000000032"));
    private static final InfirmierId INFIRMIER_1 = new InfirmierId(UUID.fromString("99997801-0000-0000-0000-000000000041"));
    private static final CreneauId MATIN = new CreneauId(UUID.fromString("99997801-0000-0000-0000-000000000021"));

    protected abstract ParametresDuPlanningRepository parametres();

    protected abstract ReglagesRepository reglages();

    protected abstract PreferencesRepository preferences();

    protected abstract ProfilsRepository profils();

    /** Pose une salle d'Organisation (marquée ou non comme salle d'isolement). */
    protected abstract void salle(CentreId centre, SalleId salle, boolean isolement);

    private void sallesDeLaBase() {
        salle(CENTRE, SALLE_1, false);
        salle(CENTRE, SALLE_2, false);
        salle(AUTRE, SALLE_AUTRE, true);
    }

    // ------------------------------------------------------------------ paramètres

    @Test
    void should_read_the_default_settings_of_a_centre_that_never_saved_any() {
        sallesDeLaBase();

        ParametresDuPlanning lus = parametres().lire(CENTRE);

        assertThat(lus.joursOuverts()).containsExactlyElementsOf(EnumSet.allOf(JourDeLaSemaine.class));
        assertThat(lus.ratio().patientsParInfirmier()).isEqualTo(4);
        assertThat(lus.patientsParPoste().nombre()).isEqualTo(3);
        assertThat(lus.sallesIsolement()).isEmpty();
    }

    @Test
    void should_read_back_what_was_saved_with_its_isolation_rooms() {
        sallesDeLaBase();
        ParametresDuPlanning aEnregistrer = ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.SAMEDI, JourDeLaSemaine.LUNDI),
                Set.of(SALLE_2), 7, 2);

        parametres().enregistrer(CENTRE, aEnregistrer);

        assertThat(parametres().lire(CENTRE)).isEqualTo(aEnregistrer);
    }

    @Test
    void should_replace_the_previous_settings_and_the_previous_isolation_rooms() {
        sallesDeLaBase();
        parametres().enregistrer(CENTRE, ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.LUNDI), Set.of(SALLE_1), 5, 5));

        parametres().enregistrer(CENTRE, ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.MARDI), Set.of(SALLE_2), null, null));

        ParametresDuPlanning lus = parametres().lire(CENTRE);
        assertThat(lus.joursOuverts()).containsExactly(JourDeLaSemaine.MARDI);
        assertThat(lus.sallesIsolement()).containsExactly(SALLE_2);
        assertThat(lus.ratio().patientsParInfirmier()).isEqualTo(4);
        assertThat(lus.patientsParPoste().nombre()).isEqualTo(3);
    }

    @Test
    void should_keep_the_settings_and_the_isolation_rooms_of_each_centre_apart() {
        sallesDeLaBase();

        parametres().enregistrer(CENTRE, ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.LUNDI), Set.of(SALLE_1), 6, 6));

        ParametresDuPlanning autre = parametres().lire(AUTRE);
        assertThat(autre.ratio().patientsParInfirmier()).isEqualTo(4);
        assertThat(autre.sallesIsolement()).containsExactly(SALLE_AUTRE);
    }

    // ------------------------------------------------------------------ réglages

    @Test
    void should_read_the_default_settings_of_planning_and_list_no_night_replanning() {
        assertThat(reglages().lire(CENTRE)).isEqualTo(ReglagesDePlanification.parDefaut());
        assertThat(reglages().centresEnReplanificationNocturne()).doesNotContain(CENTRE);
    }

    @Test
    void should_read_back_the_planning_settings_replace_them_and_list_the_centres_with_night_replanning() {
        reglages().enregistrer(CENTRE, new ReglagesDePlanification(true, 6, 35, 2));
        reglages().enregistrer(AUTRE, new ReglagesDePlanification(false, 4, 30, 0));

        assertThat(reglages().lire(CENTRE)).isEqualTo(new ReglagesDePlanification(true, 6, 35, 2));
        assertThat(reglages().lire(AUTRE)).isEqualTo(new ReglagesDePlanification(false, 4, 30, 0));
        assertThat(reglages().centresEnReplanificationNocturne()).contains(CENTRE).doesNotContain(AUTRE);

        reglages().enregistrer(CENTRE, ReglagesDePlanification.parDefaut());
        assertThat(reglages().lire(CENTRE)).isEqualTo(ReglagesDePlanification.parDefaut());
        assertThat(reglages().centresEnReplanificationNocturne()).doesNotContain(CENTRE);
    }

    // ------------------------------------------------------------------ préférences

    @Test
    void should_read_back_replace_and_isolate_the_preferences_of_the_patients() {
        PreferenceDePlanification premiere = new PreferenceDePlanification(PATIENT_1, MATIN, 3, true);
        preferences().enregistrer(CENTRE, premiere);
        preferences().enregistrer(CENTRE, PreferenceDePlanification.aucune(PATIENT_2));
        preferences().enregistrer(AUTRE, new PreferenceDePlanification(PATIENT_1, null, 5, false));

        assertThat(preferences().duCentre(CENTRE)).containsEntry(PATIENT_1, premiere)
                .containsEntry(PATIENT_2, PreferenceDePlanification.aucune(PATIENT_2)).hasSize(2);

        PreferenceDePlanification remplacante = new PreferenceDePlanification(PATIENT_1, null, null, false);
        preferences().enregistrer(CENTRE, remplacante);

        assertThat(preferences().duCentre(CENTRE)).containsEntry(PATIENT_1, remplacante).hasSize(2);
        assertThat(preferences().duCentre(AUTRE).get(PATIENT_1).seancesParSemaine()).isEqualTo(5);
    }

    // ------------------------------------------------------------------ profils

    @Test
    void should_read_back_replace_and_isolate_the_profiles_of_the_nurses_with_sorted_skills() {
        profils().enregistrer(CENTRE, new ProfilDePlanification(INFIRMIER_1, 60, EnumSet.of(Competence.CATHETER, Competence.PEDIATRIE)));
        profils().enregistrer(AUTRE, new ProfilDePlanification(INFIRMIER_1, 90, Set.of()));

        ProfilDePlanification lu = profils().duCentre(CENTRE).get(INFIRMIER_1);
        assertThat(lu.tauxActivite()).isEqualTo(60);
        assertThat(lu.competences()).containsExactly(Competence.PEDIATRIE, Competence.CATHETER);

        profils().enregistrer(CENTRE, new ProfilDePlanification(INFIRMIER_1, 100, Set.of()));

        assertThat(profils().duCentre(CENTRE).get(INFIRMIER_1)).isEqualTo(ProfilDePlanification.parDefaut(INFIRMIER_1));
        assertThat(profils().duCentre(AUTRE).get(INFIRMIER_1).tauxActivite()).isEqualTo(90);
    }
}
