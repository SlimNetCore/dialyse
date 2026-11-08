package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.contrat.VoletsMedicauxEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_A;
import static com.hemodialyse.backend.contexts.seance.application.Banc.CENTRE_B;
import static com.hemodialyse.backend.contexts.seance.application.Banc.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE7a : saisie du volet médical d'une séance, ports en mémoire (ADR 0094, KB-SEA-59). */
class RenseignerLeVoletMedicalTest {

    private Banc banc;
    private VoletsMedicauxEnMemoire volets;
    private PatientId patient;

    @BeforeEach
    void preparer() {
        banc = new Banc();
        banc.session.utilisateur = "u-medecin";
        volets = new VoletsMedicauxEnMemoire();
        patient = banc.patient(CENTRE_A, 1, "P001", "A1");
    }

    private RenseignerLeVoletMedical cas() {
        return new RenseignerLeVoletMedical(banc.session, banc.seances, volets, banc.unite(), banc.identifiants(), banc.horloge());
    }

    private Seance semer(int n, StatutDeSeance statut) {
        var s = banc.ligne(CENTRE_A, patient, n, statut);
        banc.seances.semer(s);
        return s;
    }

    private static ObservationsMedicales complet() {
        return new ObservationsMedicales("Prescription", "Bonne", "RAS", "Normaux", "Aucun", "Stable");
    }

    @Test
    void should_create_the_volet_of_a_validated_session_under_the_row_lock() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        var volet = cas().executer(CENTRE_A.valeur(), s.id().valeur(), () -> complet());

        assertThat(volet.id()).isEqualTo(new UUID(9, 100));
        assertThat(volet.observations()).isEqualTo(complet());
        assertThat(volet.creeLe()).isEqualTo(MAINTENANT);
        assertThat(volets.deLaSeance(CENTRE_A, s.id())).isPresent();
        assertThat(banc.seances.verrousDeLigneDemandes).isEqualTo(1);
        assertThat(banc.diffusionsRecues).isEmpty();
    }

    @Test
    void should_replace_everything_so_that_a_field_left_out_erases_the_value_already_entered_and_keep_one_volet() {
        var s = semer(1, StatutDeSeance.VALIDEE);
        cas().executer(CENTRE_A.valeur(), s.id().valeur(), () -> complet());
        banc.horlogeA("2026-06-15T12:00:00Z");

        var volet = cas().executer(CENTRE_A.valeur(), s.id().valeur(), () -> new ObservationsMedicales(null, null, null, null, null, "Revue"));

        assertThat(volet.id()).isEqualTo(new UUID(9, 100));
        assertThat(volets.nombre()).isEqualTo(1);
        var relu = volets.deLaSeance(CENTRE_A, s.id()).orElseThrow();
        assertThat(relu.observations()).isEqualTo(new ObservationsMedicales(null, null, null, null, null, "Revue"));
        assertThat(relu.creeLe()).isEqualTo(MAINTENANT);
        assertThat(relu.modifieLe()).isEqualTo(java.time.Instant.parse("2026-06-15T12:00:00Z"));
    }

    @Test
    void should_stay_open_on_a_signed_session_as_the_legacy_does() {
        var s = semer(1, StatutDeSeance.SIGNEE);

        cas().executer(CENTRE_A.valeur(), s.id().valeur(), () -> complet());

        assertThat(volets.nombre()).isEqualTo(1);
    }

    @Test
    void should_refuse_an_absent_a_created_and_an_invoiced_session_and_write_nothing() {
        for (var statut : new StatutDeSeance[]{StatutDeSeance.ABSENT, StatutDeSeance.CREE, StatutDeSeance.FACTUREE}) {
            var s = semer(10 + statut.ordinal(), statut);
            assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), s.id().valeur(), () -> complet())).isInstanceOf(SeanceRefusee.class);
        }
        assertThat(volets.nombre()).isZero();
    }

    @Test
    void should_refuse_an_unknown_session_and_a_session_of_another_centre_as_not_found() {
        var autre = banc.ligne(CENTRE_B, banc.patient(CENTRE_B, 2, "P002", "B1"), 5, StatutDeSeance.VALIDEE);
        banc.seances.semer(autre);

        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), new UUID(5, 99), () -> complet())).isInstanceOf(SeanceIntrouvable.class);
        assertThatThrownBy(() -> cas().executer(CENTRE_A.valeur(), autre.id().valeur(), () -> complet())).isInstanceOf(SeanceIntrouvable.class);
        assertThat(volets.nombre()).isZero();
    }

    @Test
    void should_refuse_another_centre_than_the_session_before_reading_the_observations() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        assertThatThrownBy(() -> cas().executer(CENTRE_B.valeur(), s.id().valeur(), () -> {
            throw new SaisieInvalide("ne doit pas être lu");
        })).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_not_cap_the_length_of_an_observation_as_the_legacy() {
        var s = semer(1, StatutDeSeance.VALIDEE);

        cas().executer(CENTRE_A.valeur(), s.id().valeur(), () -> new ObservationsMedicales("x".repeat(100_000), null, null, null, null, null));

        assertThat(volets.deLaSeance(CENTRE_A, s.id()).orElseThrow().observations().prescription()).hasSize(100_000);
    }
}
