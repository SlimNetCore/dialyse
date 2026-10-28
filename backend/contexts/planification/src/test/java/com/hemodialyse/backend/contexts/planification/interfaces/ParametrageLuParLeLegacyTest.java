package com.hemodialyse.backend.contexts.planification.interfaces;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.doubles.ParametrageEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.interfaces.publie.ParametrageLuParLeLegacy;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le langage publié de lecture du paramétrage traduit le modèle du contexte sans rien y ajouter.
 */
class ParametrageLuParLeLegacyTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997802-0000-0000-0000-00000000000a"));
    private static final SalleId SALLE = new SalleId(UUID.fromString("99997802-0000-0000-0000-000000000011"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("99997802-0000-0000-0000-000000000031"));
    private static final InfirmierId INFIRMIER = new InfirmierId(UUID.fromString("99997802-0000-0000-0000-000000000041"));
    private static final CreneauId MATIN = new CreneauId(UUID.fromString("99997802-0000-0000-0000-000000000021"));

    private final ParametrageEnMemoire monde = new ParametrageEnMemoire();
    private final ParametrageDeLaPlanification publie = new ParametrageLuParLeLegacy(monde.parametres, monde.reglages,
            monde.preferences, monde.profils);

    @Test
    void should_publish_the_default_settings_of_a_centre_without_settings() {
        ParametrageDeLaPlanification.Parametres parametres = publie.parametres(CENTRE.valeur());

        assertThat(parametres.joursOuverts()).hasSize(7);
        assertThat(parametres.sallesIsolement()).isEmpty();
        assertThat(parametres.patientsParInfirmier()).isEqualTo(4);
        assertThat(parametres.patientsParPosteEtSerie()).isEqualTo(3);
        assertThat(publie.reglages(CENTRE.valeur())).isEqualTo(new ParametrageDeLaPlanification.Reglages(false, 5, 40, 1));
    }

    @Test
    void should_publish_civil_days_isolation_rooms_ratio_and_stations() {
        monde.salles.ajouter(CENTRE, SALLE, false);
        monde.parametres.enregistrer(CENTRE, ParametresDuPlanning.definir(
                Set.of(JourDeLaSemaine.DIMANCHE, JourDeLaSemaine.MERCREDI), Set.of(SALLE), 7, 2));

        ParametrageDeLaPlanification.Parametres parametres = publie.parametres(CENTRE.valeur());

        assertThat(parametres.joursOuverts()).containsExactlyInAnyOrder(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY);
        assertThat(parametres.sallesIsolement()).containsExactly(SALLE.valeur());
        assertThat(parametres.patientsParInfirmier()).isEqualTo(7);
        assertThat(parametres.patientsParPosteEtSerie()).isEqualTo(2);
    }

    @Test
    void should_publish_the_settings_the_preferences_the_profiles_and_the_centres_replanned_at_night() {
        monde.reglages.enregistrer(CENTRE, new ReglagesDePlanification(true, 6, 35, 2));
        monde.preferences.enregistrer(CENTRE, new PreferenceDePlanification(PATIENT, MATIN, 3, true));
        monde.profils.enregistrer(CENTRE, new ProfilDePlanification(INFIRMIER, 70, Set.of(Competence.PEDIATRIE)));

        assertThat(publie.reglages(CENTRE.valeur())).isEqualTo(new ParametrageDeLaPlanification.Reglages(true, 6, 35, 2));
        assertThat(publie.preferencesDesPatients(CENTRE.valeur())).containsEntry(PATIENT.valeur(),
                new ParametrageDeLaPlanification.Preference(PATIENT.valeur(), MATIN.valeur(), 3, true));
        assertThat(publie.profilsDesInfirmiers(CENTRE.valeur())).containsEntry(INFIRMIER.valeur(),
                new ParametrageDeLaPlanification.Profil(INFIRMIER.valeur(), 70, Set.of("PEDIATRIE")));
        assertThat(publie.centresEnReplanificationNocturne()).containsExactly(CENTRE.valeur());
    }
}
