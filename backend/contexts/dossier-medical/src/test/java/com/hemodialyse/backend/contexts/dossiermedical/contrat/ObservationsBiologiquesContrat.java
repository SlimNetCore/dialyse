package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SourceDObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurMesuree;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link ObservationsBiologiques} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class ObservationsBiologiquesContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6002"));
    static final UUID DEMANDE = UUID.fromString("00000000-0000-0000-0000-0000000d6d01");
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);

    protected abstract ObservationsBiologiques observations();

    static ObservationBiologique observation(CentreId centre, PatientId patient, String code, LocalDate prelevement, Instant creee) {
        return ObservationBiologique.enregistrer(UUID.randomUUID(), patient, centre, null, CodeClinique.saisir(SystemeDeCodage.LOINC, code, null),
                new ValeurMesuree(new BigDecimal("12.5"), "g/dL"), null, prelevement, null, AUJOURDHUI, creee);
    }

    @Test
    void une_observation_ajoutee_est_relue_avec_tous_ses_champs() {
        var o = ObservationBiologique.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, DEMANDE,
                CodeClinique.saisir(SystemeDeCodage.LOINC, "718-7", "Hémoglobine"), null, "Indosable", LocalDate.of(2026, 1, 5),
                StatutDObservation.PRELIMINAIRE, AUJOURDHUI, T0);
        observations().ajouter(o);
        var relue = observations().trouverEnVerrouillant(CENTRE_A, PATIENT, o.id()).orElseThrow();
        assertThat(relue.analyte().systeme()).isEqualTo(SystemeDeCodage.LOINC);
        assertThat(relue.analyte().code()).isEqualTo("718-7");
        assertThat(relue.analyte().affichage()).isEqualTo("Hémoglobine");
        assertThat(relue.valeurNumerique()).isNull();
        assertThat(relue.valeurTexte()).isEqualTo("Indosable");
        assertThat(relue.datePrelevement()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(relue.statut()).isEqualTo(StatutDObservation.PRELIMINAIRE);
        assertThat(relue.source()).isEqualTo(SourceDObservation.SAISIE_DIRECTE);
        assertThat(relue.demandeDExamen()).isEqualTo(DEMANDE);
        assertThat(relue.creeLe()).isEqualTo(T0);
        assertThat(relue.modifieLe()).isEqualTo(T0);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
        assertThat(relue.patient()).isEqualTo(PATIENT);
    }

    @Test
    void une_valeur_numerique_garde_son_unite_et_ses_quatre_decimales() {
        var o = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0);
        observations().ajouter(o);
        var relue = observations().trouverEnVerrouillant(CENTRE_A, PATIENT, o.id()).orElseThrow();
        assertThat(relue.valeurNumerique().valeur()).isEqualByComparingTo("12.5");
        assertThat(relue.valeurNumerique().unite()).isEqualTo("g/dL");
        assertThat(relue.valeurTexte()).isNull();
    }

    @Test
    void une_observation_est_cherchee_par_identifiant_patient_et_centre() {
        var o = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0);
        observations().ajouter(o);
        assertThat(observations().trouverEnVerrouillant(CENTRE_B, PATIENT, o.id())).isEmpty();
        assertThat(observations().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, o.id())).isEmpty();
        assertThat(observations().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_correction_remplace_la_valeur_passe_le_statut_a_corrige_et_date_la_modification() {
        var o = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0);
        observations().ajouter(o);
        var chargee = observations().trouverEnVerrouillant(CENTRE_A, PATIENT, o.id()).orElseThrow();
        chargee.corriger(null, "Indosable", T0.plusSeconds(60));
        observations().enregistrerLaCorrection(chargee);
        var relue = observations().trouverEnVerrouillant(CENTRE_A, PATIENT, o.id()).orElseThrow();
        assertThat(relue.valeurNumerique()).isNull();
        assertThat(relue.valeurTexte()).isEqualTo("Indosable");
        assertThat(relue.statut()).isEqualTo(StatutDObservation.CORRIGE);
        assertThat(relue.modifieLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.creeLe()).isEqualTo(T0);
        assertThat(relue.analyte().code()).isEqualTo("718-7");
        assertThat(relue.datePrelevement()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var o = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0);
        var autre = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 6), T0);
        observations().ajouter(o);
        observations().ajouter(autre);
        assertThat(observations().supprimer(CENTRE_B, PATIENT, o.id())).isFalse();
        assertThat(observations().supprimer(CENTRE_A, AUTRE_PATIENT, o.id())).isFalse();
        assertThat(observations().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(observations().supprimer(CENTRE_A, PATIENT, o.id())).isTrue();
        assertThat(observations().supprimer(CENTRE_A, PATIENT, o.id())).isFalse();
        assertThat(observations().trouverEnVerrouillant(CENTRE_A, PATIENT, autre.id())).isPresent();
    }

    @Test
    void deux_envois_identiques_creent_deux_observations() {
        observations().ajouter(observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0));
        observations().ajouter(observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0));
        assertThat(observations().duPatient(CENTRE_A, PATIENT, null, null, null, 0, 10).total()).isEqualTo(2);
    }

    @Test
    void la_liste_est_triee_du_prelevement_le_plus_recent_au_plus_ancien_et_paginee_par_patient_et_centre() {
        for (int jour = 1; jour <= 5; jour++) {
            observations().ajouter(observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, jour), T0));
        }
        observations().ajouter(observation(CENTRE_A, AUTRE_PATIENT, "718-7", LocalDate.of(2026, 1, 9), T0));
        observations().ajouter(observation(CENTRE_B, PATIENT, "718-7", LocalDate.of(2026, 1, 9), T0));
        var premiere = observations().duPatient(CENTRE_A, PATIENT, null, null, null, 0, 2);
        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.items()).extracting(ObservationBiologique::datePrelevement)
                .containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
        assertThat(observations().duPatient(CENTRE_A, PATIENT, null, null, null, 2, 2).items())
                .extracting(ObservationBiologique::datePrelevement).containsExactly(LocalDate.of(2026, 1, 1));
        assertThat(observations().duPatient(CENTRE_A, PATIENT, null, null, null, 9, 2).items()).isEmpty();
        assertThat(observations().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), null, null, null, 0, 2).total()).isZero();
    }

    @Test
    void a_prelevement_egal_la_derniere_creee_vient_en_premier() {
        var premiere = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0);
        var seconde = observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0.plusSeconds(5));
        observations().ajouter(premiere);
        observations().ajouter(seconde);
        assertThat(observations().duPatient(CENTRE_A, PATIENT, null, null, null, 0, 10).items()).extracting(ObservationBiologique::id)
                .containsExactly(seconde.id(), premiere.id());
    }

    @Test
    void le_code_est_une_egalite_stricte_et_la_periode_a_ses_bornes_incluses() {
        observations().ajouter(observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 1), T0));
        observations().ajouter(observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 5), T0));
        observations().ajouter(observation(CENTRE_A, PATIENT, "2160-0", LocalDate.of(2026, 1, 9), T0));
        assertThat(observations().duPatient(CENTRE_A, PATIENT, "718-7", null, null, 0, 10).total()).isEqualTo(2);
        assertThat(observations().duPatient(CENTRE_A, PATIENT, "718-%", null, null, 0, 10).total()).isZero();
        assertThat(observations().duPatient(CENTRE_A, PATIENT, " ", null, null, 0, 10).total()).isEqualTo(3);
        assertThat(observations().duPatient(CENTRE_A, PATIENT, null, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9), 0, 10).total()).isEqualTo(2);
        assertThat(observations().duPatient(CENTRE_A, PATIENT, null, LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 1), 0, 10).total()).isZero();
    }

    @Test
    void les_observations_d_une_demande_sont_celles_du_patient_et_du_centre_rattachees_a_cette_demande() {
        var rattachee = ObservationBiologique.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, DEMANDE,
                CodeClinique.saisir(SystemeDeCodage.LOINC, "718-7", null), new ValeurMesuree(BigDecimal.ONE, "u"), null, LocalDate.of(2026, 1, 5), null,
                AUJOURDHUI, T0);
        var autrePatient = ObservationBiologique.enregistrer(UUID.randomUUID(), AUTRE_PATIENT, CENTRE_A, DEMANDE,
                CodeClinique.saisir(SystemeDeCodage.LOINC, "718-7", null), new ValeurMesuree(BigDecimal.ONE, "u"), null, LocalDate.of(2026, 1, 5), null,
                AUJOURDHUI, T0);
        observations().ajouter(rattachee);
        observations().ajouter(autrePatient);
        observations().ajouter(observation(CENTRE_A, PATIENT, "718-7", LocalDate.of(2026, 1, 6), T0));
        assertThat(observations().deLaDemande(CENTRE_A, PATIENT, DEMANDE)).extracting(ObservationBiologique::id).containsExactly(rattachee.id());
        assertThat(observations().deLaDemande(CENTRE_B, PATIENT, DEMANDE)).isEmpty();
        assertThat(observations().deLaDemande(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }
}
