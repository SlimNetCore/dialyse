package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CriticiteDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeVerification;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeReaction;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Allergies} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class AllergiesContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d6b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d6002"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);

    protected abstract Allergies allergies();

    static Allergie allergie(CentreId centre, PatientId patient, CriticiteDAllergie criticite, LocalDate date, Instant creee) {
        return Allergie.declarer(UUID.randomUUID(), patient, centre, CodeClinique.saisir(SystemeDeCodage.LOCAL, "latex", null),
                CategorieDAllergie.ENVIRONNEMENT, criticite, TypeDeReaction.ALLERGIE, criticite == CriticiteDAllergie.HAUTE ? "Choc" : null, date,
                null, AUJOURDHUI, creee);
    }

    @Test
    void une_allergie_ajoutee_est_relue_avec_tous_ses_champs() {
        var a = Allergie.declarer(UUID.randomUUID(), PATIENT, CENTRE_A, CodeClinique.saisir(SystemeDeCodage.ATC, "j01ca04", "Amoxicilline"),
                CategorieDAllergie.MEDICAMENT, CriticiteDAllergie.HAUTE, TypeDeReaction.INTOLERANCE, "Choc fictif", LocalDate.of(2026, 1, 5),
                StatutDeVerification.CONFIRMEE, AUJOURDHUI, T0);
        allergies().ajouter(a);
        var relue = allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relue.substance().systeme()).isEqualTo(SystemeDeCodage.ATC);
        assertThat(relue.substance().code()).isEqualTo("J01CA04");
        assertThat(relue.substance().affichage()).isEqualTo("Amoxicilline");
        assertThat(relue.categorie()).isEqualTo(CategorieDAllergie.MEDICAMENT);
        assertThat(relue.criticite()).isEqualTo(CriticiteDAllergie.HAUTE);
        assertThat(relue.typeDeReaction()).isEqualTo(TypeDeReaction.INTOLERANCE);
        assertThat(relue.manifestations()).isEqualTo("Choc fictif");
        assertThat(relue.dateDeConstatation()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(relue.statut()).isEqualTo(StatutDeVerification.CONFIRMEE);
        assertThat(relue.creeLe()).isEqualTo(T0);
        assertThat(relue.modifieLe()).isEqualTo(T0);
        assertThat(relue.centre()).isEqualTo(CENTRE_A);
        assertThat(relue.patient()).isEqualTo(PATIENT);
    }

    @Test
    void une_allergie_est_cherchee_par_identifiant_patient_et_centre() {
        var a = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 5), T0);
        allergies().ajouter(a);
        assertThat(allergies().trouverEnVerrouillant(CENTRE_B, PATIENT, a.id())).isEmpty();
        assertThat(allergies().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, a.id())).isEmpty();
        assertThat(allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_correction_ne_change_que_la_criticite_les_manifestations_le_statut_et_la_date_de_modification() {
        var a = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.HAUTE, LocalDate.of(2026, 1, 5), T0);
        allergies().ajouter(a);
        var chargee = allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        chargee.corriger(CriticiteDAllergie.BASSE, null, StatutDeVerification.REFUTEE, T0.plusSeconds(60));
        allergies().enregistrerLaCorrection(chargee);
        var relue = allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relue.criticite()).isEqualTo(CriticiteDAllergie.BASSE);
        assertThat(relue.manifestations()).isNull();
        assertThat(relue.statut()).isEqualTo(StatutDeVerification.REFUTEE);
        assertThat(relue.modifieLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relue.creeLe()).isEqualTo(T0);
        assertThat(relue.substance().code()).isEqualTo("LATEX");
        assertThat(relue.categorie()).isEqualTo(CategorieDAllergie.ENVIRONNEMENT);
        assertThat(relue.dateDeConstatation()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var a = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 5), T0);
        var autre = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 6), T0);
        allergies().ajouter(a);
        allergies().ajouter(autre);
        assertThat(allergies().supprimer(CENTRE_B, PATIENT, a.id())).isFalse();
        assertThat(allergies().supprimer(CENTRE_A, AUTRE_PATIENT, a.id())).isFalse();
        assertThat(allergies().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(allergies().supprimer(CENTRE_A, PATIENT, a.id())).isTrue();
        assertThat(allergies().supprimer(CENTRE_A, PATIENT, a.id())).isFalse();
        assertThat(allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, autre.id())).isPresent();
    }

    @Test
    void la_meme_substance_peut_etre_declaree_plusieurs_fois() {
        allergies().ajouter(allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 5), T0));
        allergies().ajouter(allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 5), T0));
        assertThat(allergies().duPatient(CENTRE_A, PATIENT, 0, 10).total()).isEqualTo(2);
    }

    @Test
    void la_liste_est_triee_de_la_constatation_la_plus_recente_a_la_plus_ancienne_et_paginee_par_patient_et_centre() {
        for (int jour = 1; jour <= 5; jour++) {
            allergies().ajouter(allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, jour), T0));
        }
        allergies().ajouter(allergie(CENTRE_A, AUTRE_PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 9), T0));
        allergies().ajouter(allergie(CENTRE_B, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 9), T0));
        var premiere = allergies().duPatient(CENTRE_A, PATIENT, 0, 2);
        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.items()).extracting(Allergie::dateDeConstatation).containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
        assertThat(allergies().duPatient(CENTRE_A, PATIENT, 2, 2).items()).extracting(Allergie::dateDeConstatation)
                .containsExactly(LocalDate.of(2026, 1, 1));
        assertThat(allergies().duPatient(CENTRE_A, PATIENT, 9, 2).items()).isEmpty();
        assertThat(allergies().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 2).total()).isZero();
    }

    @Test
    void a_constatation_egale_la_derniere_creee_vient_en_premier() {
        var premiere = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 5), T0);
        var seconde = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 5), T0.plusSeconds(5));
        allergies().ajouter(premiere);
        allergies().ajouter(seconde);
        assertThat(allergies().duPatient(CENTRE_A, PATIENT, 0, 10).items()).extracting(Allergie::id).containsExactly(seconde.id(), premiere.id());
    }

    @Test
    void les_critiques_sont_les_allergies_de_criticite_haute_du_patient_et_du_centre_meme_refutees() {
        var haute = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.HAUTE, LocalDate.of(2026, 1, 5), T0);
        var refutee = allergie(CENTRE_A, PATIENT, CriticiteDAllergie.HAUTE, LocalDate.of(2026, 1, 6), T0);
        refutee.corriger(CriticiteDAllergie.HAUTE, "Choc", StatutDeVerification.REFUTEE, T0);
        allergies().ajouter(haute);
        allergies().ajouter(refutee);
        allergies().ajouter(allergie(CENTRE_A, PATIENT, CriticiteDAllergie.BASSE, LocalDate.of(2026, 1, 7), T0));
        allergies().ajouter(allergie(CENTRE_A, AUTRE_PATIENT, CriticiteDAllergie.HAUTE, LocalDate.of(2026, 1, 8), T0));
        allergies().ajouter(allergie(CENTRE_B, PATIENT, CriticiteDAllergie.HAUTE, LocalDate.of(2026, 1, 9), T0));
        assertThat(allergies().critiquesDuPatient(CENTRE_A, PATIENT)).extracting(Allergie::id).containsExactly(refutee.id(), haute.id());
    }
}
