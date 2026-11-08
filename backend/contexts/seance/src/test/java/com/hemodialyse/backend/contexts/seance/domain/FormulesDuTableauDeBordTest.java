package com.hemodialyse.backend.contexts.seance.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE7b : formules du tableau de bord mensuel (tranches d'âge, sexes, fin de prise en charge, séances attendues, effectif, jours échus). */
class FormulesDuTableauDeBordTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 1, 31);
    private static final Set<DayOfWeek> LUNDI_MERCREDI_VENDREDI = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

    private static PatientDuMois patient(int n, LocalDate admission, boolean sommeil, String etat, LocalDate dateDeLEtat) {
        return new PatientDuMois(new PatientId(new UUID(1, n)), "M", LocalDate.of(1970, 1, 1), admission, sommeil, etat, dateDeLEtat,
                LUNDI_MERCREDI_VENDREDI);
    }

    // ---- Mois

    @ParameterizedTest
    @CsvSource({"2026,0", "2026,13", "2026,-1", "1000000000,1", "-1000000000,1"})
    void should_refuse_a_month_out_of_bounds(int annee, int mois) {
        assertThatThrownBy(() -> new MoisDuTableauDeBord(annee, mois)).isInstanceOf(SaisieInvalide.class);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,1", "9999,12", "2024,2"})
    void should_accept_the_extreme_years_the_legacy_accepted(int annee, int mois) {
        assertThat(new MoisDuTableauDeBord(annee, mois).premierJour().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    void should_know_the_last_day_of_a_leap_february() {
        assertThat(new MoisDuTableauDeBord(2024, 2).dernierJour()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(new MoisDuTableauDeBord(2026, 2).dernierJour()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void should_stop_a_running_month_at_today_and_give_a_future_month_no_elapsed_day() {
        var janvier = new MoisDuTableauDeBord(2026, 1);

        assertThat(janvier.dernierJourEchu(LocalDate.of(2026, 1, 10))).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(janvier.dernierJourEchu(LocalDate.of(2026, 3, 1))).isEqualTo(FIN);
        assertThat(janvier.aUnJourEchu(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(janvier.aUnJourEchu(LocalDate.of(2025, 12, 31))).isFalse();
    }

    // ---- Sexe et tranche d'âge

    @ParameterizedTest
    @CsvSource(value = {"M,M", "H,M", "m,M", "h,M", "' f ',F", "F,F", "X,AUTRE", "'',AUTRE"})
    void should_normalise_the_sex(String brut, Sexe attendu) {
        assertThat(Sexe.depuis(brut)).isEqualTo(attendu);
    }

    @Test
    void should_count_a_missing_sex_as_other() {
        assertThat(Sexe.depuis(null)).isEqualTo(Sexe.AUTRE);
    }

    @ParameterizedTest
    @CsvSource({"2009-01-31,DE_0_A_17", "2008-01-31,DE_18_A_39", "2008-02-01,DE_0_A_17", "1986-02-01,DE_18_A_39", "1986-01-31,DE_40_A_59",
            "1966-02-01,DE_40_A_59", "1966-01-31,DE_60_ET_PLUS", "2030-01-01,DE_0_A_17"})
    void should_place_the_age_at_the_end_of_the_month_in_its_range(LocalDate naissance, TrancheDAge attendue) {
        assertThat(TrancheDAge.pour(naissance, FIN)).isEqualTo(attendue);
    }

    @Test
    void should_give_an_unknown_range_without_birth_date_and_expose_the_published_labels() {
        assertThat(TrancheDAge.pour(null, FIN)).isEqualTo(TrancheDAge.INCONNUE);
        assertThat(List.of(TrancheDAge.values()).stream().map(TrancheDAge::libelle).toList())
                .containsExactly("0-17", "18-39", "40-59", "60+", "INCONNU");
    }

    // ---- Fin de la prise en charge

    @ParameterizedTest
    @CsvSource({"TRANSFERE,2026-01-20,2026-01-20", "GUERRI,2026-01-20,2026-01-20", "DECEDE,2026-01-20,2026-01-19",
            "GREFFE,2026-01-20,2026-01-19", "OCCASIONNEL,2026-01-20,2026-01-20", "VACANCIER_LOCAL,2026-01-20,2026-01-20",
            "VACANCIER_ETRANGER,2026-01-20,2026-01-20"})
    void should_end_the_stay_at_the_event_date_or_the_day_before(String etat, LocalDate evenement, LocalDate attendu) {
        assertThat(FinDeLaPriseEnCharge.dernierJourAttendu(etat, evenement)).contains(attendu);
    }

    @Test
    void should_free_the_place_at_once_when_an_exit_state_has_no_date_and_leave_other_states_unbounded() {
        assertThat(FinDeLaPriseEnCharge.dernierJourAttendu("DECEDE", null)).contains(LocalDate.MIN);
        assertThat(FinDeLaPriseEnCharge.dernierJourAttendu("OCCASIONNEL", null)).isEmpty();
        assertThat(FinDeLaPriseEnCharge.dernierJourAttendu("PERMANENT", LocalDate.of(2026, 1, 1))).isEqualTo(Optional.empty());
        assertThat(FinDeLaPriseEnCharge.dernierJourAttendu(null, null)).isEmpty();
    }

    // ---- Séances attendues

    @Test
    void should_expect_a_session_on_every_dialysis_day_of_a_permanent_patient() {
        // janvier 2026 (le 1er est un jeudi) : 4 lundis, 4 mercredis, 5 vendredis
        var attendues = SeancesAttendues.calculer(List.of(patient(1, null, false, null, null)), DEBUT, FIN, Set.of());

        assertThat(attendues.prevues()).isEqualTo(13);
        assertThat(attendues.patientsConsideres()).isEqualTo(1);
    }

    @Test
    void should_skip_blocked_days_and_sleeping_patients() {
        var bloques = Set.of(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5));
        var attendues = SeancesAttendues.calculer(List.of(patient(1, null, false, null, null), patient(2, null, true, null, null)),
                DEBUT, FIN, bloques);

        assertThat(attendues.prevues()).isEqualTo(11);
        assertThat(attendues.patientsConsideres()).isEqualTo(1);
    }

    @Test
    void should_start_at_the_admission_when_it_falls_inside_the_month_and_ignore_a_later_admission() {
        var attendues = SeancesAttendues.calculer(List.of(patient(1, LocalDate.of(2026, 1, 26), false, null, null),
                patient(2, LocalDate.of(2026, 2, 1), false, null, null)), DEBUT, FIN, Set.of());

        // 26 (lundi), 28 (mercredi), 30 (vendredi) : 3 séances ; le second patient n'est pas encore là
        assertThat(attendues.prevues()).isEqualTo(3);
        assertThat(attendues.patientsConsideres()).isEqualTo(1);
    }

    @Test
    void should_stop_at_the_end_of_the_stay_and_never_expect_a_patient_whose_place_is_already_free() {
        var attendues = SeancesAttendues.calculer(List.of(patient(1, null, false, "TRANSFERE", LocalDate.of(2026, 1, 7)),
                patient(2, null, false, "DECEDE", null), patient(3, null, false, "DECEDE", LocalDate.of(2026, 1, 7))), DEBUT, FIN, Set.of());

        // patient 1 jusqu'au 7 inclus : vendredi 2, lundi 5, mercredi 7 ; patient 3 (décédé le 7) jusqu'au 6 : vendredi 2, lundi 5
        assertThat(attendues.prevues()).isEqualTo(3 + 2);
        assertThat(attendues.patientsConsideres()).isEqualTo(2);
    }

    @Test
    void should_expect_nothing_when_the_period_has_no_elapsed_day() {
        var attendues = SeancesAttendues.calculer(List.of(patient(1, null, false, null, null)), DEBUT, DEBUT.minusDays(1), Set.of());

        assertThat(attendues.prevues()).isZero();
        assertThat(attendues.patientsConsideres()).isZero();
    }

    @Test
    void should_keep_the_dead_french_weekday_keys_at_zero_and_count_on_the_english_keys() {
        var attendues = SeancesAttendues.calculer(List.of(patient(1, null, false, null, null)), DEBUT, FIN, Set.of());

        assertThat(attendues.parJourDeSemaine()).containsEntry("LUNDI", 0L).containsEntry("MERCREDI", 0L).containsEntry("DIMANCHE", 0L)
                .containsEntry("MONDAY", 4L).containsEntry("WEDNESDAY", 4L).containsEntry("FRIDAY", 5L).hasSize(10);
    }

    @Test
    void should_carry_only_the_seven_french_keys_when_nobody_is_expected() {
        var attendues = SeancesAttendues.calculer(List.of(), DEBUT, FIN, Set.of());

        assertThat(attendues.parJourDeSemaine().keySet()).containsExactly("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI",
                "DIMANCHE");
    }

    // ---- Effectif et répartitions

    @Test
    void should_count_the_staff_of_the_month_once_and_add_the_patients_with_a_performed_session() {
        var present = new PatientDuMois(new PatientId(new UUID(1, 9)), "F", LocalDate.of(1950, 1, 1), null, true, null, null, Set.of());
        var patients = List.of(patient(1, null, false, null, null), patient(2, null, true, null, null),
                patient(3, LocalDate.of(2026, 2, 1), false, null, null), patient(4, null, false, "GUERRI", null), present);

        var effectif = EffectifDuMois.composer(patients, List.of(new Demographie(present.id(), "F", LocalDate.of(1950, 1, 1)),
                new Demographie(patient(1, null, false, null, null).id(), "M", LocalDate.of(1970, 1, 1))), DEBUT, FIN);

        // 1 : pris en charge ; 2 : en sommeil, absent ; 3 : admis le mois suivant ; 4 : place déjà libérée ; 9 : présent (sommeil) ajouté
        assertThat(effectif.patients()).isEqualTo(2);
        assertThat(effectif.parSexe()).containsExactly(Map.entry("M", 1L), Map.entry("F", 1L), Map.entry("AUTRE", 0L));
        assertThat(effectif.parTranche()).containsEntry("60+", 1L).containsEntry("40-59", 1L);
    }

    @Test
    void should_count_a_patient_of_another_centre_who_has_a_session_here_as_present_KB_SEA_65() {
        var etranger = new Demographie(new PatientId(new UUID(8, 8)), "x", null);

        var effectif = EffectifDuMois.composer(new ArrayList<>(), List.of(etranger), DEBUT, FIN);

        assertThat(effectif.patients()).isEqualTo(1);
        assertThat(effectif.parSexe()).containsEntry("AUTRE", 1L);
        assertThat(effectif.parTranche()).containsEntry("INCONNU", 1L);
    }

    // ---- Tableau

    @Test
    void should_total_the_sessions_as_the_presences_and_never_end_the_period_before_its_start() {
        var mois = new MoisDuTableauDeBord(2026, 3);
        var tableau = TableauDeBordDesSeances.composer(mois, LocalDate.of(2026, 1, 10), 7, 0, SeancesAttendues.calculer(List.of(), DEBUT, FIN,
                Set.of()), EffectifDuMois.composer(List.of(), List.of(), DEBUT, FIN), 0);

        assertThat(tableau.seancesTotales()).isEqualTo(7);
        assertThat(tableau.periodeDebut()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(tableau.periodeFin()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    // ---- Détail et export

    @ParameterizedTest
    @ValueSource(strings = {"presence", "PRESENCE", "  Presence "})
    void should_read_the_presence_kind_without_case_or_spaces(String brut) {
        assertThat(TypeDeDetail.lire(brut)).isEqualTo(TypeDeDetail.PRESENCE);
        assertThat(TypeDeDetail.lire(brut).code()).isEqualTo("presence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "absences", "autre", "  "})
    void should_refuse_an_unknown_kind(String brut) {
        assertThatThrownBy(() -> TypeDeDetail.lire(brut)).isInstanceOf(SaisieInvalide.class).hasMessage("kind must be 'presence' or 'absence'");
    }

    @Test
    void should_sort_the_lines_by_day_then_last_name_then_first_name_by_character_order() {
        var p = new PatientId(new UUID(1, 1));
        var jour = LocalDate.of(2026, 1, 5);
        List<LigneDuDetail> lignes = new ArrayList<>(List.of(
                new LigneDuDetail(p, "dupont", "Anne", jour, "SIGNEE"),
                new LigneDuDetail(p, "Zola", "Anne", jour, "SIGNEE"),
                new LigneDuDetail(p, "Zola", "Alice", jour, "SIGNEE"),
                new LigneDuDetail(p, "Zola", "Zoe", jour.minusDays(1), "SIGNEE")));

        lignes.sort(LigneDuDetail.ORDRE);

        // KB-SEA-67 : « Zola » avant « dupont » (les majuscules précèdent les minuscules)
        assertThat(lignes).extracting(l -> l.nom() + "/" + l.prenom()).containsExactly("Zola/Zoe", "Zola/Alice", "Zola/Anne", "dupont/Anne");
    }

    @ParameterizedTest
    @CsvSource({"csv,CSV", "CSV,CSV", "Pdf,PDF", "xlsx,XLSX"})
    void should_read_the_export_format_without_case(String brut, FormatDExport attendu) {
        assertThat(FormatDExport.lire(brut)).isEqualTo(attendu);
    }

    @Test
    void should_refuse_an_unknown_export_format_and_announce_no_charset_for_the_csv() {
        assertThatThrownBy(() -> FormatDExport.lire("ods")).isInstanceOf(SaisieInvalide.class)
                .hasMessage("format must be 'csv', 'pdf' or 'xlsx'");
        assertThat(FormatDExport.CSV.typeDeContenu()).isEqualTo("text/plain");
    }
}
