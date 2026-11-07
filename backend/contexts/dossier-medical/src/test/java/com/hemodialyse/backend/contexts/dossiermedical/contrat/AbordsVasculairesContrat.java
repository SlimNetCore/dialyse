package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link AbordsVasculaires} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class AbordsVasculairesContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7002"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract AbordsVasculaires abords();

    static AbordVasculaire abord(CentreId centre, PatientId patient, LocalDate creation, Instant enregistre) {
        return AbordVasculaire.enregistrer(UUID.randomUUID(), patient, centre, "FAV", null, null, creation, null, null, null, enregistre);
    }

    @Test
    void un_abord_ajoute_est_relu_avec_tous_ses_champs() {
        var a = AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, "KT_AIGU", "GAUCHE", "Jugulaire fictive", LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 5), false, "Complication fictive", T0);
        abords().ajouter(a);
        var relu = abords().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relu.type()).isEqualTo("KT_AIGU");
        assertThat(relu.cote()).isEqualTo("GAUCHE");
        assertThat(relu.localisation()).isEqualTo("Jugulaire fictive");
        assertThat(relu.creation()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(relu.fin()).isEqualTo(LocalDate.of(2026, 2, 5));
        assertThat(relu.actif()).isFalse();
        assertThat(relu.complications()).isEqualTo("Complication fictive");
        assertThat(relu.enregistreLe()).isEqualTo(T0);
        assertThat(relu.centre()).isEqualTo(CENTRE_A);
        assertThat(relu.patient()).isEqualTo(PATIENT);
    }

    @Test
    void un_abord_est_cherche_par_identifiant_patient_et_centre_jamais_par_identifiant_seul() {
        var a = abord(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        abords().ajouter(a);
        assertThat(abords().trouverEnVerrouillant(CENTRE_B, PATIENT, a.id())).isEmpty();
        assertThat(abords().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, a.id())).isEmpty();
        assertThat(abords().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void le_remplacement_est_enregistre_sans_changer_de_patient_ni_de_centre() {
        var a = AbordVasculaire.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, "FAV", "GAUCHE", "Avant-bras", LocalDate.of(2026, 1, 5), null, true,
                "x", T0);
        abords().ajouter(a);
        var chargee = abords().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        chargee.remplacer("PTFE", null, null, LocalDate.of(2026, 3, 1), null, false, null, T0.plusSeconds(60));
        abords().enregistrerLeRemplacement(chargee);
        var relu = abords().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relu.type()).isEqualTo("PTFE");
        assertThat(relu.cote()).isNull();
        assertThat(relu.localisation()).isNull();
        assertThat(relu.complications()).isNull();
        assertThat(relu.actif()).isFalse();
        assertThat(relu.creation()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(relu.enregistreLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relu.patient()).isEqualTo(PATIENT);
        assertThat(relu.centre()).isEqualTo(CENTRE_A);
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var a = abord(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        var autre = abord(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 6), T0);
        abords().ajouter(a);
        abords().ajouter(autre);
        assertThat(abords().supprimer(CENTRE_B, PATIENT, a.id())).isFalse();
        assertThat(abords().supprimer(CENTRE_A, AUTRE_PATIENT, a.id())).isFalse();
        assertThat(abords().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(abords().supprimer(CENTRE_A, PATIENT, a.id())).isTrue();
        assertThat(abords().supprimer(CENTRE_A, PATIENT, a.id())).isFalse();
        assertThat(abords().trouverEnVerrouillant(CENTRE_A, PATIENT, autre.id())).isPresent();
    }

    @Test
    void la_liste_est_triee_de_la_creation_la_plus_recente_a_la_plus_ancienne_et_paginee_par_patient_et_centre() {
        for (int jour = 1; jour <= 5; jour++) {
            abords().ajouter(abord(CENTRE_A, PATIENT, LocalDate.of(2026, 1, jour), T0));
        }
        abords().ajouter(abord(CENTRE_A, AUTRE_PATIENT, LocalDate.of(2026, 1, 9), T0));
        abords().ajouter(abord(CENTRE_B, PATIENT, LocalDate.of(2026, 1, 9), T0));
        var premiere = abords().duPatient(CENTRE_A, PATIENT, 0, 2);
        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.items()).extracting(AbordVasculaire::creation).containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
        assertThat(abords().duPatient(CENTRE_A, PATIENT, 2, 2).items()).extracting(AbordVasculaire::creation)
                .containsExactly(LocalDate.of(2026, 1, 1));
        assertThat(abords().duPatient(CENTRE_A, PATIENT, 9, 2).items()).isEmpty();
        assertThat(abords().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 2).total()).isZero();
    }

    @Test
    void les_abords_sans_date_viennent_en_dernier_sur_tous_les_sgbd_puis_le_dernier_enregistre_en_premier() {
        var sansDate = abord(CENTRE_A, PATIENT, null, T0);
        var sansDatePlusRecent = abord(CENTRE_A, PATIENT, null, T0.plusSeconds(5));
        var date = abord(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        abords().ajouter(sansDate);
        abords().ajouter(sansDatePlusRecent);
        abords().ajouter(date);
        assertThat(abords().duPatient(CENTRE_A, PATIENT, 0, 10).items()).extracting(AbordVasculaire::id)
                .containsExactly(date.id(), sansDatePlusRecent.id(), sansDate.id());
    }
}
