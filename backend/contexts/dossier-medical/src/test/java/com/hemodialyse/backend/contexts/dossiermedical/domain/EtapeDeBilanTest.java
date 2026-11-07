package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants d'une étape du bilan pré-greffe. */
class EtapeDeBilanTest {
    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());
    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());

    private static EtapeDeBilan etape() {
        return EtapeDeBilan.ajouter(UUID.randomUUID(), PATIENT, CENTRE, CategorieDEtape.ORL, "Consultation ORL", T0);
    }

    @Test
    void une_etape_ajoutee_est_a_faire_et_vierge() {
        var e = etape();
        assertThat(e.statut()).isEqualTo(StatutDEtape.A_FAIRE);
        assertThat(e.dateRealisation()).isNull();
        assertThat(e.resultat()).isNull();
    }

    @Test
    void la_categorie_et_le_libelle_non_blanc_sont_obligatoires() {
        for (String libelle : new String[]{null, " "}) {
            assertThatThrownBy(() -> EtapeDeBilan.ajouter(UUID.randomUUID(), PATIENT, CENTRE, CategorieDEtape.ORL, libelle, T0))
                    .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("ETAPE_GREFFE_CHAMPS_REQUIS"));
        }
        assertThatThrownBy(() -> EtapeDeBilan.ajouter(UUID.randomUUID(), PATIENT, CENTRE, null, "x", T0))
                .isInstanceOf(RegleDeGestionViolee.class);
    }

    @Test
    void le_libelle_est_borne_a_255_caracteres() {
        EtapeDeBilan.ajouter(UUID.randomUUID(), PATIENT, CENTRE, CategorieDEtape.ORL, "x".repeat(255), T0);
        assertThatThrownBy(() -> EtapeDeBilan.ajouter(UUID.randomUUID(), PATIENT, CENTRE, CategorieDEtape.ORL, "x".repeat(256), T0))
                .isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void une_etape_faite_exige_une_date_de_realisation() {
        var e = etape();
        assertThatThrownBy(() -> e.mettreAJour(StatutDEtape.FAIT, null, "ok", null, null, null, T0))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, x -> assertThat(x.code()).isEqualTo("ETAPE_GREFFE_DATE_REALISATION_REQUISE"));
        assertThat(e.statut()).isEqualTo(StatutDEtape.A_FAIRE);
    }

    @Test
    void la_mise_a_jour_remplace_tous_les_champs_et_accepte_une_date_pour_tout_statut() {
        var e = etape();
        UUID demande = UUID.randomUUID();
        e.mettreAJour(StatutDEtape.FAIT, LocalDate.of(2026, 11, 1), "Negatif", LocalDate.of(2027, 1, 1), demande, demande, T0);
        e.mettreAJour(StatutDEtape.NON_APPLICABLE, LocalDate.of(2026, 11, 2), null, null, null, null, T0.plusSeconds(1));
        assertThat(e.statut()).isEqualTo(StatutDEtape.NON_APPLICABLE);
        assertThat(e.dateRealisation()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(e.resultat()).isNull();
        assertThat(e.demandeExamenId()).isNull();
        assertThat(e.categorie()).isEqualTo(CategorieDEtape.ORL);
    }

    @Test
    void la_liste_standard_compte_vingt_trois_etapes_sans_doublon() {
        assertThat(ListeStandardDesEtapes.ETAPES).hasSize(23);
        assertThat(ListeStandardDesEtapes.ETAPES.stream().map(ListeStandardDesEtapes.EtapeStandard::cle).distinct().count()).isEqualTo(23);
    }
}
