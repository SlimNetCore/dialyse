package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des ports de lecture et d'écriture des places (dépôt, risque infectieux, noms, verrou) : le double en mémoire
 * ({@code PlacesEnMemoire}) et les adapters JDBC (H2, PostgreSQL 16) passent exactement ces tests, ce qui garantit que le double est
 * fidèle. Les scénarios propres à une vraie base (création de la ligne de verrou, course) sont dans les sous-classes JDBC.
 */
abstract class PlacesContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997805-0000-0000-0000-00000000000a"));
    protected static final SalleId SALLE = new SalleId(UUID.fromString("99997805-0000-0000-0000-000000000011"));
    protected static final SalleId SALLE_2 = new SalleId(UUID.fromString("99997805-0000-0000-0000-000000000012"));
    protected static final CreneauId CRENEAU = new CreneauId(UUID.fromString("99997805-0000-0000-0000-000000000021"));
    protected static final GenerateurId GENERATEUR = new GenerateurId(UUID.fromString("99997805-0000-0000-0000-000000000031"));
    protected static final GenerateurId GENERATEUR_2 = new GenerateurId(UUID.fromString("99997805-0000-0000-0000-000000000032"));
    protected static final GenerateurId GENERATEUR_3 = new GenerateurId(UUID.fromString("99997805-0000-0000-0000-000000000033"));
    protected static final PatientId ALICE = new PatientId(UUID.fromString("99997805-0000-0000-0000-000000000041"));
    protected static final PatientId BORIS = new PatientId(UUID.fromString("99997805-0000-0000-0000-000000000042"));
    protected static final PatientId CHLOE = new PatientId(UUID.fromString("99997805-0000-0000-0000-000000000043"));
    protected static final PatientId DAMIEN = new PatientId(UUID.fromString("99997805-0000-0000-0000-000000000044"));
    protected static final PatientId SANS_PLACE = new PatientId(UUID.fromString("99997805-0000-0000-0000-000000000045"));
    protected static final PatientId INCONNU = new PatientId(UUID.fromString("99997805-0000-0000-0000-0000000000ff"));
    protected static final RessourcesDuCentre RIEN = new RessourcesDuCentre(null, null, null, null, null);

    protected abstract PlacesDuCentreRepository places();

    protected abstract RisquesInfectieux risques();

    protected abstract NomsDesPatients noms();

    protected abstract VerrouDesPlaces verrou();

    /** Un patient actif du centre ; {@code placement} nul = sans place. Nom affiché « NOM Prénom ». */
    protected abstract void patient(PatientId patient, String nomAffiche, Place placement);

    /** Un patient d'un autre centre, placé sur les mêmes ressources. */
    protected abstract void patientDUnAutreCentre(PatientId patient, String nomAffiche, Place placement);

    /** Le patient est sorti (transféré le jour même du jugement moins un) : sa place est libérée. */
    protected abstract void sortir(PatientId patient);

    /** La dernière sérologie des marqueurs à risque du patient est positive. */
    protected abstract void serologiePositive(PatientId patient);

    protected static Place place(SalleId salle, GenerateurId generateur, JourDeLaSemaine... jours) {
        return new Place(salle, CRENEAU, generateur, EnumSet.copyOf(List.of(jours)));
    }

    protected final LocalDate aujourdhui() {
        return LocalDate.of(2026, 10, 27);
    }

    @Test
    void should_load_the_places_of_the_active_patients_of_the_centre() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI));
        patient(SANS_PLACE, "DURAND Sam", null);

        PlacesDuCentre lues = places().charger(CENTRE, RIEN, aujourdhui());

        assertThat(lues.occupations()).hasSize(1);
        Occupation alice = lues.occupationDe(ALICE).orElseThrow();
        assertThat(alice.salle()).isEqualTo(SALLE);
        assertThat(alice.creneau()).isEqualTo(CRENEAU);
        assertThat(alice.generateur()).isEqualTo(GENERATEUR);
        assertThat(alice.jours()).containsExactlyInAnyOrder(JourDeLaSemaine.LUNDI, JourDeLaSemaine.JEUDI);
    }

    @Test
    void should_free_the_place_of_a_patient_who_left_the_centre() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));
        sortir(BORIS);

        assertThat(places().charger(CENTRE, RIEN, aujourdhui()).occupations()).extracting(Occupation::patient)
                .containsExactly(ALICE);
    }

    @Test
    void should_tell_who_has_left_the_centre_and_ignore_the_patients_of_another_centre() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));
        patientDUnAutreCentre(CHLOE, "DURAND Chloé", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MERCREDI));
        sortir(BORIS);

        assertThat(places().estSorti(CENTRE, BORIS, aujourdhui())).isTrue();
        assertThat(places().estSorti(CENTRE, ALICE, aujourdhui())).isFalse();
        assertThat(places().estSorti(CENTRE, BORIS, aujourdhui(), "PERMANENT", null)).as("reprise dans la même saisie").isFalse();
        assertThat(places().estSorti(CENTRE, ALICE, aujourdhui(), "TRANSFERE", aujourdhui().minusDays(3)))
                .as("sortie saisie dans la même fiche").isTrue();
        assertThat(places().estSorti(CENTRE, CHLOE, aujourdhui())).as("d'un autre centre").isFalse();
        assertThat(places().estSorti(CENTRE, INCONNU, aujourdhui())).isFalse();
    }

    @Test
    void should_never_load_the_places_of_another_centre() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patientDUnAutreCentre(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));

        assertThat(places().charger(CENTRE, RIEN, aujourdhui()).occupations()).extracting(Occupation::patient)
                .containsExactly(ALICE);
        assertThat(places().placementActuel(CENTRE, BORIS)).isEmpty();
    }

    @Test
    void should_deduce_the_isolation_category_from_the_latest_serology() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));
        serologiePositive(ALICE);

        PlacesDuCentre lues = places().charger(CENTRE, RIEN, aujourdhui());

        assertThat(lues.occupationDe(ALICE).orElseThrow().categorie()).isEqualTo(CategorieDIsolement.A_RISQUE);
        assertThat(lues.occupationDe(BORIS).orElseThrow().categorie()).isEqualTo(CategorieDIsolement.SANS_RISQUE);
        assertThat(risques().de(CENTRE, ALICE)).isEqualTo(CategorieDIsolement.A_RISQUE);
        assertThat(risques().de(CENTRE, BORIS)).isEqualTo(CategorieDIsolement.SANS_RISQUE);
        assertThat(places().placementActuel(CENTRE, ALICE).orElseThrow().categorie()).isEqualTo(CategorieDIsolement.A_RISQUE);
    }

    @Test
    void should_consider_an_unknown_patient_or_one_of_another_centre_as_without_risk() {
        patientDUnAutreCentre(CHLOE, "BERNARD Chloé", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        serologiePositive(CHLOE);

        assertThat(risques().de(CENTRE, CHLOE)).isEqualTo(CategorieDIsolement.SANS_RISQUE);
        assertThat(risques().de(CENTRE, INCONNU)).isEqualTo(CategorieDIsolement.SANS_RISQUE);
    }

    @Test
    void should_give_the_current_place_even_of_a_patient_who_left_and_an_empty_one_without_place() {
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));
        patient(SANS_PLACE, "DURAND Sam", null);
        sortir(BORIS);

        assertThat(places().placementActuel(CENTRE, BORIS).orElseThrow().salle()).isEqualTo(SALLE);
        assertThat(places().placementActuel(CENTRE, SANS_PLACE).orElseThrow().estVide()).isTrue();
        assertThat(places().placementActuel(CENTRE, INCONNU)).isEmpty();
    }

    @Test
    void should_write_only_the_place_of_the_changed_patients() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR_2, JourDeLaSemaine.MARDI));
        PlacesDuCentre lues = places().charger(CENTRE, ressourcesPour(), aujourdhui());
        lues.placer(ALICE, Optional.of(place(SALLE_2, GENERATEUR_3, JourDeLaSemaine.MERCREDI, JourDeLaSemaine.SAMEDI)),
                CategorieDIsolement.SANS_RISQUE);

        places().enregistrer(lues);

        Occupation alice = places().placementActuel(CENTRE, ALICE).orElseThrow();
        assertThat(alice.salle()).isEqualTo(SALLE_2);
        assertThat(alice.jours()).containsExactlyInAnyOrder(JourDeLaSemaine.MERCREDI, JourDeLaSemaine.SAMEDI);
        Occupation boris = places().placementActuel(CENTRE, BORIS).orElseThrow();
        assertThat(boris.salle()).isEqualTo(SALLE);
        assertThat(boris.generateur()).isEqualTo(GENERATEUR_2);
        assertThat(boris.jours()).containsExactly(JourDeLaSemaine.MARDI);
    }

    @Test
    void should_remove_the_place_of_a_patient_when_none_is_requested() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        PlacesDuCentre lues = places().charger(CENTRE, ressourcesPour(), aujourdhui());
        lues.placer(ALICE, Optional.empty(), CategorieDIsolement.SANS_RISQUE);

        places().enregistrer(lues);

        assertThat(places().placementActuel(CENTRE, ALICE).orElseThrow().estVide()).isTrue();
        assertThat(places().charger(CENTRE, RIEN, aujourdhui()).occupations()).isEmpty();
    }

    @Test
    void should_list_the_patients_of_a_generator_without_those_who_left_or_belong_to_another_centre() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patient(BORIS, "MARTIN Boris", place(SALLE, GENERATEUR, JourDeLaSemaine.MARDI));
        patient(CHLOE, "BERNARD Chloe", place(SALLE, GENERATEUR, JourDeLaSemaine.JEUDI));
        patient(DAMIEN, "LEROY Damien", place(SALLE, GENERATEUR_2, JourDeLaSemaine.JEUDI));
        patientDUnAutreCentre(SANS_PLACE, "DURAND Sam", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        sortir(CHLOE);

        assertThat(places().patientsDuGenerateur(CENTRE, GENERATEUR, aujourdhui())).containsExactlyInAnyOrder(ALICE, BORIS);
        assertThat(places().patientsDuGenerateur(CENTRE, GENERATEUR_3, aujourdhui())).isEmpty();
    }

    @Test
    void should_name_the_patients_of_the_centre_only() {
        patient(ALICE, "DUPONT Alice", place(SALLE, GENERATEUR, JourDeLaSemaine.LUNDI));
        patientDUnAutreCentre(BORIS, "MARTIN Boris", null);

        Map<PatientId, String> noms = noms().de(CENTRE, List.of(ALICE, BORIS, INCONNU));

        assertThat(noms).containsOnlyKeys(ALICE).containsEntry(ALICE, "DUPONT Alice");
        assertThat(noms().de(CENTRE, List.of())).isEmpty();
    }

    @Test
    void should_take_the_lock_of_the_centre_even_when_it_was_never_configured() {
        verrou().prendre(CENTRE);
        verrou().prendre(CENTRE);
    }

    /** Ressources minimales où les places utilisées par les tests existent. */
    protected final RessourcesDuCentre ressourcesPour() {
        return new RessourcesDuCentre(
                List.of(new RessourcesDuCentre.Salle(SALLE, "Salle 1", false), new RessourcesDuCentre.Salle(SALLE_2, "Salle 2", false)),
                List.of(new RessourcesDuCentre.Creneau(CRENEAU, "Matin", 1)),
                List.of(new RessourcesDuCentre.Generateur(GENERATEUR, "G1", SALLE),
                        new RessourcesDuCentre.Generateur(GENERATEUR_2, "G2", SALLE),
                        new RessourcesDuCentre.Generateur(GENERATEUR_3, "G3", SALLE_2)), Set.of(), List.of());
    }
}
