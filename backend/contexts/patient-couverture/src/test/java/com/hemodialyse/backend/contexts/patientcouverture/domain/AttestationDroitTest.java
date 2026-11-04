package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles de l'attestation de droits : période obligatoire, bornes incluses, aucun contrôle entre attestations. */
class AttestationDroitTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    private static final LocalDate D = LocalDate.of(2026, 3, 1);
    private static final LocalDate F = LocalDate.of(2026, 3, 31);

    private static AttestationDroit declarer(LocalDate debut, LocalDate fin) {
        return AttestationDroit.declarer(UUID.randomUUID(), PATIENT, CENTRE, debut, fin, T0);
    }

    @Test
    void should_couvrir_un_jour_bornes_incluses() {
        AttestationDroit a = declarer(D, F);

        assertThat(a.couvre(D.minusDays(1))).isFalse();
        assertThat(a.couvre(D)).isTrue();
        assertThat(a.couvre(LocalDate.of(2026, 3, 15))).isTrue();
        assertThat(a.couvre(F)).isTrue();
        assertThat(a.couvre(F.plusDays(1))).isFalse();
    }

    @Test
    void should_accepter_un_seul_jour_une_periode_passee_et_une_periode_future() {
        assertThat(declarer(D, D).couvre(D)).isTrue();
        assertThat(declarer(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 2)).fin()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(declarer(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31)).debut()).isEqualTo(LocalDate.of(2099, 1, 1));
    }

    @Test
    void should_refuser_des_dates_absentes_avant_une_fin_anterieure_au_debut() {
        assertThatThrownBy(() -> declarer(null, F)).isInstanceOf(SaisieInvalide.class).hasMessage("Dates attestation obligatoires");
        assertThatThrownBy(() -> declarer(D, null)).isInstanceOf(SaisieInvalide.class).hasMessage("Dates attestation obligatoires");
        assertThatThrownBy(() -> declarer(F, D)).isInstanceOf(SaisieInvalide.class)
                .hasMessage("La date de début doit être antérieure à la date de fin");
    }

    @Test
    void should_porter_le_message_de_la_fiche_pour_une_fin_avant_le_debut_saisie_sur_la_fiche() {
        assertThatThrownBy(() -> AttestationDroit.saisieSurLaFiche(UUID.randomUUID(), PATIENT, CENTRE, F, D, T0))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Date fin avant date debut");
        assertThatThrownBy(() -> AttestationDroit.saisieSurLaFiche(UUID.randomUUID(), PATIENT, CENTRE, null, D, T0))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Dates attestation obligatoires");
    }

    @Test
    void should_reconnaitre_la_meme_periode_exacte() {
        AttestationDroit a = declarer(D, F);

        assertThat(a.memePeriode(D, F)).isTrue();
        assertThat(a.memePeriode(D, F.plusDays(1))).isFalse();
        assertThat(a.memePeriode(D.plusDays(1), F)).isFalse();
    }

    @Test
    void should_relire_une_ligne_heritee_sans_la_revalider() {
        AttestationDroit inversee = AttestationDroit.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, F, D, null);

        assertThat(inversee.couvre(LocalDate.of(2026, 3, 15))).isFalse();
        assertThat(inversee.creeLe()).isNull();
    }
}
