package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Demographie;
import com.hemodialyse.backend.contexts.seance.domain.FormatDExport;
import com.hemodialyse.backend.contexts.seance.domain.PatientDuMois;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.TypeDeDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SE7b : le tableau de bord mensuel, son détail nominatif et ses exports, ports en mémoire (ADR 0095). « Aujourd'hui » vaut le
 * 2026-06-15 (horloge du banc, fuseau du centre) ; les mois lus sont passés (janvier 2026) ou futurs (décembre 2026).
 */
class TableauDeBordMensuelCasDUsageTest {

    private static final Set<DayOfWeek> LMV = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    private static final PatientId ADA = new PatientId(new UUID(1, 1));
    private static final PatientId BEA = new PatientId(new UUID(1, 2));
    private static final PatientId ETRANGER = new PatientId(new UUID(1, 3));

    private Banc banc;
    private TableauDeBordEnMemoire donnees;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        donnees = new TableauDeBordEnMemoire();
        donnees.patient(CENTRE_A, new PatientDuMois(ADA, "F", LocalDate.of(1970, 1, 1), null, false, null, null, LMV), "Zola", "Ada");
        donnees.patient(CENTRE_A, new PatientDuMois(BEA, "M", LocalDate.of(1990, 1, 1), null, false, null, null, LMV), "dupont", "Bea");
    }

    private ConsulterLeTableauDeBord tableau() {
        return new ConsulterLeTableauDeBord(banc.session, banc.horloge(), donnees, donnees, donnees, donnees.jours());
    }

    private ConsulterLeDetailDuTableauDeBord detail() {
        return new ConsulterLeDetailDuTableauDeBord(banc.session, banc.horloge(), donnees, donnees, donnees);
    }

    private ExporterLeTableauDeBord exporter() {
        return new ExporterLeTableauDeBord(banc.session, tableau(), (format, t) -> (format + ":" + t.presences()).getBytes());
    }

    @Test
    void should_combine_expected_sessions_presences_absences_and_staff_of_a_past_month() {
        donnees.seance(CENTRE_A, ADA, LocalDate.of(2026, 1, 5), "SIGNEE", new Demographie(ADA, "F", LocalDate.of(1970, 1, 1)));
        donnees.seance(CENTRE_A, ADA, LocalDate.of(2026, 1, 7), "CREE", new Demographie(ADA, "F", LocalDate.of(1970, 1, 1)));
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 1, 9), "A_QUALIFIER");
        donnees.bloquer(CENTRE_A, LocalDate.of(2026, 1, 2));

        var t = tableau().executer(CENTRE_A.valeur(), 2026, 1);

        assertThat(t.seancesPrevues()).isEqualTo(2 * 12);
        assertThat(t.presences()).isEqualTo(1);
        assertThat(t.absences()).isEqualTo(1);
        assertThat(t.seancesTotales()).isEqualTo(1);
        assertThat(t.effectif().patients()).isEqualTo(2);
        assertThat(t.joursBloques()).isEqualTo(1);
        assertThat(t.attendues().patientsConsideres()).isEqualTo(2);
    }

    @Test
    void should_read_the_absences_only_up_to_the_day_of_the_centre_and_none_in_a_future_month() {
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 6, 10), "A_QUALIFIER");
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 6, 20), "A_QUALIFIER");

        var juin = tableau().executer(CENTRE_A.valeur(), 2026, 6);
        assertThat(juin.absences()).isEqualTo(1);
        assertThat(donnees.derniereFinDemandee).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(juin.periodeFin()).isEqualTo(LocalDate.of(2026, 6, 15));

        donnees.lecturesDesAbsences = 0;
        var decembre = tableau().executer(CENTRE_A.valeur(), 2026, 12);
        assertThat(decembre.absences()).isZero();
        assertThat(decembre.seancesPrevues()).isZero();
        assertThat(donnees.lecturesDesAbsences).isZero();
    }

    @Test
    void should_not_count_in_the_running_month_the_absences_dated_after_today_in_the_dashboard_and_the_detail_KB_ABS_35() {
        banc.horlogeA("2026-03-15T10:00:00Z");
        for (int jour : new int[]{10, 15, 16, 28}) donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 3, jour), "A_QUALIFIER");

        var t = tableau().executer(CENTRE_A.valeur(), 2026, 3);
        var d = detail().executer(CENTRE_A.valeur(), 2026, 3, "absence");

        // l'export réutilise le même tableau (ExporterLeTableauDeBord.tableau.pour) : même borne
        assertThat(t.absences()).isEqualTo(2);
        assertThat(t.periodeFin()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(d.lignes()).extracting(l -> l.jour().getDayOfMonth()).containsExactly(10, 15);
    }

    @Test
    void should_use_the_day_of_the_centre_clock_and_not_the_machine_day() {
        banc.horlogeA("2026-01-10T22:30:00Z"); // Alger (UTC+1) : déjà le 2026-01-10 23:30
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 1, 10), "A_QUALIFIER");
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 1, 11), "A_QUALIFIER");

        assertThat(tableau().executer(CENTRE_A.valeur(), 2026, 1).absences()).isEqualTo(1);
    }

    @Test
    void should_count_a_patient_of_another_centre_who_has_a_session_here_in_the_staff_but_not_in_the_detail_KB_SEA_64_65() {
        donnees.seance(CENTRE_A, ETRANGER, LocalDate.of(2026, 1, 5), "FACTUREE", new Demographie(ETRANGER, "F", null));

        var t = tableau().executer(CENTRE_A.valeur(), 2026, 1);
        var lignes = detail().executer(CENTRE_A.valeur(), 2026, 1, "presence").lignes();

        assertThat(t.presences()).isEqualTo(1);
        assertThat(t.effectif().patients()).isEqualTo(3);
        assertThat(lignes).isEmpty();
    }

    @Test
    void should_refuse_another_centre_first_then_a_bad_month() {
        assertThatThrownBy(() -> tableau().executer(CENTRE_B.valeur(), 2026, 13)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> detail().executer(CENTRE_B.valeur(), 2026, 13, "x")).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> exporter().executer(CENTRE_B.valeur(), 2026, 13, "ods")).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> tableau().executer(CENTRE_A.valeur(), 2026, 13)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> tableau().executer(CENTRE_A.valeur(), 1_000_000_000, 1)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_list_presences_sorted_by_day_last_name_first_name_and_drop_patients_of_another_centre() {
        donnees.seance(CENTRE_A, ADA, LocalDate.of(2026, 1, 7), "SIGNEE", new Demographie(ADA, "F", null));
        donnees.seance(CENTRE_A, BEA, LocalDate.of(2026, 1, 7), "VALIDEE", new Demographie(BEA, "M", null));
        donnees.seance(CENTRE_A, BEA, LocalDate.of(2026, 1, 5), "FACTUREE", new Demographie(BEA, "M", null));
        donnees.seance(CENTRE_A, ETRANGER, LocalDate.of(2026, 1, 5), "FACTUREE", new Demographie(ETRANGER, "M", null));

        var d = detail().executer(CENTRE_A.valeur(), 2026, 1, " Presence ");

        assertThat(d.type()).isEqualTo(TypeDeDetail.PRESENCE);
        // KB-SEA-67 : « Zola » avant « dupont »
        assertThat(d.lignes()).extracting(l -> l.nom() + "@" + l.jour().getDayOfMonth()).containsExactly("dupont@5", "Zola@7", "dupont@7");
    }

    @Test
    void should_list_absences_up_to_today_and_nothing_for_a_future_month() {
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 6, 10), "JUSTIFIEE");
        donnees.absence(CENTRE_A, BEA, LocalDate.of(2026, 12, 10), "JUSTIFIEE");

        assertThat(detail().executer(CENTRE_A.valeur(), 2026, 6, "absence").lignes()).hasSize(1);
        assertThat(detail().executer(CENTRE_A.valeur(), 2026, 12, "absence").lignes()).isEmpty();
    }

    @Test
    void should_show_a_patient_both_present_and_absent_the_same_day_KB_SEA_64() {
        var jour = LocalDate.of(2026, 1, 5);
        donnees.seance(CENTRE_A, ADA, jour, "SIGNEE", new Demographie(ADA, "F", null));
        donnees.absence(CENTRE_A, ADA, jour, "A_QUALIFIER");

        assertThat(detail().executer(CENTRE_A.valeur(), 2026, 1, "presence").lignes()).hasSize(1);
        assertThat(detail().executer(CENTRE_A.valeur(), 2026, 1, "absence").lignes()).hasSize(1);
    }

    @Test
    void should_refuse_an_unknown_kind_before_the_month() {
        assertThatThrownBy(() -> detail().executer(CENTRE_A.valeur(), 2026, 13, "autre")).isInstanceOf(SaisieInvalide.class)
                .hasMessage("kind must be 'presence' or 'absence'");
    }

    @Test
    void should_name_the_export_with_the_two_digit_month_and_the_format_extension() {
        var csv = exporter().executer(CENTRE_A.valeur(), 2026, 1, "csv");
        var xlsx = exporter().executer(CENTRE_A.valeur(), 2026, 11, "XLSX");

        assertThat(csv.nomDuFichier()).isEqualTo("seances-dashboard-2026-01.csv");
        assertThat(csv.format()).isEqualTo(FormatDExport.CSV);
        assertThat(new String(csv.contenu())).isEqualTo("CSV:0");
        assertThat(xlsx.nomDuFichier()).isEqualTo("seances-dashboard-2026-11.xlsx");
    }

    @Test
    void should_check_the_export_format_before_the_month() {
        assertThatThrownBy(() -> exporter().executer(CENTRE_A.valeur(), 2026, 13, "ods")).isInstanceOf(SaisieInvalide.class)
                .hasMessage("format must be 'csv', 'pdf' or 'xlsx'");
        assertThatThrownBy(() -> exporter().executer(CENTRE_A.valeur(), 2026, 13, "pdf")).isInstanceOf(SaisieInvalide.class)
                .hasMessage("Mois du tableau de bord hors bornes");
    }
}
