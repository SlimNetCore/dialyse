package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.InfirmiersDuCentre.Situation;
import com.hemodialyse.backend.contexts.planification.doubles.ParametrageEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les cas d'usage d'écriture du paramétrage, avec les doubles en mémoire des ports.
 */
class EcrituresDuParametrageTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997800-0000-0000-0000-00000000000a"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("99997800-0000-0000-0000-00000000000b"));
    private static final SalleId SALLE = new SalleId(UUID.fromString("99997800-0000-0000-0000-000000000011"));
    private static final SalleId SALLE_ISO = new SalleId(UUID.fromString("99997800-0000-0000-0000-000000000012"));
    private static final SalleId SALLE_AUTRE = new SalleId(UUID.fromString("99997800-0000-0000-0000-000000000013"));
    private static final CreneauId MATIN = new CreneauId(UUID.fromString("99997800-0000-0000-0000-000000000021"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("99997800-0000-0000-0000-000000000031"));
    private static final InfirmierId INFIRMIER = new InfirmierId(UUID.fromString("99997800-0000-0000-0000-000000000041"));

    private final ParametrageEnMemoire monde = new ParametrageEnMemoire();
    private ModifierLesParametres modifierParametres;
    private ModifierLesReglages modifierReglages;
    private DefinirLaPreferenceDuPatient definirPreference;
    private DefinirLeProfilDeLInfirmier definirProfil;

    @BeforeEach
    void ouvrirLeCentre() {
        monde.salles.ajouter(CENTRE, SALLE, false);
        monde.salles.ajouter(CENTRE, SALLE_ISO, true);
        monde.salles.ajouter(AUTRE, SALLE_AUTRE, false);
        monde.creneaux.put(CENTRE, Set.of(MATIN));
        modifierParametres = new ModifierLesParametres(monde.unite, monde.verrou, monde.parametres, monde.salles);
        modifierReglages = new ModifierLesReglages(monde.unite, monde.verrou, monde.reglages);
        definirPreference = new DefinirLaPreferenceDuPatient(monde.unite, monde.verrou, monde.preferences, monde.creneauxPort,
                monde.patients);
        definirProfil = new DefinirLeProfilDeLInfirmier(monde.unite, monde.verrou, monde.profils, monde.infirmiers);
    }

    private ModifierLesParametres.Commande commande(Set<JourDeLaSemaine> jours, Set<SalleId> salles, Integer ratio, Integer poste) {
        return new ModifierLesParametres.Commande(jours, salles, ratio, poste);
    }

    // ------------------------------------------------------------------ paramètres

    @Test
    void should_replace_the_settings_and_mark_exactly_the_given_rooms_as_isolation_rooms() {
        ParametresDuPlanning enregistres = modifierParametres.executer(CENTRE,
                commande(Set.of(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI), Set.of(SALLE), 6, 2));

        assertThat(enregistres.sallesIsolement()).containsExactly(SALLE);
        assertThat(new ConsulterLesParametres(monde.parametres).executer(CENTRE)).isEqualTo(enregistres);
        assertThat(monde.salles.sallesDIsolement(CENTRE)).containsExactly(SALLE);
    }

    @Test
    void should_clear_every_isolation_room_of_the_centre_when_none_is_given() {
        modifierParametres.executer(CENTRE, commande(Set.of(JourDeLaSemaine.LUNDI), null, null, null));

        assertThat(monde.salles.sallesDIsolement(CENTRE)).isEmpty();
    }

    @Test
    void should_never_touch_the_rooms_of_another_centre() {
        monde.salles.marquerIsolement(AUTRE, Set.of(SALLE_AUTRE));

        modifierParametres.executer(CENTRE, commande(Set.of(JourDeLaSemaine.LUNDI), Set.of(SALLE), null, null));

        assertThat(monde.salles.sallesDIsolement(AUTRE)).containsExactly(SALLE_AUTRE);
    }

    @Test
    void should_refuse_a_room_of_another_centre_and_write_nothing() {
        assertThatThrownBy(() -> modifierParametres.executer(CENTRE,
                commande(Set.of(JourDeLaSemaine.LUNDI), Set.of(SALLE_AUTRE), 6, 2)))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Une salle d'isolement n'appartient pas à ce centre");

        assertThat(monde.salles.sallesDIsolement(CENTRE)).containsExactly(SALLE_ISO);
        assertThat(monde.parametres.lire(CENTRE).ratio().patientsParInfirmier()).isEqualTo(4);
    }

    @Test
    void should_refuse_invalid_values_before_opening_a_transaction() {
        assertThatThrownBy(() -> modifierParametres.executer(CENTRE, commande(Set.of(), null, 4, 3)))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> modifierParametres.executer(CENTRE, commande(Set.of(JourDeLaSemaine.LUNDI), null, 21, 3)))
                .isInstanceOf(SaisieInvalide.class);

        assertThat(monde.transactions).isZero();
    }

    @Test
    void should_take_the_centre_lock_inside_the_transaction_for_every_write() {
        modifierParametres.executer(CENTRE, commande(Set.of(JourDeLaSemaine.LUNDI), null, null, null));
        modifierReglages.executer(CENTRE, ReglagesDePlanification.parDefaut());
        monde.patientsDuCentre.put(PATIENT, PatientsDuCentre.Situation.EN_SUIVI);
        definirPreference.executer(CENTRE, PreferenceDePlanification.aucune(PATIENT));
        monde.infirmiersDuCentre.put(INFIRMIER, Situation.ACTIF);
        definirProfil.executer(CENTRE, ProfilDePlanification.parDefaut(INFIRMIER));

        assertThat(monde.verrous).containsExactly(CENTRE, CENTRE, CENTRE, CENTRE);
        assertThat(monde.transactions).isEqualTo(4);
    }

    // ------------------------------------------------------------------ réglages

    @Test
    void should_replace_the_settings_of_the_centre_only() {
        modifierReglages.executer(CENTRE, new ReglagesDePlanification(true, 6, 35, 2));

        assertThat(new ConsulterLesReglages(monde.reglages).executer(CENTRE)).isEqualTo(new ReglagesDePlanification(true, 6, 35, 2));
        assertThat(new ConsulterLesReglages(monde.reglages).executer(AUTRE)).isEqualTo(ReglagesDePlanification.parDefaut());
    }

    // ------------------------------------------------------------------ préférences

    @Test
    void should_record_the_preference_of_a_patient_followed_by_the_centre() {
        monde.patientsDuCentre.put(PATIENT, PatientsDuCentre.Situation.EN_SUIVI);

        PreferenceDePlanification preference = definirPreference.executer(CENTRE,
                new PreferenceDePlanification(PATIENT, MATIN, 3, true));

        assertThat(monde.preferences.duCentre(CENTRE)).containsEntry(PATIENT, preference);
    }

    @Test
    void should_refuse_a_preferred_slot_that_is_not_the_centre_s_before_looking_for_the_patient() {
        assertThatThrownBy(() -> definirPreference.executer(CENTRE,
                new PreferenceDePlanification(PATIENT, new CreneauId(UUID.randomUUID()), 3, true)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class,
                        e -> assertThat(e.code()).isEqualTo("PREFERENCE_CRENEAU_INCONNU"));
    }

    @Test
    void should_refuse_an_unknown_patient() {
        assertThatThrownBy(() -> definirPreference.executer(CENTRE, PreferenceDePlanification.aucune(PATIENT)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class,
                        e -> assertThat(e.code()).isEqualTo("PREFERENCE_PATIENT_INTROUVABLE"));
    }

    @Test
    void should_refuse_the_preference_of_a_patient_who_left_the_centre() {
        monde.patientsDuCentre.put(PATIENT, PatientsDuCentre.Situation.SORTI);

        assertThatThrownBy(() -> definirPreference.executer(CENTRE, PreferenceDePlanification.aucune(PATIENT)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class,
                        e -> assertThat(e.code()).isEqualTo("PREFERENCE_PATIENT_SORTI"));
        assertThat(monde.preferences.duCentre(CENTRE)).isEmpty();
    }

    // ------------------------------------------------------------------ profils

    @Test
    void should_record_the_profile_of_an_active_nurse() {
        monde.infirmiersDuCentre.put(INFIRMIER, Situation.ACTIF);

        ProfilDePlanification profil = definirProfil.executer(CENTRE,
                new ProfilDePlanification(INFIRMIER, 80, Set.of(Competence.CATHETER)));

        assertThat(monde.profils.duCentre(CENTRE)).containsEntry(INFIRMIER, profil);
    }

    @Test
    void should_refuse_an_unknown_nurse() {
        assertThatThrownBy(() -> definirProfil.executer(CENTRE, ProfilDePlanification.parDefaut(INFIRMIER)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class,
                        e -> assertThat(e.code()).isEqualTo("PROFIL_INFIRMIER_INTROUVABLE"));
    }

    @Test
    void should_refuse_the_profile_of_an_inactive_nurse() {
        monde.infirmiersDuCentre.put(INFIRMIER, Situation.INACTIF);

        assertThatThrownBy(() -> definirProfil.executer(CENTRE, ProfilDePlanification.parDefaut(INFIRMIER)))
                .isInstanceOfSatisfying(RegleDePlanificationViolee.class,
                        e -> assertThat(e.code()).isEqualTo("PROFIL_INFIRMIER_INACTIF"));
        assertThat(monde.profils.duCentre(CENTRE)).isEmpty();
    }
}
