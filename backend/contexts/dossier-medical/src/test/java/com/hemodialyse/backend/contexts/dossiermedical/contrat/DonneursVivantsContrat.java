package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatDeCrossmatch;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanDuDonneur;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link DonneursVivants} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class DonneursVivantsContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7002"));
    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    protected abstract DonneursVivants donneurs();

    static DonneurVivant donneur(CentreId centre, PatientId patient, String nom, Instant creeLe) {
        return DonneurVivant.enregistrer(UUID.randomUUID(), patient, centre, nom, null, null, LienDeParente.PARENT, null, null, null, creeLe);
    }

    @Test
    void un_donneur_enregistre_est_relu_candidat_avec_tous_ses_champs() {
        var d = DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE_A, "Donneur", "Fictif", LocalDate.of(1970, 1, 2),
                LienDeParente.FRERE_SOEUR, "0000000001", "A+", "A1 B8", T0);
        donneurs().ajouter(d);
        var relu = donneurs().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        assertThat(relu.nom()).isEqualTo("Donneur");
        assertThat(relu.prenom()).isEqualTo("Fictif");
        assertThat(relu.dateNaissance()).isEqualTo(LocalDate.of(1970, 1, 2));
        assertThat(relu.lienDeParente()).isEqualTo(LienDeParente.FRERE_SOEUR);
        assertThat(relu.telephone()).isEqualTo("0000000001");
        assertThat(relu.groupeSanguin()).isEqualTo("A+");
        assertThat(relu.typageHla()).isEqualTo("A1 B8");
        assertThat(relu.statutBilan()).isEqualTo(StatutDuBilanDuDonneur.CANDIDAT);
        assertThat(relu.crossmatch()).isEqualTo(ResultatDeCrossmatch.NON_FAIT);
        assertThat(relu.creeLe()).isEqualTo(T0);
    }

    @Test
    void un_donneur_est_cherche_par_identifiant_patient_et_centre() {
        var d = donneur(CENTRE_A, PATIENT, "x", T0);
        donneurs().ajouter(d);
        assertThat(donneurs().trouverEnVerrouillant(CENTRE_B, PATIENT, d.id())).isEmpty();
        assertThat(donneurs().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, d.id())).isEmpty();
        assertThat(donneurs().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_mise_a_jour_remplace_tout_le_dossier_du_donneur() {
        var d = donneur(CENTRE_A, PATIENT, "Avant", T0);
        donneurs().ajouter(d);
        var charge = donneurs().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        charge.mettreAJour("Après", "Prénom", null, LienDeParente.ENFANT, null, "O+", null, StatutDuBilanDuDonneur.RETENU,
                ResultatDeCrossmatch.NEGATIF, LocalDate.of(2026, 11, 20), "bilan", "aucune", "retenu", LocalDate.of(2026, 11, 30),
                T0.plusSeconds(60));
        donneurs().enregistrer(charge);
        var relu = donneurs().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        assertThat(relu.nom()).isEqualTo("Après");
        assertThat(relu.lienDeParente()).isEqualTo(LienDeParente.ENFANT);
        assertThat(relu.statutBilan()).isEqualTo(StatutDuBilanDuDonneur.RETENU);
        assertThat(relu.crossmatch()).isEqualTo(ResultatDeCrossmatch.NEGATIF);
        assertThat(relu.dateCrossmatch()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(relu.bilanRealise()).isEqualTo("bilan");
        assertThat(relu.contreIndications()).isEqualTo("aucune");
        assertThat(relu.decisionFinale()).isEqualTo("retenu");
        assertThat(relu.dateDecision()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(relu.modifieLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(relu.creeLe()).isEqualTo(T0);
    }

    @Test
    void la_liste_va_du_plus_recemment_cree_au_plus_ancien_pour_le_patient_et_le_centre() {
        donneurs().ajouter(donneur(CENTRE_A, PATIENT, "ancien", T0));
        donneurs().ajouter(donneur(CENTRE_A, PATIENT, "recent", T0.plusSeconds(10)));
        donneurs().ajouter(donneur(CENTRE_A, AUTRE_PATIENT, "autre", T0));
        donneurs().ajouter(donneur(CENTRE_B, PATIENT, "ailleurs", T0));
        assertThat(donneurs().duPatient(CENTRE_A, PATIENT)).extracting(DonneurVivant::nom).containsExactly("recent", "ancien");
        assertThat(donneurs().duPatient(CENTRE_B, AUTRE_PATIENT)).isEmpty();
    }

    @Test
    void deux_donneurs_identiques_sont_deux_donneurs() {
        donneurs().ajouter(donneur(CENTRE_A, PATIENT, "x", T0));
        donneurs().ajouter(donneur(CENTRE_A, PATIENT, "x", T0));
        assertThat(donneurs().duPatient(CENTRE_A, PATIENT)).hasSize(2);
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var d = donneur(CENTRE_A, PATIENT, "x", T0);
        var autre = donneur(CENTRE_A, PATIENT, "y", T0);
        donneurs().ajouter(d);
        donneurs().ajouter(autre);
        assertThat(donneurs().supprimer(CENTRE_B, PATIENT, d.id())).isFalse();
        assertThat(donneurs().supprimer(CENTRE_A, AUTRE_PATIENT, d.id())).isFalse();
        assertThat(donneurs().supprimer(CENTRE_A, PATIENT, d.id())).isTrue();
        assertThat(donneurs().supprimer(CENTRE_A, PATIENT, d.id())).isFalse();
        assertThat(donneurs().duPatient(CENTRE_A, PATIENT)).extracting(DonneurVivant::id).containsExactly(autre.id());
    }
}
