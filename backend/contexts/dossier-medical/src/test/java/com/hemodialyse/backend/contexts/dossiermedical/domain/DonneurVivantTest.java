package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants du candidat donneur vivant. */
class DonneurVivantTest {
    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());

    private static DonneurVivant donneur() {
        return DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "Donneur", null, null, LienDeParente.PARENT, null, null, null, T0);
    }

    @Test
    void un_donneur_enregistre_est_candidat_avec_un_crossmatch_non_fait() {
        var d = donneur();
        assertThat(d.statutBilan()).isEqualTo(StatutDuBilanDuDonneur.CANDIDAT);
        assertThat(d.crossmatch()).isEqualTo(ResultatDeCrossmatch.NON_FAIT);
    }

    @Test
    void le_nom_non_blanc_et_le_lien_sont_obligatoires() {
        for (String nom : new String[]{null, " "}) {
            assertThatThrownBy(() -> DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, nom, null, null, LienDeParente.PARENT, null,
                    null, null, T0)).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                    e -> assertThat(e.code()).isEqualTo("DONNEUR_VIVANT_CHAMPS_REQUIS"));
        }
        assertThatThrownBy(() -> DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "x", null, null, null, null, null, null, T0))
                .isInstanceOf(RegleDeGestionViolee.class);
    }

    @Test
    void les_bornes_de_colonne_sont_refusees_en_saisie_invalide() {
        assertThatThrownBy(() -> DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "x".repeat(101), null, null,
                LienDeParente.PARENT, null, null, null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "x", null, null, LienDeParente.PARENT,
                "1".repeat(31), null, null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "x", null, null, LienDeParente.PARENT, null,
                "ABCDEF", null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "x", null, null, LienDeParente.PARENT, null,
                null, "x".repeat(501), T0)).isInstanceOf(SaisieInvalide.class);
        DonneurVivant.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, "x".repeat(100), "p".repeat(100), LocalDate.now().plusYears(1),
                LienDeParente.PARENT, "1".repeat(30), "ABCDE", "x".repeat(500), T0);
    }

    @Test
    void la_mise_a_jour_remplace_tout_et_n_applique_aucune_regle_croisee() {
        var d = donneur();
        d.mettreAJour("Nom", "Prenom", null, LienDeParente.ENFANT, "1", "O+", "A2", StatutDuBilanDuDonneur.RETENU, ResultatDeCrossmatch.POSITIF,
                null, "bilan", "ci", "décision", LocalDate.of(2026, 11, 1), T0.plusSeconds(1));
        assertThat(d.statutBilan()).isEqualTo(StatutDuBilanDuDonneur.RETENU);
        assertThat(d.crossmatch()).isEqualTo(ResultatDeCrossmatch.POSITIF);
        d.mettreAJour("Nom", null, null, LienDeParente.ENFANT, null, null, null, StatutDuBilanDuDonneur.EXCLU, null, null, null, null, null,
                null, T0.plusSeconds(2));
        assertThat(d.prenom()).isNull();
        assertThat(d.crossmatch()).isEqualTo(ResultatDeCrossmatch.NON_FAIT);
        assertThat(d.decisionFinale()).isNull();
    }

    @Test
    void la_mise_a_jour_sans_statut_de_bilan_est_refusee() {
        var d = donneur();
        assertThatThrownBy(() -> d.mettreAJour("Nom", null, null, LienDeParente.PARENT, null, null, null, null, null, null, null, null, null,
                null, T0)).isInstanceOf(RegleDeGestionViolee.class);
    }
}
