package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.doubles.LiberationEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceQuittee;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Libération nocturne et libération à l'enregistrement d'une fiche, avec le monde en mémoire (RG-PLN-080 à 083).
 */
class LiberationDesPlacesApplicationTest {

    static final CentreId A = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a0"));
    static final CentreId B = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000b0"));
    static final LocalDate J = LocalDate.of(2026, 10, 27);
    static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC);

    LiberationEnMemoire monde;
    LibererLesPlacesEchues nuit;
    LibererLaPlaceDeLaFiche fiche;

    static PatientId patient(int n) {
        return new PatientId(new UUID(0, n));
    }

    @BeforeEach
    void preparer() {
        monde = new LiberationEnMemoire();
        monde.centresActifs.addAll(java.util.List.of(A, B));
        monde.jours.put(A, J);
        monde.jours.put(B, J);
        nuit = new LibererLesPlacesEchues(monde, monde, monde, monde, monde, monde, monde, monde, HORLOGE);
        fiche = new LibererLaPlaceDeLaFiche(monde, monde, monde, monde, monde, monde, HORLOGE);
    }

    PatientId sorti(CentreId centre, int n, LocalDate dernierJour) {
        PatientId id = patient(n);
        monde.fiches.put(id, new PlaceTenue(centre, id, "TRANSFERE", dernierJour, new PlaceQuittee(new SalleId(UUID.randomUUID()),
                new CreneauId(UUID.randomUUID()), new GenerateurId(UUID.randomUUID()), Set.of(JourDeLaSemaine.LUNDI))));
        return id;
    }

    @Test
    void should_free_due_places_and_write_the_movement_dated_with_the_day_of_the_centre() {
        PatientId echu = sorti(A, 1, J.minusDays(1));
        PatientId encore = sorti(A, 2, J);

        var bilan = nuit.liberer();

        assertThat(bilan.centres()).extracting(LibererLesPlacesEchues.ResultatDuCentre::liberees).containsExactly(1, 0);
        assertThat(monde.fiches.get(echu).place().estTenue()).isFalse();
        assertThat(monde.fiches.get(encore).place().estTenue()).isTrue();
        assertThat(monde.mouvements).singleElement().satisfies(m -> {
            assertThat(m.dateDEffet()).isEqualTo(J);
            assertThat(m.automatique()).isTrue();
        });
        assertThat(monde.cachesEvinces).containsExactly(echu);
    }

    @Test
    void should_judge_each_centre_on_its_own_day() {
        PatientId a = sorti(A, 1, J);
        PatientId b = sorti(B, 2, J);
        monde.jours.put(A, J.plusDays(1));

        nuit.liberer();

        assertThat(monde.fiches.get(a).place().estTenue()).as("jour du centre A : lendemain, échu").isFalse();
        assertThat(monde.fiches.get(b).place().estTenue()).as("jour du centre B : même jour, pas échu").isTrue();
    }

    @Test
    void should_only_process_active_centres() {
        PatientId inactif = sorti(new CentreId(UUID.randomUUID()), 3, J.minusDays(3));
        nuit.liberer();
        assertThat(monde.fiches.get(inactif).place().estTenue()).isTrue();
    }

    @Test
    void should_be_idempotent() {
        sorti(A, 1, J.minusDays(1));
        nuit.liberer();
        var second = nuit.liberer();
        assertThat(second.centres()).extracting(LibererLesPlacesEchues.ResultatDuCentre::liberees).containsExactly(0, 0);
        assertThat(monde.mouvements).hasSize(1);
    }

    @Test
    void should_free_the_other_patients_report_the_failed_one_and_alert_once_per_centre_and_day() {
        PatientId fautif = sorti(A, 1, J.minusDays(1));
        PatientId bon = sorti(A, 2, J.minusDays(1));
        monde.mouvementEnPanne.add(fautif);

        var premiere = nuit.liberer();
        var seconde = nuit.liberer();

        assertThat(monde.fiches.get(bon).place().estTenue()).isFalse();
        assertThat(monde.fiches.get(fautif).place().estTenue()).as("transaction du patient annulée").isTrue();
        assertThat(monde.mouvements).hasSize(1);
        assertThat(premiere.centres().get(0).incidents()).hasSize(1);
        assertThat(premiere.centres().get(0).alerteEmise()).isTrue();
        assertThat(seconde.centres().get(0).alerteEmise()).as("clé déterministe : pas de seconde alerte ce jour").isFalse();
        assertThat(monde.alertesEmises).hasSize(1).first().asString().endsWith("|1");
    }

    @Test
    void should_retry_the_failed_patient_the_next_night() {
        PatientId fautif = sorti(A, 1, J.minusDays(1));
        monde.mouvementEnPanne.add(fautif);
        nuit.liberer();
        monde.mouvementEnPanne.clear();
        monde.jours.put(A, J.plusDays(1));
        nuit.liberer();
        assertThat(monde.fiches.get(fautif).place().estTenue()).isFalse();
    }

    @Test
    void should_report_a_centre_whose_lock_is_held_elsewhere_without_stopping_the_others() {
        PatientId a = sorti(A, 1, J.minusDays(1));
        PatientId b = sorti(B, 2, J.minusDays(1));
        monde.verrouTenuAilleurs.add(A);

        var bilan = nuit.liberer();

        assertThat(monde.fiches.get(a).place().estTenue()).isTrue();
        assertThat(monde.fiches.get(b).place().estTenue()).isFalse();
        assertThat(bilan.centres().get(0).incidents()).hasSize(1);
    }

    @Test
    void should_report_an_unreadable_list_of_centres() {
        monde.listeDesCentresEnPanne = true;
        var bilan = nuit.liberer();
        assertThat(bilan.centres()).isEmpty();
        assertThat(bilan.incident()).contains("liste des centres actifs");
    }

    @Test
    void should_report_a_centre_whose_day_is_unreadable() {
        sorti(A, 1, J.minusDays(1));
        monde.jours.remove(A);
        // un jour introuvable est une lecture en échec, pas un oubli silencieux
        var resultat = nuit.liberer(A);
        assertThat(resultat.liberees()).isZero();
        assertThat(resultat.incidents()).singleElement().asString().contains("lecture du centre");
    }

    @Test
    void should_never_write_a_second_movement_for_the_same_place() {
        PatientId id = sorti(A, 1, J.minusDays(2));
        assertThat(fiche.siEchue(A.valeur(), id.valeur())).isTrue();
        // la fiche est renvoyée avec sa place : elle est de nouveau tenue, puis libérée sans nouveau mouvement
        monde.fiches.put(id, new PlaceTenue(A, id, "TRANSFERE", J.minusDays(2), monde.mouvements.get(0).placeQuittee()));
        assertThat(fiche.siEchue(A.valeur(), id.valeur())).isTrue();
        assertThat(monde.fiches.get(id).place().estTenue()).isFalse();
        assertThat(monde.mouvements).hasSize(1);
    }

    @Test
    void should_judge_the_deadline_the_day_before_when_a_record_is_saved() {
        PatientId hier = sorti(A, 1, J.minusDays(1));
        PatientId avantHier = sorti(A, 2, J.minusDays(2));

        assertThat(fiche.siEchue(A.valeur(), hier.valeur())).as("dernier jour hier : libérée la nuit, pas à l'enregistrement").isFalse();
        assertThat(fiche.siEchue(A.valeur(), avantHier.valeur())).isTrue();
        assertThat(monde.mouvements).singleElement().satisfies(m -> assertThat(m.dateDEffet()).as("jour réel, pas J-1").isEqualTo(J));
    }

    @Test
    void should_take_no_lock_when_a_record_has_nothing_to_free() {
        PatientId id = sorti(A, 1, J);
        monde.verrouTenuAilleurs.add(A);
        assertThat(fiche.siEchue(A.valeur(), id.valeur())).isFalse();
    }

    @Test
    void should_answer_409_when_the_lock_of_the_centre_is_not_obtained_while_saving_a_record() {
        PatientId id = sorti(A, 1, J.minusDays(3));
        monde.verrouTenuAilleurs.add(A);
        assertThatThrownBy(() -> fiche.siEchue(A.valeur(), id.valeur())).isInstanceOf(PlacementDesPatients.EcritureConcurrente.class);
        assertThat(monde.fiches.get(id).place().estTenue()).isTrue();
    }
}
