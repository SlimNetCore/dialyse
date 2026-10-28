package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.doubles.ParametrageEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les cas d'usage de lecture : listes des écrans de préférences et de profils, réglages.
 */
class ConsultationDuParametrageTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997803-0000-0000-0000-00000000000a"));

    @Test
    void should_hand_over_the_page_asked_for_by_the_screens_of_patients_and_nurses() {
        PageDeResultats<LectureDesPreferences.LigneDePatient> patients = new PageDeResultats<>(List.of(), 3, 1, 2);
        PageDeResultats<LectureDesPreferences.LigneDInfirmier> infirmiers = new PageDeResultats<>(List.of(), 5, 0, 20);
        LectureDesPreferences lecture = new LectureDesPreferences() {
            @Override
            public PageDeResultats<LigneDePatient> patientsSuivis(CentreId centre, Pagination pagination) {
                assertThat(centre).isEqualTo(CENTRE);
                assertThat(pagination).isEqualTo(new Pagination(1, 2));
                return patients;
            }

            @Override
            public PageDeResultats<LigneDInfirmier> infirmiersActifs(CentreId centre, Pagination pagination) {
                assertThat(centre).isEqualTo(CENTRE);
                assertThat(pagination).isEqualTo(new Pagination(0, 20));
                return infirmiers;
            }
        };

        assertThat(new ListerLesPreferencesDesPatients(lecture).executer(CENTRE, new Pagination(1, 2))).isSameAs(patients);
        assertThat(new ListerLesProfilsDesInfirmiers(lecture).executer(CENTRE, new Pagination(0, 20))).isSameAs(infirmiers);
    }

    @Test
    void should_return_the_settings_just_written() {
        ParametrageEnMemoire monde = new ParametrageEnMemoire();
        ReglagesDePlanification nouveaux = new ReglagesDePlanification(true, 7, 38, 3);

        ReglagesDePlanification renvoyes = new ModifierLesReglages(monde.unite, monde.verrou, monde.reglages).executer(CENTRE, nouveaux);

        assertThat(renvoyes).isEqualTo(nouveaux);
    }
}
