package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.ObjectifInfirmiers;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import org.junit.jupiter.api.Test;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.optimisation.service.OptimisationFixture.DIMANCHE;
import static org.assertj.core.api.Assertions.assertThat;

class EmpreinteOptimisationTest {

    private static final ParametresOptimisation PARAMS = ParametresOptimisation.parDefaut(PerimetreOptimisation.COMPLET, DIMANCHE);

    private static OptimisationFixture centre() {
        OptimisationFixture f = new OptimisationFixture();
        SalleRef a = f.salle("Salle A", 2);
        CreneauRef matin = f.creneau("Matin");
        f.patient("P1", false, a, matin, f.generateur(a, 0), LUNDI);
        return f;
    }

    @Test
    void should_be_stable_for_identical_data() {
        OptimisationFixture f = centre();

        assertThat(EmpreinteOptimisation.calculer(f.build(), PARAMS))
                .isEqualTo(EmpreinteOptimisation.calculer(f.build(), PARAMS))
                .hasSize(64);
    }

    @Test
    void should_ignore_parameters_that_do_not_change_the_data_read() {
        OptimisationFixture f = centre();
        ParametresOptimisation autre = new ParametresOptimisation(PerimetreOptimisation.COMPLET, DIMANCHE, 1, 99, 9,
                ObjectifInfirmiers.ECONOMIE, 1, 3);

        assertThat(EmpreinteOptimisation.calculer(f.build(), autre))
                .isEqualTo(EmpreinteOptimisation.calculer(f.build(), PARAMS));
    }

    @Test
    void should_change_when_a_patient_is_added_or_the_horizon_moves() {
        OptimisationFixture f = centre();
        String avant = EmpreinteOptimisation.calculer(f.build(), PARAMS);

        ParametresOptimisation autreSemaine = ParametresOptimisation.parDefaut(PerimetreOptimisation.COMPLET, DIMANCHE.plusWeeks(1));
        assertThat(EmpreinteOptimisation.calculer(f.build(), autreSemaine)).isNotEqualTo(avant);

        f.patientNonPlace("Nouveau", false, LUNDI);
        assertThat(EmpreinteOptimisation.calculer(f.build(), PARAMS)).isNotEqualTo(avant);
    }

    @Test
    void should_change_when_a_nurse_rotation_or_absence_changes() {
        OptimisationFixture f = centre();
        var marie = f.infirmier("Marie");
        String base = EmpreinteOptimisation.calculer(f.build(), PARAMS);

        f.absence(marie, DIMANCHE, DIMANCHE.plusDays(2));
        String avecAbsence = EmpreinteOptimisation.calculer(f.build(), PARAMS);
        assertThat(avecAbsence).isNotEqualTo(base);

        f.affecter(marie, f.salles.getFirst(), f.creneaux.getFirst(), LUNDI);
        assertThat(EmpreinteOptimisation.calculer(f.build(), PARAMS)).isNotEqualTo(avecAbsence);
    }
}
