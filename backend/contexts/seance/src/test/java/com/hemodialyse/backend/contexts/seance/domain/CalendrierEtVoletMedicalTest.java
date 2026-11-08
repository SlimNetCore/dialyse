package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles du domaine de SE7a : calendrier du centre, volet médical, séances récentes (ADR 0094). Aucun double, aucune infrastructure. */
class CalendrierEtVoletMedicalTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final Instant T = Instant.parse("2026-06-15T10:00:00Z");

    private static Seance seance(StatutDeSeance statut) {
        return Seance.restituer(new SeanceId(new UUID(2, 1)), CENTRE, new PatientId(new UUID(1, 1)), LocalDate.of(2026, 6, 15), statut, T, null,
                null);
    }

    // ---- calendrier du centre

    @Test
    void should_accept_a_holiday_without_label_and_a_label_of_255_characters_and_a_day_in_1900() {
        assertThatCode(() -> new JourDuCalendrier(new UUID(3, 1), CENTRE, LocalDate.of(2026, 1, 15), NatureDeJour.JOUR_FERIE, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> new JourDuCalendrier(new UUID(3, 1), CENTRE, LocalDate.of(1900, 1, 1), NatureDeJour.FERMETURE_EXCEPTIONNELLE,
                "m".repeat(255))).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_a_calendar_day_without_date_or_with_a_label_longer_than_the_column() {
        assertThatThrownBy(() -> new JourDuCalendrier(new UUID(3, 1), CENTRE, null, NatureDeJour.JOUR_FERIE, "x"))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Jour requis");
        assertThatThrownBy(() -> new JourDuCalendrier(new UUID(3, 1), CENTRE, LocalDate.of(2026, 1, 15), NatureDeJour.JOUR_FERIE, "m".repeat(256)))
                .isInstanceOf(SaisieInvalide.class).hasMessageContaining("255");
        assertThatThrownBy(() -> new JourDuCalendrier(new UUID(3, 1), null, LocalDate.of(2026, 1, 15), NatureDeJour.JOUR_FERIE, "x"))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> new JourDuCalendrier(new UUID(3, 1), CENTRE, LocalDate.of(2026, 1, 15), null, "x"))
                .isInstanceOf(SaisieInvalide.class);
    }

    @ParameterizedTest
    @CsvSource({"2026,1,2026-01-01,2026-01-31", "2026,2,2026-02-01,2026-02-28", "2028,2,2028-02-01,2028-02-29", "0,12,0000-12-01,0000-12-31"})
    void should_bound_a_month_by_its_first_and_last_day_included(int annee, int mois, String premier, String dernier) {
        var m = new MoisDuCalendrier(annee, mois);

        assertThat(m.premierJour()).isEqualTo(LocalDate.parse(premier));
        assertThat(m.dernierJour()).isEqualTo(LocalDate.parse(dernier));
    }

    @ParameterizedTest
    @CsvSource({"2026,0", "2026,13", "2026,-1", "1000000000,1", "-1000000000,1"})
    void should_refuse_a_month_out_of_bounds_with_a_bad_request_not_a_server_error(int annee, int mois) {
        assertThatThrownBy(() -> new MoisDuCalendrier(annee, mois)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void should_name_the_nature_of_the_duplicate_day_in_the_message() {
        assertThat(new JourDejaAuCalendrier(NatureDeJour.JOUR_FERIE, LocalDate.of(2026, 1, 15))).hasMessageContaining("jour férié");
        assertThat(new JourDejaAuCalendrier(NatureDeJour.FERMETURE_EXCEPTIONNELLE, LocalDate.of(2026, 1, 15)))
                .hasMessageContaining("fermeture exceptionnelle");
    }

    // ---- volet médical

    @ParameterizedTest
    @CsvSource({"VALIDEE", "SIGNEE"})
    void should_accept_a_medical_volet_on_a_validated_or_even_signed_session(StatutDeSeance statut) {
        assertThatCode(() -> seance(statut).exigerUnVoletMedical()).doesNotThrowAnyException();
    }

    @Test
    void should_refuse_a_medical_volet_on_an_absent_a_created_or_an_invoiced_session_with_the_legacy_codes() {
        assertThatThrownBy(() -> seance(StatutDeSeance.ABSENT).exigerUnVoletMedical()).isInstanceOfSatisfying(SeanceRefusee.class, e -> {
            assertThat(e.motif().code()).isEqualTo("BUSINESS_RULE_VIOLATION");
            assertThat(e).hasMessage("La seance exclue de la facturation (patient absent) n'a pas de volet medical");
        });
        assertThatThrownBy(() -> seance(StatutDeSeance.CREE).exigerUnVoletMedical()).isInstanceOfSatisfying(SeanceRefusee.class, e -> {
            assertThat(e.motif().code()).isEqualTo("BUSINESS_RULE_VIOLATION");
            assertThat(e).hasMessage("Le volet medical n'est accessible qu'apres validation infirmiere");
        });
        assertThatThrownBy(() -> seance(StatutDeSeance.FACTUREE).exigerUnVoletMedical()).isInstanceOfSatisfying(SeanceRefusee.class, e -> {
            assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE");
            assertThat(e).hasMessage("La seance facturee ne peut plus etre modifiee");
        });
    }

    @Test
    void should_replace_every_observation_so_that_an_absent_one_erases_the_value_already_entered() {
        var volet = VoletMedical.ouvrir(new UUID(8, 1), CENTRE, new SeanceId(new UUID(2, 1)),
                new ObservationsMedicales("P", "T", "E", "R", "A", "C"), T);

        volet.remplacerLesObservations(new ObservationsMedicales(null, null, null, null, null, "Revue"), T.plusSeconds(60));

        assertThat(volet.observations()).isEqualTo(new ObservationsMedicales(null, null, null, null, null, "Revue"));
        assertThat(volet.creeLe()).isEqualTo(T);
        assertThat(volet.modifieLe()).isEqualTo(T.plusSeconds(60));
        assertThat(volet.id()).isEqualTo(new UUID(8, 1));
    }

    @Test
    void should_accept_an_observation_of_any_length_as_the_legacy_does_the_column_being_unbounded_text() {
        assertThatCode(() -> new ObservationsMedicales("x".repeat(20_001), null, null, "y".repeat(500_000), null, null))
                .doesNotThrowAnyException();
    }

    // ---- séances récentes

    @ParameterizedTest
    @CsvSource({"-5,1", "0,1", "1,1", "3,3", "10,10", "11,10", "1000,10"})
    void should_bring_the_requested_number_back_into_one_to_ten_without_any_error(int demande, int attendu) {
        assertThat(NombreDeSeancesRecentes.demande(demande).valeur()).isEqualTo(attendu);
    }
}
