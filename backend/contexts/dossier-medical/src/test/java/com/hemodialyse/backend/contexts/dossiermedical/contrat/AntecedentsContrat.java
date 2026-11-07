package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAntecedent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link Antecedents} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class AntecedentsContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d5001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d5002"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 15);

    protected abstract Antecedents antecedents();

    static Antecedent libre(CentreId centre, PatientId patient, LocalDate debut, Instant creee) {
        return Antecedent.enregistrer(UUID.randomUUID(), patient, centre, TypeDAntecedent.MEDICAL, null, "Libre", debut, null, null, null,
                AUJOURDHUI, creee);
    }

    static Antecedent code(CentreId centre, PatientId patient, SystemeDeCodage systeme, String code, LocalDate debut) {
        return Antecedent.enregistrer(UUID.randomUUID(), patient, centre, TypeDAntecedent.COMORBIDITE, CodeClinique.saisir(systeme, code, null),
                null, debut, null, null, null, AUJOURDHUI, T0);
    }

    @Test
    void un_antecedent_ajoute_est_relu_avec_tous_ses_champs() {
        var a = Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, TypeDAntecedent.CHIRURGICAL,
                CodeClinique.saisir(SystemeDeCodage.CIM10, "n18.5", "Maladie fictive"), "Précision", LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 2, 5), "MODEREE", "Note fictive", AUJOURDHUI, T0);
        antecedents().ajouter(a);
        var relu = antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relu.type()).isEqualTo(TypeDAntecedent.CHIRURGICAL);
        assertThat(relu.diagnostic().systeme()).isEqualTo(SystemeDeCodage.CIM10);
        assertThat(relu.diagnostic().code()).isEqualTo("N18.5");
        assertThat(relu.diagnostic().affichage()).isEqualTo("Maladie fictive");
        assertThat(relu.libelleLibre()).isEqualTo("Précision");
        assertThat(relu.debut()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(relu.fin()).isEqualTo(LocalDate.of(2026, 2, 5));
        assertThat(relu.statut()).isEqualTo(StatutClinique.ACTIF);
        assertThat(relu.severite()).isEqualTo("MODEREE");
        assertThat(relu.note()).isEqualTo("Note fictive");
        assertThat(relu.creeLe()).isEqualTo(T0);
        assertThat(relu.modifieLe()).isEqualTo(T0);
        assertThat(relu.centre()).isEqualTo(CENTRE_A);
        assertThat(relu.patient()).isEqualTo(PATIENT);
    }

    @Test
    void les_champs_facultatifs_absents_restent_absents() {
        var a = libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        antecedents().ajouter(a);
        var relu = antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relu.diagnostic()).isNull();
        assertThat(relu.fin()).isNull();
        assertThat(relu.severite()).isNull();
        assertThat(relu.note()).isNull();
    }

    @Test
    void un_antecedent_est_cherche_par_identifiant_patient_et_centre() {
        var a = libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        antecedents().ajouter(a);
        assertThat(antecedents().trouverEnVerrouillant(CENTRE_B, PATIENT, a.id())).isEmpty();
        assertThat(antecedents().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, a.id())).isEmpty();
        assertThat(antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_correction_et_la_resolution_sont_enregistrees_sans_toucher_au_type_ni_a_la_creation() {
        var a = Antecedent.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, TypeDAntecedent.FAMILIAL, null, "Avant",
                LocalDate.of(2026, 1, 5), null, "FORTE", "note", AUJOURDHUI, T0);
        antecedents().ajouter(a);
        var chargee = antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        chargee.corriger(CodeClinique.saisir(SystemeDeCodage.LOCAL, "x1", "Affichage"), "Après", LocalDate.of(2026, 1, 6), null, null, null,
                null, T0.plusSeconds(60));
        antecedents().enregistrerLaCorrection(chargee);
        var relu = antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(relu.type()).isEqualTo(TypeDAntecedent.FAMILIAL);
        assertThat(relu.diagnostic().code()).isEqualTo("X1");
        assertThat(relu.libelleLibre()).isEqualTo("Après");
        assertThat(relu.severite()).isNull();
        assertThat(relu.note()).isNull();
        assertThat(relu.modifieLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relu.creeLe()).isEqualTo(T0);
        relu.resoudre(LocalDate.of(2026, 3, 1), T0.plusSeconds(120));
        antecedents().enregistrerLaCorrection(relu);
        var resolu = antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, a.id()).orElseThrow();
        assertThat(resolu.statut()).isEqualTo(StatutClinique.RESOLU);
        assertThat(resolu.fin()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var a = libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        var autre = libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 6), T0);
        antecedents().ajouter(a);
        antecedents().ajouter(autre);
        assertThat(antecedents().supprimer(CENTRE_B, PATIENT, a.id())).isFalse();
        assertThat(antecedents().supprimer(CENTRE_A, AUTRE_PATIENT, a.id())).isFalse();
        assertThat(antecedents().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(antecedents().supprimer(CENTRE_A, PATIENT, a.id())).isTrue();
        assertThat(antecedents().supprimer(CENTRE_A, PATIENT, a.id())).isFalse();
        assertThat(antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, autre.id())).isPresent();
    }

    @Test
    void un_diagnostic_actif_existe_pour_le_meme_systeme_et_le_meme_code_chez_le_meme_patient_et_le_meme_centre() {
        var n18 = CodeClinique.saisir(SystemeDeCodage.CIM10, "N18", null);
        antecedents().ajouter(code(CENTRE_A, PATIENT, SystemeDeCodage.CIM10, "N18", LocalDate.of(2026, 1, 5)));
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, PATIENT, n18, null)).isTrue();
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, PATIENT, CodeClinique.saisir(SystemeDeCodage.LOCAL, "N18", null),
                null)).isFalse();
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, PATIENT, CodeClinique.saisir(SystemeDeCodage.CIM10, "N19", null),
                null)).isFalse();
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, AUTRE_PATIENT, n18, null)).isFalse();
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_B, PATIENT, n18, null)).isFalse();
    }

    @Test
    void un_antecedent_resolu_ou_inactif_n_est_pas_un_diagnostic_actif_et_l_antecedent_corrige_s_exclut() {
        var n18 = CodeClinique.saisir(SystemeDeCodage.CIM10, "N18", null);
        var a = code(CENTRE_A, PATIENT, SystemeDeCodage.CIM10, "N18", LocalDate.of(2026, 1, 5));
        antecedents().ajouter(a);
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, PATIENT, n18, a.id())).isFalse();
        a.resoudre(LocalDate.of(2026, 2, 1), T0);
        antecedents().enregistrerLaCorrection(a);
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, PATIENT, n18, null)).isFalse();
        a.corriger(n18, null, LocalDate.of(2026, 1, 5), null, StatutClinique.INACTIF, null, null, T0);
        antecedents().enregistrerLaCorrection(a);
        assertThat(antecedents().unDiagnosticActifExisteEnVerrouillant(CENTRE_A, PATIENT, n18, null)).isFalse();
    }

    @Test
    void la_liste_est_triee_du_debut_le_plus_recent_au_plus_ancien_et_paginee_par_patient_et_centre() {
        for (int jour = 1; jour <= 5; jour++) {
            antecedents().ajouter(libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, jour), T0));
        }
        antecedents().ajouter(libre(CENTRE_A, AUTRE_PATIENT, LocalDate.of(2026, 1, 9), T0));
        antecedents().ajouter(libre(CENTRE_B, PATIENT, LocalDate.of(2026, 1, 9), T0));
        var premiere = antecedents().duPatient(CENTRE_A, PATIENT, 0, 2);
        assertThat(premiere.total()).isEqualTo(5);
        assertThat(premiere.items()).extracting(Antecedent::debut).containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4));
        assertThat(antecedents().duPatient(CENTRE_A, PATIENT, 2, 2).items()).extracting(Antecedent::debut)
                .containsExactly(LocalDate.of(2026, 1, 1));
        assertThat(antecedents().duPatient(CENTRE_A, PATIENT, 9, 2).items()).isEmpty();
        assertThat(antecedents().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 2).total()).isZero();
    }

    @Test
    void a_debut_egal_le_dernier_cree_vient_en_premier() {
        var premier = libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0);
        var second = libre(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 5), T0.plusSeconds(5));
        antecedents().ajouter(premier);
        antecedents().ajouter(second);
        assertThat(antecedents().duPatient(CENTRE_A, PATIENT, 0, 10).items()).extracting(Antecedent::id).containsExactly(second.id(), premier.id());
    }
}
