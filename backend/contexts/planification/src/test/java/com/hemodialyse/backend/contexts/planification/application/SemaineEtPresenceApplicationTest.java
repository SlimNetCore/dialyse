package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.SejourDuPatient;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.Semaine;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.A1;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.CENTRE;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.MATIN;
import static com.hemodialyse.backend.contexts.planification.domain.JeuDePlaces.patient;
import static org.assertj.core.api.Assertions.assertThat;

/** Cas d'usage de lecture de la semaine et de la présence des patients, avec des doubles en mémoire. */
class SemaineEtPresenceApplicationTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 3, 2);

    private final List<PatientDeLaSemaine> fiches = new ArrayList<>();
    private LocalDate aujourdhui = LUNDI.plusDays(3);

    private PatientDeLaSemaine fiche(int n, SejourDuPatient sejour, JourDeLaSemaine... jours) {
        PatientDeLaSemaine p = new PatientDeLaSemaine(patient(n), "Patient " + n, new Occupation(patient(n), A, MATIN, A1,
                jours.length == 0 ? EnumSet.noneOf(JourDeLaSemaine.class) : EnumSet.copyOf(List.of(jours)),
                CategorieDIsolement.SANS_RISQUE), sejour);
        fiches.add(p);
        return p;
    }

    private ConsulterLaSemaine semaine() {
        return new ConsulterLaSemaine((centre, du) -> JeuDePlaces.ressources(), centre -> fiches, (centre, du, au) -> List.of(),
                (centre, du, au) -> List.of(), centre -> aujourdhui);
    }

    @Test
    void should_read_the_current_week_of_the_centre_when_no_date_is_given() {
        fiche(1, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI);
        Semaine s = semaine().executer(CENTRE, null);
        assertThat(s.debut()).isEqualTo(LUNDI.minusDays(1));
        assertThat(s.cases().stream().filter(c -> !c.occupants().isEmpty())).hasSize(1);
    }

    @Test
    void should_read_the_week_containing_the_given_day() {
        assertThat(semaine().executer(CENTRE, LUNDI.plusDays(5)).debut()).isEqualTo(LUNDI.minusDays(1));
        assertThat(semaine().executer(CENTRE, LUNDI.plusDays(6)).debut()).isEqualTo(LUNDI.plusDays(6));
    }

    private ConsulterLaPresenceDesPatients presence() {
        return new ConsulterLaPresenceDesPatients(centre -> fiches);
    }

    @Test
    void should_expect_only_present_patients_whose_day_is_ticked_sorted_by_identifier() {
        fiche(2, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI);
        fiche(1, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI, JourDeLaSemaine.MARDI);
        fiche(3, new SejourDuPatient(null, null, false, true), JourDeLaSemaine.LUNDI);
        fiche(4, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.MARDI);
        fiche(5, new SejourDuPatient(null, LUNDI, true, false), JourDeLaSemaine.LUNDI);
        fiche(6, new SejourDuPatient(LUNDI.plusDays(1), null, false, false), JourDeLaSemaine.LUNDI);
        assertThat(presence().attendusLe(CENTRE.valeur(), LUNDI)).containsExactly(patient(1).valeur(), patient(2).valeur());
    }

    @Test
    void should_count_the_active_queue_without_sleeping_and_already_gone_patients() {
        fiche(1, SejourDuPatient.SANS_LIMITE);
        fiche(2, new SejourDuPatient(null, null, false, true));
        fiche(3, new SejourDuPatient(null, LUNDI.minusDays(1), true, false));
        fiche(4, new SejourDuPatient(null, LUNDI, true, false));
        assertThat(presence().fileActive(CENTRE.valeur(), LUNDI, LUNDI.plusDays(30))).isEqualTo(2);
    }

    @Test
    void should_count_every_patient_attached_to_the_generators_whatever_his_state() {
        fiche(1, SejourDuPatient.SANS_LIMITE, JourDeLaSemaine.LUNDI);
        fiche(2, new SejourDuPatient(null, LocalDate.MIN, true, false));
        fiche(3, new SejourDuPatient(null, null, false, true), JourDeLaSemaine.MARDI);
        var compteur = new CompterLesPatientsAuxGenerateurs(centre -> fiches);

        assertThat(compteur.nombre(CENTRE.valeur(), List.of(JeuDePlaces.A1.valeur()))).as("sortis et sommeil compris").isEqualTo(3);
        assertThat(compteur.nombre(CENTRE.valeur(), List.of(JeuDePlaces.B1.valeur()))).isZero();
        assertThat(compteur.nombre(CENTRE.valeur(), List.of())).isZero();
    }

    @Test
    void should_give_the_base_of_the_theoretical_capacity_with_the_default_ratio_when_the_stored_one_is_invalid() {
        fiche(1, SejourDuPatient.SANS_LIMITE);
        var invalide = new com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository() {
            @Override
            public com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning lire(CentreId centre) {
                throw new com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide("0 patient par poste");
            }

            @Override
            public void enregistrer(CentreId centre,
                                    com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning parametres) {
            }
        };
        BaseDeLaCapaciteTheorique base = new ConsulterLaBaseDeLaCapaciteTheorique(centre -> 2, invalide, presence());
        BaseDeLaCapaciteTheorique.Base b = base.duCentre(UUID.fromString(CENTRE.valeur().toString()), LUNDI, LUNDI.plusDays(7));
        assertThat(b.creneaux()).isEqualTo(2);
        assertThat(b.patientsParPosteEtSerie()).isEqualTo(3);
        assertThat(b.fileActive()).isEqualTo(1);
    }
}
