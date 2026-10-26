package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParametresDuPlanningTest {

    private static final SalleId ISOLEMENT = new SalleId(UUID.fromString("99997800-0000-0000-0000-000000000001"));

    @Test
    void should_open_every_day_with_ratio_4_and_3_patients_per_station_by_default() {
        ParametresDuPlanning parDefaut = ParametresDuPlanning.parDefaut();

        assertThat(parDefaut.joursOuverts()).containsExactlyElementsOf(EnumSet.allOf(JourDeLaSemaine.class));
        assertThat(parDefaut.sallesIsolement()).isEmpty();
        assertThat(parDefaut.ratio().patientsParInfirmier()).isEqualTo(4);
        assertThat(parDefaut.patientsParPoste().nombre()).isEqualTo(3);
    }

    @Test
    void should_replace_everything_and_use_the_defaults_for_the_values_left_out() {
        ParametresDuPlanning definis = ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.LUNDI), null, null, null);

        assertThat(definis.ratio()).isEqualTo(RatioDeSecurite.parDefaut());
        assertThat(definis.patientsParPoste()).isEqualTo(PatientsParPosteEtSerie.parDefaut());
        assertThat(definis.sallesIsolement()).isEmpty();
    }

    @Test
    void should_keep_the_values_given() {
        ParametresDuPlanning definis = ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.SAMEDI, JourDeLaSemaine.LUNDI),
                Set.of(ISOLEMENT), 6, 2);

        assertThat(definis.joursOuverts()).containsExactly(JourDeLaSemaine.LUNDI, JourDeLaSemaine.SAMEDI);
        assertThat(definis.sallesIsolement()).containsExactly(ISOLEMENT);
        assertThat(definis.ratio().patientsParInfirmier()).isEqualTo(6);
        assertThat(definis.patientsParPoste().nombre()).isEqualTo(2);
    }

    @Test
    void should_require_at_least_one_opening_day() {
        assertThatThrownBy(() -> ParametresDuPlanning.definir(Set.of(), null, 4, 3)).isInstanceOf(SaisieInvalide.class)
                .hasMessage("Au moins un jour d'ouverture est requis");
        assertThatThrownBy(() -> ParametresDuPlanning.definir(null, null, 4, 3)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new ParametresDuPlanning(Set.of(), Set.of(), RatioDeSecurite.parDefaut(),
                PatientsParPosteEtSerie.parDefaut())).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_accept_the_ratio_bounds_and_refuse_beyond() {
        assertThat(new RatioDeSecurite(1).patientsParInfirmier()).isEqualTo(1);
        assertThat(new RatioDeSecurite(20).patientsParInfirmier()).isEqualTo(20);
        for (int invalide : List.of(0, 21, -1)) {
            assertThatThrownBy(() -> new RatioDeSecurite(invalide)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("Le ratio patients par infirmier doit être compris entre 1 et 20");
        }
    }

    @Test
    void should_accept_the_patients_per_station_bounds_and_refuse_beyond() {
        assertThat(new PatientsParPosteEtSerie(1).nombre()).isEqualTo(1);
        assertThat(new PatientsParPosteEtSerie(10).nombre()).isEqualTo(10);
        for (int invalide : List.of(0, 11, -1)) {
            assertThatThrownBy(() -> new PatientsParPosteEtSerie(invalide)).isInstanceOf(SaisieInvalide.class)
                    .hasMessage("Le nombre de patients par poste et par série doit être compris entre 1 et 10");
        }
    }

    @Test
    void should_report_the_first_failure_in_the_order_days_ratio_station() {
        assertThatThrownBy(() -> ParametresDuPlanning.definir(Set.of(), null, 0, 0)).hasMessageContaining("jour d'ouverture");
        assertThatThrownBy(() -> ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.LUNDI), null, 0, 0))
                .hasMessageContaining("ratio");
        assertThatThrownBy(() -> ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.LUNDI), null, 4, 0))
                .hasMessageContaining("par poste");
    }

    @Test
    void should_swap_the_isolation_rooms_without_touching_the_rest() {
        ParametresDuPlanning base = ParametresDuPlanning.definir(Set.of(JourDeLaSemaine.MARDI), null, 7, 5);

        ParametresDuPlanning avec = base.avecLesSallesDIsolement(Set.of(ISOLEMENT));

        assertThat(avec.sallesIsolement()).containsExactly(ISOLEMENT);
        assertThat(avec.joursOuverts()).isEqualTo(base.joursOuverts());
        assertThat(avec.ratio()).isEqualTo(base.ratio());
        assertThat(avec.patientsParPoste()).isEqualTo(base.patientsParPoste());
    }

    @Test
    void should_order_the_days_from_sunday_to_saturday_and_map_them_to_civil_days() {
        assertThat(JourDeLaSemaine.values()[0]).isEqualTo(JourDeLaSemaine.DIMANCHE);
        assertThat(JourDeLaSemaine.DIMANCHE.jourCivil()).isEqualTo(java.time.DayOfWeek.SUNDAY);
        assertThat(JourDeLaSemaine.LUNDI.jourCivil()).isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(JourDeLaSemaine.SAMEDI.jourCivil()).isEqualTo(java.time.DayOfWeek.SATURDAY);
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            assertThat(JourDeLaSemaine.de(jour.jourCivil())).isEqualTo(jour);
        }
    }
}
