package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants de l'observation biologique (domaine pur, sans mock). */
class ObservationBiologiqueTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 10);
    private static final Instant T0 = Instant.parse("2026-01-10T10:00:00Z");
    private static final CodeClinique HEMOGLOBINE = CodeClinique.saisir(SystemeDeCodage.LOINC, " 718-7 ", " Hémoglobine ");

    private static ValeurMesuree g(String valeur) {
        return new ValeurMesuree(new BigDecimal(valeur), "g/dL");
    }

    private static ObservationBiologique enregistrer(CodeClinique analyte, ValeurMesuree valeur, String texte, LocalDate date,
                                                     StatutDObservation statut) {
        return ObservationBiologique.enregistrer(UUID.randomUUID(), PATIENT, CENTRE, null, analyte, valeur, texte, date, statut, JOUR, T0);
    }

    private static String codeDeLaRegle(Runnable action) {
        return ((RegleDeGestionViolee) catchThrowable(action)).code();
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            return t;
        }
        throw new AssertionError("Aucune exception");
    }

    @Test
    void une_observation_enregistree_est_finale_saisie_directement_et_son_analyte_est_normalise() {
        var o = enregistrer(HEMOGLOBINE, g("11.5"), null, JOUR, null);
        assertThat(o.statut()).isEqualTo(StatutDObservation.FINAL);
        assertThat(o.source()).isEqualTo(SourceDObservation.SAISIE_DIRECTE);
        assertThat(o.analyte().code()).isEqualTo("718-7");
        assertThat(o.analyte().affichage()).isEqualTo("Hémoglobine");
        assertThat(o.creeLe()).isEqualTo(o.modifieLe());
    }

    @Test
    void le_statut_demande_est_conserve() {
        assertThat(enregistrer(HEMOGLOBINE, null, "Trace", JOUR, StatutDObservation.PRELIMINAIRE).statut())
                .isEqualTo(StatutDObservation.PRELIMINAIRE);
    }

    @Test
    void la_valeur_est_soit_numerique_soit_textuelle_jamais_les_deux_ni_aucune() {
        assertThat(codeDeLaRegle(() -> enregistrer(HEMOGLOBINE, g("1"), "Trace", JOUR, null))).isEqualTo("OBSERVATION_VALEUR_INVALIDE");
        assertThat(codeDeLaRegle(() -> enregistrer(HEMOGLOBINE, null, null, JOUR, null))).isEqualTo("OBSERVATION_VALEUR_INVALIDE");
        assertThat(codeDeLaRegle(() -> enregistrer(HEMOGLOBINE, null, "   ", JOUR, null))).isEqualTo("OBSERVATION_VALEUR_INVALIDE");
    }

    @Test
    void un_texte_blanc_a_cote_d_une_valeur_numerique_est_conserve_tel_quel_comme_le_legacy() {
        var o = enregistrer(HEMOGLOBINE, g("1"), "   ", JOUR, null);
        assertThat(o.valeurTexte()).isEqualTo("   ");
        assertThat(o.valeurNumerique().valeur()).isEqualByComparingTo("1");
    }

    @Test
    void une_valeur_numerique_exige_une_unite_et_aucune_plausibilite_n_est_controlee() {
        assertThatThrownBy(() -> new ValeurMesuree(BigDecimal.ONE, "  ")).isInstanceOfSatisfying(RegleDeGestionViolee.class,
                e -> assertThat(e.code()).isEqualTo("VALEUR_MESUREE_UNITE_REQUISE"));
        assertThat(enregistrer(HEMOGLOBINE, g("-3"), null, JOUR, null).valeurNumerique().valeur()).isEqualByComparingTo("-3");
        assertThat(enregistrer(HEMOGLOBINE, g("0"), null, JOUR, null)).isNotNull();
    }

    @Test
    void l_analyte_doit_etre_loinc() {
        var local = CodeClinique.saisir(SystemeDeCodage.LOCAL, "hb_local", null);
        assertThat(codeDeLaRegle(() -> enregistrer(local, g("1"), null, JOUR, null))).isEqualTo("OBSERVATION_ANALYTE_NON_LOINC");
    }

    @Test
    void la_date_est_obligatoire_et_jamais_dans_le_futur_mais_une_date_ancienne_est_acceptee() {
        assertThat(codeDeLaRegle(() -> enregistrer(HEMOGLOBINE, g("1"), null, null, null))).isEqualTo("OBSERVATION_CHAMPS_REQUIS");
        assertThat(codeDeLaRegle(() -> enregistrer(HEMOGLOBINE, g("1"), null, JOUR.plusDays(1), null))).isEqualTo("OBSERVATION_DATE_FUTURE");
        assertThat(enregistrer(HEMOGLOBINE, g("1"), null, JOUR, null)).isNotNull();
        assertThat(enregistrer(HEMOGLOBINE, g("1"), null, LocalDate.of(1900, 1, 1), null)).isNotNull();
    }

    @Test
    void les_regles_precedent_les_bornes_de_colonne_et_les_bornes_donnent_une_saisie_invalide() {
        assertThat(codeDeLaRegle(() -> enregistrer(HEMOGLOBINE, null, "x".repeat(501), JOUR.plusDays(1), null)))
                .isEqualTo("OBSERVATION_DATE_FUTURE");
        assertThatThrownBy(() -> enregistrer(HEMOGLOBINE, null, "x".repeat(501), JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThat(enregistrer(HEMOGLOBINE, null, "x".repeat(500), JOUR, null)).isNotNull();
        assertThatThrownBy(() -> enregistrer(HEMOGLOBINE, new ValeurMesuree(BigDecimal.ONE, "u".repeat(21)), null, JOUR, null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> enregistrer(HEMOGLOBINE, g("99999999999.9999"), null, JOUR, null)).isInstanceOf(SaisieInvalide.class);
        assertThat(enregistrer(HEMOGLOBINE, g("9999999999.9999"), null, JOUR, null)).isNotNull();
        assertThatThrownBy(() -> enregistrer(HEMOGLOBINE, g("9999999999.99995"), null, JOUR, null)).isInstanceOf(SaisieInvalide.class);
        var affichageTropLong = CodeClinique.saisir(SystemeDeCodage.LOINC, "718-7", "a".repeat(256));
        assertThatThrownBy(() -> enregistrer(affichageTropLong, g("1"), null, JOUR, null)).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void une_correction_remplace_la_valeur_et_passe_toujours_le_statut_a_corrige() {
        var o = enregistrer(HEMOGLOBINE, g("10"), null, JOUR, null);
        o.corriger(g("10"), null, T0.plusSeconds(60));
        assertThat(o.statut()).isEqualTo(StatutDObservation.CORRIGE);
        assertThat(o.modifieLe()).isEqualTo(T0.plusSeconds(60));
        o.corriger(null, "Indosable", T0.plusSeconds(120));
        assertThat(o.valeurNumerique()).isNull();
        assertThat(o.valeurTexte()).isEqualTo("Indosable");
        assertThat(o.datePrelevement()).isEqualTo(JOUR);
        assertThat(o.analyte().code()).isEqualTo("718-7");
    }

    @Test
    void une_correction_respecte_l_exclusivite_de_la_valeur_et_les_bornes() {
        var o = enregistrer(HEMOGLOBINE, g("10"), null, JOUR, null);
        assertThat(codeDeLaRegle(() -> o.corriger(g("1"), "x", T0))).isEqualTo("OBSERVATION_VALEUR_INVALIDE");
        assertThat(codeDeLaRegle(() -> o.corriger(null, null, T0))).isEqualTo("OBSERVATION_VALEUR_INVALIDE");
        assertThatThrownBy(() -> o.corriger(null, "t".repeat(501), T0)).isInstanceOf(SaisieInvalide.class);
        assertThat(o.statut()).isEqualTo(StatutDObservation.FINAL);
    }

    @Test
    void une_observation_derivee_d_un_bilan_est_immuable_mais_une_importee_se_corrige() {
        var derivee = ObservationBiologique.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, null, HEMOGLOBINE, g("9"), null, JOUR,
                StatutDObservation.FINAL, SourceDObservation.DERIVEE_BILAN, T0, T0);
        assertThat(codeDeLaRegle(() -> derivee.corriger(g("1"), null, T0))).isEqualTo("OBSERVATION_DERIVEE_IMMUABLE");
        var importee = ObservationBiologique.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, null, HEMOGLOBINE, g("9"), null, JOUR,
                StatutDObservation.FINAL, SourceDObservation.IMPORT, T0, T0);
        importee.corriger(g("1"), null, T0);
        assertThat(importee.statut()).isEqualTo(StatutDObservation.CORRIGE);
    }

    @Test
    void un_statut_inconnu_vide_ou_en_minuscules_est_une_saisie_invalide() {
        assertThat(StatutDObservation.lireFacultatif(null)).isNull();
        assertThat(StatutDObservation.lireFacultatif("CORRIGE")).isEqualTo(StatutDObservation.CORRIGE);
        for (String saisie : new String[]{"", "final", "ANNULE"}) {
            assertThatThrownBy(() -> StatutDObservation.lireFacultatif(saisie)).isInstanceOf(SaisieInvalide.class)
                    .hasMessageNotContaining("com.hemodialyse");
        }
    }
}
